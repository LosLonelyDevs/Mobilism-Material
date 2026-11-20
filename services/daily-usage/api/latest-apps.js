import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';
import { appKey, lookup, parseLookup } from '../lib/follow-lookup.js';

const fields = ['id', 'title', 'name', 'version', 'postedAt', 'author'];
const lifetime = 24 * 60 * 60;
const confirmAfter = 10 * 60;
const maxUploadDelay = 2 * 60;
const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max &&
  value.trim() === value && !/[\u0000-\u001f\u007f]/u.test(value);

function validIds(ids) {
  return Array.isArray(ids) && ids.length <= 20 && ids.every(id => Number.isSafeInteger(id) && id > 0) &&
    new Set(ids).size === ids.length;
}

function parsePage(body, now) {
  if (!body || Object.keys(body).some(key => !['firstPage', 'adultFiltered', 'items', 'ids', 'readAt', 'baseline'].includes(key)) ||
      typeof body.firstPage !== 'boolean' || typeof body.adultFiltered !== 'boolean' || !Array.isArray(body.items) ||
      body.items.length > 20) throw new Error();
  const dated = Object.hasOwn(body, 'readAt');
  if (dated && (!Number.isSafeInteger(body.readAt) || Math.abs(now - body.readAt) > maxUploadDelay)) throw new Error();
  const at = dated ? Math.min(body.readAt, now) : now;
  const items = body.items.map(item => {
    if (!item || Object.keys(item).length !== fields.length || !fields.every(key => Object.hasOwn(item, key)) ||
        !Number.isSafeInteger(item.id) || item.id <= 0 || !Number.isSafeInteger(item.postedAt) ||
        item.postedAt < 1 || item.postedAt > now + 300 || !text(item.name, 200) ||
        !text(item.version, 80) || !text(item.title, 512) || !text(item.author, 100)) throw new Error();
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
        ELSE item END AS item
      FROM observations
    ), candidates AS (
      SELECT item, 0 AS priority FROM updated
      UNION ALL SELECT item, 1 FROM jsonb_array_elements($1::jsonb) AS item
    )
    SELECT coalesce(jsonb_agg(item ORDER BY posted DESC, id DESC), '[]'::jsonb) FROM (
      SELECT * FROM (
        SELECT DISTINCT ON (item->>'key') item, (item->>'postedAt')::bigint AS posted, (item->>'id')::bigint AS id
        FROM candidates
        WHERE coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $5::bigint
        ORDER BY item->>'key', posted DESC, id DESC, priority
      ) unique_apps ORDER BY posted DESC, id DESC LIMIT 500
    ) newest
  ), observed_at = greatest(observed_at, $2::bigint) WHERE singleton`;

export function createHandler(database = query, clock = () => Math.floor(Date.now() / 1000)) {
  return async (req, res) => {
    if (req.method === 'GET') {
      try {
        const [row] = await database('SELECT items, observed_at FROM latest_apps_cache WHERE singleton');
        const now = clock();
        const visible = (row?.items || []).filter(item => !item.hiddenAt && (item.lastSeen ?? item.postedAt) > now - lifetime);
        const items = visible.map(item => Object.fromEntries([...fields, 'adultFiltered'].map(key => [key, item[key]])));
        const ttl = Math.min(60, ...visible.map(item => (item.lastSeen ?? item.postedAt) + lifetime - now));
        res.setHeader('Vercel-CDN-Cache-Control', 'public, s-maxage=' + ttl);
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Cache-Control', 'public, max-age=' + ttl);
        res.statusCode = 200;
        return res.end(JSON.stringify({ observedAt: Number(row?.observed_at || 0), items }));
      } catch { return respond(res, 503, { error: 'Cache unavailable' }); }
    }
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'GET, POST');
      return respond(res, 405, { error: 'Use GET or POST' });
    }
    if (!/^application\/json(?:;|$)/iu.test(req.headers?.['content-type'] || '')) return respond(res, 415, { error: 'Use JSON' });
    const now = clock();
    let page, firstPage, requested, body;
    try {
      body = req.body;
      if (Buffer.isBuffer(body) || typeof body === 'string') {
        if (Buffer.byteLength(body) > 512 * 1024) throw new Error();
        body = JSON.parse(body.toString());
      }
      if (body && Object.hasOwn(body, 'apps')) requested = parseLookup(body);
      else {
        page = parsePage(body, now);
        firstPage = body.firstPage;
      }
    } catch { return respond(res, 400, { error: 'Invalid Latest page' }); }
    try {
      if (requested) return respond(res, 200, await lookup(database, requested, body.adultFiltered, now, body.day));
      const { items, ids, at, dated, anchors, missing, adultFiltered } = page;
      for (const item of items) if (anchors[item.id]) item.anchors = anchors[item.id];
      const seen = Object.fromEntries((dated ? ids : []).map(id => [id, true]));
      const safe = Object.fromEntries((adultFiltered ? ids : []).map(id => [id, true]));
      await database(updatePage, [JSON.stringify(items), firstPage ? at : 0, JSON.stringify(seen), at,
        now - lifetime, JSON.stringify(anchors), JSON.stringify(missing), confirmAfter, JSON.stringify(safe)]);
      return respond(res, 200, { accepted: true });
    } catch { return respond(res, 503, { error: 'Cache unavailable' }); }
  };
}

export default createHandler();
