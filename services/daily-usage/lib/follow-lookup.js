import { defaultStorage } from '../public/config-schema.js';

const maxNames = 512;
const receiptFormat = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
export const appKey = name => name.normalize('NFKC').trim().toLowerCase().replace(/\s+/gu, ' ');
export const cacheLifetime = 86400;
// Scalar subqueries read the policy in the same statement as the snapshot.
export const retentionSecondsSql = `(3600 * coalesce((SELECT (config->'followingStorage'->>'retentionHours')::int
  FROM app_control WHERE id = 1), ${defaultStorage.retentionHours}))`;
export const capacitySql = `coalesce((SELECT (config->'followingStorage'->>'capacity')::int
  FROM app_control WHERE id = 1), ${defaultStorage.capacity})`;
export const latestFeedSize = 500;
const verificationLifetime = 21600;

export async function visibleSnapshot(database, now, latestOnly = false) {
  const itemsColumn = latestOnly ? `jsonb_path_query_array(items, '$[0 to ${latestFeedSize - 1}]') AS items` : 'items';
  const [row] = await database(`SELECT ${itemsColumn}, observed_at, ${retentionSecondsSql} AS lifetime FROM latest_apps_cache WHERE singleton`);
  const lifetime = Math.min(row?.lifetime ?? cacheLifetime, latestOnly ? cacheLifetime : Infinity);
  const items = (row?.items ?? []).filter(item => !item.hiddenAt && (item.lastSeen ?? item.postedAt) > now - lifetime);
  return { observedAt: Number(row?.observed_at ?? 0), items, lifetime };
}

export function parseLookup(body) {
  if (Object.keys(body).some(k => !['apps', 'adultFiltered', 'day'].includes(k)) ||
      typeof body.adultFiltered !== 'boolean' || typeof body.day !== 'string' || !/^\d{4}-\d{2}-\d{2}$/u.test(body.day) ||
      !Array.isArray(body.apps) || !body.apps.length || body.apps.length > maxNames) throw new Error();
  const names = new Set(), tokens = new Set();
  return body.apps.map(app => {
    if (!app || Object.keys(app).length !== 2 || typeof app.name !== 'string' || !app.name.length ||
        app.name.length > 200 || /[\p{Cc}\p{Cs}]/u.test(app.name) || typeof app.receipt !== 'string' ||
        !receiptFormat.test(app.receipt) || tokens.has(app.receipt)) throw new Error();
    const key = appKey(app.name);
    if (!key || key.length > 200 || names.has(key)) throw new Error();
    names.add(key);
    tokens.add(app.receipt);
    return { key, name: app.name, receipt: app.receipt };
  });
}

export async function lookup(database, apps, adultFiltered, now, clientDay, mode, clientVersion, record = true) {
  const keys = apps.map(app => app.key);
  const [row] = await database(`SELECT observed_at, available.* FROM latest_apps_cache
    CROSS JOIN LATERAL (
      SELECT coalesce(jsonb_agg(item ORDER BY position) FILTER (WHERE (NOT $2::boolean OR (item->>'adultFiltered')::boolean)
        AND ((position <= ${latestFeedSize} AND coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $3::bigint - ${cacheLifetime})
          OR (item->>'lastSeen')::bigint >= $4)), '[]') AS matches,
        coalesce(jsonb_agg(item->>'key'), '[]') AS keys
      FROM jsonb_array_elements(items) WITH ORDINALITY AS entry(item, position) WHERE item->>'key' = ANY ($1::text[])
        AND NOT (item ? 'hiddenAt') AND coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $3::bigint - ${retentionSecondsSql}
    ) available WHERE singleton`, [keys, adultFiltered, now, now - verificationLifetime]);
  if (!row) throw new Error();
  const feedObservedAt = Number(row.observed_at);
  const matches = new Set(row.keys);
  const isFresh = at => at > 0 && now - at >= -300 && now - at <= verificationLifetime;
  const fresh = isFresh(feedObservedAt);
  // A recent sighting confirms that specific app even when nobody has opened Latest recently.
  // Released clients accept at most 500 results even when they ask for 512 names.
  const items = (fresh ? row.matches : row.matches.filter(item => isFresh(item.lastSeen))).slice(0, latestFeedSize);
  const observedAt = fresh || !items.length ? feedObservedAt : Math.min(...items.map(item => item.lastSeen));
  const day = new Date(now * 1000).toISOString().slice(0, 10);
  const returned = new Set(items.map(item => item.key));
  const counts = apps.map(app => ({ ...app, hit: returned.has(app.key) ? 1 : 0,
    miss: fresh && !matches.has(app.key) ? 1 : 0 }));
  if (record) try {
    await database(`WITH input AS (
        SELECT * FROM jsonb_to_recordset($1::jsonb) AS a(key text, name text, receipt uuid, hit int, miss int)
      ), first_today AS (
        INSERT INTO follow_lookup_receipts(day, receipt) SELECT $2::date, receipt FROM input WHERE $3::boolean
        ORDER BY receipt ON CONFLICT DO NOTHING RETURNING receipt
      ) INSERT INTO daily_followed_apps(day, app_key, name, followers, misses, hits, measured_misses, other_lookups,
          background_hits, background_misses, manual_hits, manual_misses, last_requested_at, last_client_version)
        SELECT $2::date, key, name, (f.receipt IS NOT NULL)::int, miss, hit, miss, 1 - hit - miss,
          CASE WHEN $4::text = 'background' THEN hit ELSE 0 END, CASE WHEN $4 = 'background' THEN miss ELSE 0 END,
          CASE WHEN $4 = 'manual' THEN hit ELSE 0 END, CASE WHEN $4 = 'manual' THEN miss ELSE 0 END, $5::bigint, $6
        FROM input LEFT JOIN first_today f USING(receipt) ORDER BY key
        ON CONFLICT (day, app_key) DO UPDATE SET followers = daily_followed_apps.followers + excluded.followers,
          misses = daily_followed_apps.misses + excluded.misses, hits = daily_followed_apps.hits + excluded.hits,
          measured_misses = daily_followed_apps.measured_misses + excluded.measured_misses,
          other_lookups = daily_followed_apps.other_lookups + excluded.other_lookups,
          background_hits = daily_followed_apps.background_hits + excluded.background_hits,
          background_misses = daily_followed_apps.background_misses + excluded.background_misses,
          manual_hits = daily_followed_apps.manual_hits + excluded.manual_hits,
          manual_misses = daily_followed_apps.manual_misses + excluded.manual_misses,
          last_client_version = CASE WHEN excluded.last_requested_at >= coalesce(daily_followed_apps.last_requested_at, 0)
            THEN excluded.last_client_version ELSE daily_followed_apps.last_client_version END,
          last_requested_at = greatest(daily_followed_apps.last_requested_at, excluded.last_requested_at)`,
      [JSON.stringify(counts), day, day === clientDay, mode, now, clientVersion]);
  } catch { /* A statistics write must never turn a cache hit into a Mobilism search. */ }
  return { observedAt, misses: counts.filter(app => app.miss).length,
    items: items.map(({ id, title, name, version, postedAt, author, adultFiltered }) =>
    ({ id, title, name, version, postedAt, author, adultFiltered })) };
}
