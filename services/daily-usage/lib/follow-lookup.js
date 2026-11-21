const maxNames = 512;
const receiptFormat = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/u;
export const appKey = name => name.normalize('NFKC').trim().toLowerCase().replace(/\s+/gu, ' ');
export const cacheLifetime = 86400;

export async function visibleSnapshot(database, now) {
  const [row] = await database('SELECT items, observed_at FROM latest_apps_cache WHERE singleton');
  const items = (row?.items ?? []).filter(item => !item.hiddenAt && (item.lastSeen ?? item.postedAt) > now - cacheLifetime);
  return { observedAt: Number(row?.observed_at ?? 0), items };
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

export async function lookup(database, apps, adultFiltered, now, clientDay) {
  const keys = apps.map(app => app.key);
  const [row] = await database(`SELECT observed_at, available.* FROM latest_apps_cache
    CROSS JOIN LATERAL (
      SELECT coalesce(jsonb_agg(item) FILTER (WHERE NOT $2::boolean OR (item->>'adultFiltered')::boolean), '[]') AS matches,
        coalesce(jsonb_agg(item->>'key'), '[]') AS keys
      FROM jsonb_array_elements(items) item WHERE item->>'key' = ANY ($1::text[])
        AND NOT (item ? 'hiddenAt') AND coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $3
    ) available WHERE singleton`, [keys, adultFiltered, now - cacheLifetime]);
  if (!row) throw new Error();
  const feedObservedAt = Number(row.observed_at);
  const matches = new Set(row.keys);
  const isFresh = at => at > 0 && now - at >= -300 && now - at <= 21600;
  const fresh = isFresh(feedObservedAt);
  // A recent sighting confirms that specific app even when nobody has opened Latest recently.
  const items = fresh ? row.matches : row.matches.filter(item => isFresh(item.lastSeen));
  const observedAt = fresh || !items.length ? feedObservedAt : Math.min(...items.map(item => item.lastSeen));
  const day = new Date(now * 1000).toISOString().slice(0, 10);
  const returned = new Set(items.map(item => item.key));
  const counts = apps.map(app => ({ ...app, hit: returned.has(app.key) ? 1 : 0,
    miss: fresh && !matches.has(app.key) ? 1 : 0 }));
  try {
    await database(`WITH input AS (
        SELECT * FROM jsonb_to_recordset($1::jsonb) AS a(key text, name text, receipt uuid, hit int, miss int)
      ), first_today AS (
        INSERT INTO follow_lookup_receipts(day, receipt) SELECT $2::date, receipt FROM input WHERE $3::boolean
        ORDER BY receipt ON CONFLICT DO NOTHING RETURNING receipt
      ) INSERT INTO daily_followed_apps(day, app_key, name, followers, misses, hits, measured_misses, other_lookups)
        SELECT $2::date, key, name, (f.receipt IS NOT NULL)::int, miss, hit, miss, 1 - hit - miss
        FROM input LEFT JOIN first_today f USING(receipt) ORDER BY key
        ON CONFLICT (day, app_key) DO UPDATE SET followers = daily_followed_apps.followers + excluded.followers,
          misses = daily_followed_apps.misses + excluded.misses, hits = daily_followed_apps.hits + excluded.hits,
          measured_misses = daily_followed_apps.measured_misses + excluded.measured_misses,
          other_lookups = daily_followed_apps.other_lookups + excluded.other_lookups`, [JSON.stringify(counts), day, day === clientDay]);
  } catch { /* A statistics write must never turn a cache hit into a Mobilism search. */ }
  return { observedAt, misses: counts.filter(app => app.miss).length,
    items: items.map(({ id, title, name, version, postedAt, author, adultFiltered }) =>
    ({ id, title, name, version, postedAt, author, adultFiltered })) };
}
