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
export const catalogByteLimit = 1024 * 1024;
export const categoryTopByteLimit = 256 * 1024;

// PostgreSQL renders a JSONB array with two separator bytes per entry, including the brackets.
export function retainedCatalogSql(now, day, yesterdayWeight) {
  return `, ranked AS (
    SELECT *, row_number() OVER (ORDER BY posted DESC, id DESC, app_key) AS position FROM unique_apps
  ), demand AS (
    SELECT app_key, sum((hits + misses + other_lookups) *
      CASE WHEN day = ${day} THEN 1 ELSE ${yesterdayWeight} END) AS requests,
      max(last_requested_at) AS last_request
    FROM daily_followed_apps
    WHERE day BETWEEN ${day} - 1 AND ${day} AND last_requested_at > ${now} - ${cacheLifetime}
      AND app_key IN (SELECT app_key FROM ranked WHERE position > ${latestFeedSize})
    GROUP BY app_key
  ), selected AS (
    SELECT item, position, row_number() OVER priority AS slot,
      sum(octet_length(item::text) + 2) OVER priority AS bytes
    FROM ranked LEFT JOIN demand USING (app_key)
    WINDOW priority AS (ORDER BY (position <= ${latestFeedSize}) DESC,
      requests DESC NULLS LAST, last_request DESC NULLS LAST, position)
  ) SELECT coalesce(jsonb_agg(item ORDER BY position), '[]'::jsonb) FROM selected
    WHERE slot <= ${capacitySql} AND bytes <= ${catalogByteLimit}`;
}

export async function visibleSnapshot(database, now, latestOnly = false, includeStorage = false) {
  const itemsColumn = latestOnly ? `jsonb_path_query_array(items, '$[0 to ${latestFeedSize - 1}]') AS items` : 'items';
  const sizes = includeStorage ? ', octet_length(items::text) AS catalog_bytes, octet_length(category_top::text) AS chart_bytes' : '';
  const [row] = await database(`SELECT ${itemsColumn}, observed_at, ${retentionSecondsSql} AS lifetime ${sizes} FROM latest_apps_cache WHERE singleton`);
  const lifetime = Math.min(row?.lifetime ?? cacheLifetime, latestOnly ? cacheLifetime : Infinity);
  const items = (row?.items ?? []).filter(item => !item.hiddenAt && (item.lastSeen ?? item.postedAt) > now - lifetime);
  return { observedAt: Number(row?.observed_at ?? 0), items, lifetime, ...(includeStorage && { storageBytes: {
    catalog: row?.catalog_bytes ?? 0, catalogLimit: catalogByteLimit,
    categoryTop: row?.chart_bytes ?? 0, categoryTopLimit: categoryTopByteLimit,
  } }) };
}

export function publicRelease({ id, title, name, version, postedAt, author, adultFiltered, category, packageName }) {
  return { id, title, name, version, postedAt, author, adultFiltered, ...(category && { category }), ...(packageName && { packageName }) };
}

// Project the phone's inventory in Postgres so titles, artwork and internal metadata stay off the wire.
export async function installedApps(database, now, safe) {
  const [row] = await database(`WITH snapshot AS (
      SELECT items, ${retentionSecondsSql} AS lifetime FROM latest_apps_cache WHERE singleton
    ), visible AS (
      SELECT item, position, coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) + lifetime AS expires
      FROM snapshot CROSS JOIN LATERAL jsonb_array_elements(items) WITH ORDINALITY AS entry(item, position)
      WHERE NOT item ? 'hiddenAt' AND (NOT $2::boolean OR (item->>'adultFiltered')::boolean)
    ) SELECT coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object('id', item->'id', 'name', item->'name',
      'version', item->'version', 'packageName', item->'packageName', 'adultFiltered', item->'adultFiltered'))
      ORDER BY position), '[]'::jsonb) AS items, least($1::bigint + 300, min(expires)) AS expires
      FROM visible WHERE expires > $1`, [now, safe]);
  return { body: { observedAt: now, items: row.items, collection: 'installed' }, expiresAt: Number(row.expires) };
}

// One shared ranking for every phone. Each installation counts at most once per app per UTC day.
export async function discoverApps(database, now, safe) {
  const day = new Date(now * 1000).toISOString().slice(0, 10);
  const rows = await database(`WITH demand AS (
      SELECT app_key, sum(followers) AS popularity FROM daily_followed_apps
      WHERE day BETWEEN $1::date - 6 AND $1::date GROUP BY app_key HAVING sum(followers) > 0
    ), cache AS (
      SELECT items, category_top, ${retentionSecondsSql} AS lifetime FROM latest_apps_cache WHERE singleton
    ), live AS (
      SELECT item, lifetime FROM cache CROSS JOIN LATERAL jsonb_array_elements(items) item
      WHERE coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $2::bigint - lifetime
    ), placements AS (
      SELECT chart, lifetime FROM cache CROSS JOIN LATERAL jsonb_array_elements(category_top) chart
      WHERE (chart->>'lastSeen')::bigint > $2::bigint - lifetime AND (NOT $3::boolean OR (chart->>'adultFiltered')::boolean)
    ), sources AS (
      SELECT item, lifetime, coalesce((item->>'topicViews')::bigint, 0) AS views FROM live
      WHERE item ? 'category' AND NOT item ? 'hiddenAt' AND (NOT $3::boolean OR (item->>'adultFiltered')::boolean)
      UNION ALL
      SELECT CASE WHEN item IS NULL OR ((chart->>'postedAt')::bigint, (chart->>'id')::bigint) >
          ((item->>'postedAt')::bigint, (item->>'id')::bigint) THEN chart
        ELSE jsonb_strip_nulls(jsonb_build_object('category', chart->'category', 'artworkId', chart->'artworkId')) || item END,
        placements.lifetime, (chart->>'topicViews')::bigint
      FROM placements LEFT JOIN live ON item->>'key' = chart->>'key'
      WHERE item IS NULL OR (NOT item ? 'hiddenAt' AND (NOT $3::boolean OR (item->>'adultFiltered')::boolean)
        AND (NOT item ? 'category' OR item#>>'{category,id}' = chart#>>'{category,id}'))
    ), candidates AS (
      SELECT DISTINCT ON (item->>'key') item, lifetime, views, coalesce(popularity, 0) AS popularity
      FROM sources LEFT JOIN demand ON app_key = item->>'key' WHERE popularity > 0 OR views > 0
      ORDER BY item->>'key', (item->>'postedAt')::bigint DESC, (item->>'id')::bigint DESC, views DESC, (item ? 'artworkId') DESC
    ), ranked AS (
      SELECT *, row_number() OVER (PARTITION BY item#>>'{category,id}' ORDER BY popularity DESC, views DESC,
        (item->>'postedAt')::bigint DESC, (item->>'id')::bigint DESC) AS rank FROM candidates
    ) SELECT item, lifetime FROM ranked WHERE rank <= 20 ORDER BY rank, popularity DESC, views DESC, (item->>'id')::bigint DESC LIMIT 500`,
    [day, now, safe]);
  return { observedAt: now, items: rows.map(row => row.item), lifetime: Number(rows[0]?.lifetime ?? cacheLifetime) };
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
    items: items.map(publicRelease) };
}
