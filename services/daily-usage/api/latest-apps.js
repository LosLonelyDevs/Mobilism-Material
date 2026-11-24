import { query } from '../lib/database.js';
import { authorizeAppWrite, enrollmentEnabled } from '../lib/app-writes.js';
import { respond } from '../lib/protocol.js';
import { appKey, cacheLifetime, capacitySql, discoverApps, latestFeedSize, lookup, parseLookup, publicRelease, retentionSecondsSql, visibleSnapshot } from '../lib/follow-lookup.js';
import { defaultStorage } from '../public/config-schema.js';

const fields = ['id', 'title', 'name', 'version', 'postedAt', 'author'];
const confirmAfter = 10 * 60;
const maxUploadDelay = 2 * 60;
export const catalogCapacity = defaultStorage.capacity;
const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max &&
  value.trim() === value && !/[\u0000-\u001f\u007f]/u.test(value);

function validIds(ids) {
  return Array.isArray(ids) && ids.length <= 20 && ids.every(id => Number.isSafeInteger(id) && id > 0) &&
    new Set(ids).size === ids.length;
}

function parsePage(body, now, maxAge = maxUploadDelay) {
  if (!body || Object.keys(body).some(key => !['firstPage', 'adultFiltered', 'items', 'ids', 'readAt', 'baseline'].includes(key)) ||
      typeof body.firstPage !== 'boolean' || typeof body.adultFiltered !== 'boolean' || !Array.isArray(body.items) ||
      body.items.length > 20) throw new Error();
  const dated = Object.hasOwn(body, 'readAt');
  if (dated && (!Number.isSafeInteger(body.readAt) || body.readAt > now + maxUploadDelay || now - body.readAt > maxAge)) throw new Error();
  const at = dated ? Math.min(body.readAt, now) : now;
  const items = body.items.map(item => {
    if (!item || Object.keys(item).some(key => !['category', 'packageName', 'artworkId', 'topicViews', ...fields].includes(key)) || !fields.every(key => Object.hasOwn(item, key)) ||
        (Object.hasOwn(item, 'artworkId') && !(text(item.artworkId, 64) && /^[A-Za-z0-9_-]+$/u.test(item.artworkId))) ||
        (Object.hasOwn(item, 'topicViews') && !(Number.isSafeInteger(item.topicViews) && item.topicViews > 0)) ||
        (Object.hasOwn(item, 'packageName') && !(text(item.packageName, 255) && /^[A-Za-z][\w]*(?:\.[A-Za-z\d_]+)+$/u.test(item.packageName))) ||
        !Number.isSafeInteger(item.id) || item.id <= 0 || !Number.isSafeInteger(item.postedAt) ||
        item.postedAt < 1 || item.postedAt > now + 300 || !text(item.name, 200) ||
        !text(item.version, 80) || !text(item.title, 512) || !text(item.author, 100)) throw new Error();
    if (Object.hasOwn(item, 'category')) {
      const c = item.category;
      if (!c || !Number.isSafeInteger(c.id) || c.id <= 0 || Object.keys(c).some(k => k !== 'id' && k !== 'name') ||
          (Object.hasOwn(c, 'name') && !text(c.name, 120))) throw new Error();
    }
    return { ...item, adultFiltered: body.adultFiltered, lastSeen: dated ? at : Math.min(item.postedAt, now),
      key: appKey(item.name) };
  });
  if (Object.hasOwn(body, 'ids') && (!dated || !validIds(body.ids) || items.some(item => !body.ids.includes(item.id)))) throw new Error();
  const ids = body.ids ?? items.map(item => item.id);
  if (!ids.length) throw new Error();
  const anchors = {}, missing = {};
  if (Object.hasOwn(body, 'baseline')) {
    if (!body.firstPage || !body.ids || ids.length < 2 || !validIds(body.baseline)) throw new Error();
    for (let i = 1; i < ids.length - 1; i++) anchors[ids[i]] = [ids[i - 1], ids[i + 1], body.adultFiltered];
    for (let i = 1; i < body.baseline.length - 1; i++) {
      const [newer, id, older] = body.baseline.slice(i - 1, i + 2);
      const first = ids.indexOf(newer);
      if (!ids.includes(id) && first >= 0 && ids.indexOf(older) > first) missing[id] = [newer, older, body.adultFiltered];
    }
  }
  return { items, ids, at, dated, anchors, missing, adultFiltered: body.adultFiltered };
}

