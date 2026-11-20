import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';

const fields = ['id', 'title', 'name', 'version', 'postedAt', 'author'];
const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max &&
  value.trim() === value && !/[\u0000-\u001f\u007f]/u.test(value);

function parsePage(body, now) {
  if (!body || Object.keys(body).some(key => !['firstPage', 'adultFiltered', 'items'].includes(key)) ||
      typeof body.firstPage !== 'boolean' || typeof body.adultFiltered !== 'boolean' || !Array.isArray(body.items) ||
      body.items.length < 1 || body.items.length > 20) throw new Error();
  return body.items.map(item => {
    if (!item || Object.keys(item).length !== fields.length || !fields.every(key => Object.hasOwn(item, key)) ||
        !Number.isSafeInteger(item.id) || item.id <= 0 || !Number.isSafeInteger(item.postedAt) ||
        item.postedAt < 1 || item.postedAt > now + 300 || !text(item.name, 200) ||
        !text(item.version, 80) || !text(item.title, 512) || !text(item.author, 100)) throw new Error();
    return { ...item, adultFiltered: body.adultFiltered, key: item.name.normalize('NFKC').toLowerCase().replace(/\s+/gu, ' ') };
  });
}

// One row holds the bounded snapshot. Concurrent uploads merge against the last committed version.
const updatePage = `
  UPDATE latest_apps_cache SET items = (
    SELECT jsonb_agg(item ORDER BY posted DESC, id DESC) FROM (
      SELECT * FROM (
        SELECT DISTINCT ON (item->>'key') item, (item->>'postedAt')::bigint AS posted, (item->>'id')::bigint AS id
        FROM jsonb_array_elements(items || $1::jsonb) WITH ORDINALITY AS pages(item, position)
        ORDER BY item->>'key', posted DESC, id DESC, (item->>'adultFiltered')::boolean DESC, position
      ) unique_apps ORDER BY posted DESC, id DESC LIMIT 200
    ) newest
  ), observed_at = greatest(observed_at, $2::bigint) WHERE singleton`;

export function createHandler(database = query, clock = () => Math.floor(Date.now() / 1000)) {
  return async (req, res) => {
    if (req.method === 'GET') {
      try {
        const [row] = await database('SELECT items, observed_at FROM latest_apps_cache WHERE singleton');
        const items = (row?.items || []).map(({ key, ...item }) => item);
        res.setHeader('Vercel-CDN-Cache-Control', 'public, s-maxage=60');
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Cache-Control', 'public, max-age=60');
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
    let items, firstPage;
    try {
      let body = req.body;
      if (Buffer.isBuffer(body) || typeof body === 'string') {
        if (Buffer.byteLength(body) > 32000) throw new Error();
        body = JSON.parse(body.toString());
      }
      items = parsePage(body, now);
      firstPage = body.firstPage;
    } catch { return respond(res, 400, { error: 'Invalid Latest page' }); }
    try {
      await database(updatePage, [JSON.stringify(items), firstPage ? now : 0]);
      return respond(res, 200, { accepted: true });
    } catch { return respond(res, 503, { error: 'Cache unavailable' }); }
  };
}

export default createHandler();