// One row holds the bounded snapshot. Concurrent uploads merge against the last committed version.
const updatePage = `
  UPDATE latest_apps_cache SET items = (
    WITH prior AS (
      SELECT CASE WHEN $9::jsonb ? (item->>'id') THEN item || '{"adultFiltered":true}'::jsonb ELSE item END AS item,
             item->>'id' AS id, coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) AS seen,
             (item->>'firstMiss')::bigint AS missed
      FROM jsonb_array_elements(items) AS item
    ), observations AS (
      SELECT *, $3::jsonb ? id AND $4::bigint >= seen AS sighting,
             $6::jsonb -> id AS anchors, $7::jsonb -> id = item->'anchors' AS gap
      FROM prior
    ), updated AS (
      SELECT CASE
        WHEN sighting AND $4 >= coalesce((item->>'hiddenAt')::bigint, missed, 0) THEN
          (item - 'firstMiss' - 'hiddenAt') || jsonb_build_object('lastSeen', $4::bigint) ||
          CASE WHEN anchors IS NOT NULL THEN jsonb_build_object('anchors', anchors) ELSE '{}'::jsonb END
        WHEN gap AND $4 > seen AND missed IS NULL THEN item || jsonb_build_object('firstMiss', $4::bigint)
        WHEN gap AND $4 > seen AND $4 - missed >= $8::bigint THEN
          item || jsonb_build_object('hiddenAt', greatest((item->>'hiddenAt')::bigint, $4::bigint))
        ELSE item END AS item, seen, $12::jsonb -> id AS category
      FROM observations
    ), candidates AS (
      -- Merge metadata only for the same topic. An ID-only report can move a topic but cannot erase its known label.
      SELECT CASE WHEN category IS NOT NULL AND (
          NOT item ? 'category' OR
          (category->'id' = item#>'{category,id}' AND category ? 'name' AND NOT (item->'category' ? 'name')) OR
          ($4::bigint >= seen AND (category ? 'name' OR category->'id' <> item#>'{category,id}'))
        ) THEN jsonb_set(item, '{category}', category) ELSE item END ||
        CASE WHEN $13::jsonb ? (item->>'id') AND (NOT item ? 'packageName' OR $4::bigint >= seen)
          THEN jsonb_build_object('packageName', $13::jsonb -> (item->>'id')) ELSE '{}'::jsonb END ||
        CASE WHEN $14::jsonb ? (item->>'id') AND (NOT item ? 'artworkId' OR $4::bigint >= seen)
          THEN jsonb_build_object('artworkId', $14::jsonb -> (item->>'id')) ELSE '{}'::jsonb END ||
        CASE WHEN $15::jsonb ? (item->>'id') THEN jsonb_build_object('topicViews', greatest(
          (item->>'topicViews')::bigint, ($15::jsonb ->> (item->>'id'))::bigint)) ELSE '{}'::jsonb END AS item, 0 AS priority FROM updated
      UNION ALL SELECT item, 1 FROM jsonb_array_elements($1::jsonb) AS item
    ), unique_apps AS (
      SELECT DISTINCT ON (item->>'key') item, item->>'key' AS app_key,
        (item->>'postedAt')::bigint AS posted, (item->>'id')::bigint AS id
      FROM candidates
      WHERE coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $5::bigint - ${retentionSecondsSql}
      ORDER BY item->>'key', posted DESC, id DESC, priority
    ), ranked AS (
      SELECT *, row_number() OVER (ORDER BY posted DESC, id DESC, app_key) AS position FROM unique_apps
    ), demand AS (
      SELECT app_key, sum((hits + misses + other_lookups) *
        CASE WHEN day = $10::date THEN 1 ELSE $11::numeric END) AS requests,
        max(last_requested_at) AS last_request
      FROM daily_followed_apps
      WHERE day BETWEEN $10::date - 1 AND $10::date AND last_requested_at > $5::bigint - ${cacheLifetime}
        AND app_key IN (SELECT app_key FROM ranked WHERE position > ${latestFeedSize})
      GROUP BY app_key
    ), followed AS (
      SELECT position FROM ranked LEFT JOIN demand USING (app_key)
      WHERE position > ${latestFeedSize}
      ORDER BY requests DESC NULLS LAST, last_request DESC NULLS LAST, position
      LIMIT (${capacitySql} - ${latestFeedSize})
    )
    SELECT coalesce(jsonb_agg(item ORDER BY position), '[]'::jsonb) FROM ranked
    WHERE position <= ${latestFeedSize} OR position IN (SELECT position FROM followed)
  ), observed_at = greatest(observed_at, $2::bigint) WHERE singleton`;

async function savePage(database, page, firstPage, now) {
  const { items, ids, at, dated, anchors, missing, adultFiltered } = page;
  for (const item of items) if (anchors[item.id]) item.anchors = anchors[item.id];
  const seen = Object.fromEntries((dated ? ids : []).map(id => [id, true]));
  const safe = Object.fromEntries((adultFiltered ? ids : []).map(id => [id, true]));
  // Approximate a rolling day using existing daily counts. Midnight preserves yesterday's demand.
  const today = new Date(now * 1000).toISOString().slice(0, 10);
  const yesterdayWeight = 1 - (now % 86400) / 86400;
  await database(updatePage, [JSON.stringify(items), firstPage ? at : 0, JSON.stringify(seen), at,
    now, JSON.stringify(anchors), JSON.stringify(missing), confirmAfter, JSON.stringify(safe),
    today, yesterdayWeight, JSON.stringify(Object.fromEntries(items.filter(item => item.category).map(item => [item.id, item.category]))),
    JSON.stringify(Object.fromEntries(items.filter(item => item.packageName).map(item => [item.id, item.packageName]))),
    JSON.stringify(Object.fromEntries(items.filter(item => item.artworkId).map(item => [item.id, item.artworkId]))),
    JSON.stringify(Object.fromEntries(items.filter(item => item.topicViews).map(item => [item.id, item.topicViews])))]);
}

export function createHandler(database = query, clock = () => Math.floor(Date.now() / 1000), env = process.env) {
  return async (req, res) => {
    if (req.method === 'GET') {
      try {
        const now = clock();
        const params = new URL(req.url ?? '/', 'https://fixture.invalid').searchParams;
        const discover = params.get('view') === 'discover';
        const installed = params.get('view') === 'installed';
        const safe = params.get('safe') === '1';
        if ((discover || installed) && !['0', '1'].includes(params.get('safe'))) return respond(res, 400, { error: 'Invalid visibility' });
        const { observedAt, items: visible, lifetime } = discover
          ? await discoverApps(database, now, safe) : await visibleSnapshot(database, now, !installed);
        const items = installed ? visible.filter(item => !safe || item.adultFiltered).map(({ id, name, version, packageName, adultFiltered }) =>
          ({ id, name, version, ...(packageName && { packageName }), adultFiltered })) : visible.map(item =>
            discover && item.artworkId ? { ...publicRelease(item), artworkId: item.artworkId } : publicRelease(item));
        const ttl = Math.max(0, Math.min(discover || installed ? 300 : 60, ...visible.map(item => (item.lastSeen ?? item.postedAt) + lifetime - now)));
        res.setHeader('Vercel-CDN-Cache-Control', 'public, s-maxage=' + ttl);
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Cache-Control', 'public, max-age=' + ttl);
        res.statusCode = 200;
        return res.end(JSON.stringify({ observedAt: installed ? now : observedAt, items,
          ...(discover && { collection: 'discover' }), ...(installed && { collection: 'installed' }) }));
      } catch { return respond(res, 503, { error: 'Cache unavailable' }); }
    }
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'GET, POST');
      return respond(res, 405, { error: 'Use GET or POST' });
    }
    if (!/^application\/json(?:;|$)/iu.test(req.headers?.['content-type'] || '')) return respond(res, 415, { error: 'Use JSON' });
    const now = clock();
    if (enrollmentEnabled(env)) res.setHeader('X-App-Attestation', '1');
    let page, firstPage, requested, body, allowed, renew, unavailable, observations;
    try {
      body = req.body;
      const raw = Buffer.isBuffer(body) || typeof body === 'string' ? body : JSON.stringify(body);
      if (!raw || Buffer.byteLength(raw) > 1024 * 1024 + 4096) throw new Error();
      body = JSON.parse(raw.toString());
      if (Buffer.byteLength(typeof body?.payload === 'string' ? body.payload : raw) > 512 * 1024) throw new Error();
      ({ body, allowed, renew, unavailable } = await authorizeAppWrite(body, req, 'following', database, env));
      if (renew) res.setHeader('X-App-Attestation-Renew', '1');
      if (body && Object.hasOwn(body, 'observations')) {
        observations = body.observations;
        delete body.observations;
      }
      if (body && Object.hasOwn(body, 'apps')) requested = parseLookup(body);
      else {
        if (unavailable) return respond(res, 503, { error: 'Cache unavailable' });
        if (!allowed) return respond(res, 403, { error: 'Verified install required' });
        page = parsePage(body, now);
        firstPage = body.firstPage;
      }
    } catch { return respond(res, 400, { error: 'Invalid Latest page' }); }
    try {
      if (allowed && observations) {
        try {
          const { items, adultFiltered, readAt } = observations;
          if (!Array.isArray(items) || items.length < 1 || items.length > 10 || !Number.isSafeInteger(readAt)) throw new Error();
          // Only an actual Latest page can provide a baseline, missing IDs or a feed timestamp.
          const extra = parsePage({ items, adultFiltered, readAt, firstPage: false }, now, 6 * 3600);
          await savePage(database, extra, false, now);
          res.setHeader('X-App-Observations-Accepted', '1');
        } catch { /* Invalid or unavailable optional data must not fail the primary request. */ }
      }
      if (requested) {
        const mode = req.headers?.['x-following-check'];
        const check = mode === 'background' || mode === 'manual' ? mode : null;
        const agent = req.headers?.['user-agent'];
        const version = check && typeof agent === 'string' && agent.length <= 1024
          ? /(?:^| )MobilismMaterial\/(\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?) \(Android; build \d{1,19}\)$/.exec(agent)?.[1] : null;
        return respond(res, 200, await lookup(database, requested, body.adultFiltered, now, body.day, check,
          version && version.length <= 50 ? version : null, allowed));
      }
      await savePage(database, page, firstPage, now);
      return respond(res, 200, { accepted: true });
    } catch { return respond(res, 503, { error: 'Cache unavailable' }); }
  };
}

export default createHandler();
