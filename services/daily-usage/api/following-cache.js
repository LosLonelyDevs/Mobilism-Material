import { query } from '../lib/database.js';
import { authorize, sessionUser } from '../lib/access.js';
import { sameOrigin } from '../lib/admin-auth.js';
import { readConfig, saveConfig, publicConfig } from '../lib/app-config.js';
import { DAY, respond, utcDay, validDay } from '../lib/protocol.js';
import { releaseCatalog } from '../lib/releases.js';
import { requestBody } from '../lib/request-body.js';
import { visibleSnapshot } from '../lib/follow-lookup.js';
import { followingControlsAvailable } from '../public/limit-availability.js';

const ranks = `WITH totals AS (
  SELECT app_key, min(name) AS name, sum(followers) AS followers, sum(misses) AS misses,
    sum(hits) AS hits, sum(measured_misses) AS measured_misses, sum(other_lookups) AS other_lookups
  FROM daily_followed_apps WHERE day BETWEEN $1::date AND $2::date GROUP BY app_key
), ranked AS (
  SELECT *, row_number() OVER (ORDER BY followers DESC, app_key) AS follow_rank,
    row_number() OVER (ORDER BY misses DESC, app_key) AS miss_rank FROM totals
) SELECT coalesce(sum(hits),0)::text AS hits, coalesce(sum(misses),0)::text AS misses,
  coalesce(sum(measured_misses),0)::text AS "measuredMisses",
  coalesce(jsonb_agg(jsonb_build_object('key', app_key, 'name', name, 'followers', followers::text, 'misses', misses::text,
    'totalLookups', (hits + misses + other_lookups)::text, 'followRank', follow_rank, 'missRank', miss_rank))
    FILTER (WHERE (follow_rank <= 10 AND followers > 0)
    OR (miss_rank <= 10 AND misses > 0)), '[]') AS rankings FROM ranked`;

const rates = `SELECT cache, sum(hits)::text AS hits, sum(misses)::text AS misses FROM daily_cache_usage
  WHERE day BETWEEN $1::date AND $2::date AND cache IN ('FollowingBackground', 'FollowingManual') GROUP BY cache ORDER BY cache`;
const setting = config => ({ revision: config.revision, enabled: config.limits.followPullToRefreshEnabled === true });

export function createHandler(database = query, session = req => sessionUser(req, { database }), catalog = releaseCatalog, now = () => new Date()) {
  return async (req, res) => {
    if (!await authorize(req, res, 'following-cache', session)) return;
    if (!['GET', 'PUT'].includes(req.method)) {
      res.setHeader('Allow', 'GET, PUT'); return respond(res, 405, { error: 'Use GET or PUT' });
    }
    if (req.method === 'PUT' && !sameOrigin(req)) return respond(res, 403, { error: 'Request denied' });
    try {
      const available = followingControlsAvailable(await catalog());
      if (req.method === 'GET') {
        const params = new URL(req.url, 'https://usage.invalid').searchParams;
        const asOf = now(), today = utcDay(asOf), from = params.get('from') ?? today, to = params.get('to') ?? today;
        if (!validDay(from) || !validDay(to) || from > to || to > today || new Date(to) - new Date(from) > 364 * DAY)
          return respond(res, 400, { error: 'Invalid date range' });
        if (!available) return respond(res, 200, { available: false });
        const [cacheUsage, [totals], snapshot, config] = await Promise.all([database(rates, [from, to]), database(ranks, [from, to]),
          visibleSnapshot(database, Math.floor(asOf.getTime() / 1000)), readConfig(database)]);
        const versions = new Map(snapshot.items.map(item => [item.key, item.version]));
        const { rankings, ...lookups } = totals;
        const followRankings = rankings.map(({ key, ...row }) => ({ ...row, cachedVersion: versions.get(key) ?? null }));
        return respond(res, 200, { available, from, to, asOf: asOf.toISOString(), cachedApps: versions.size,
          cacheUsage, lookups, followRankings, pullToRefresh: setting(config) });
      }
      if (!available) return respond(res, 409, { error: 'Requires stable v0.1.24 or later.' });
      const body = requestBody(req, res, respond);
      if (!body) return;
      if (typeof body.enabled !== 'boolean' || !Number.isSafeInteger(body.revision) || body.revision < 0 ||
          Object.keys(body).some(key => !['revision', 'enabled'].includes(key)))
        return respond(res, 400, { error: 'Choose a valid pull-to-refresh setting.' });
      const config = await readConfig(database);
      if (config.revision === body.revision) {
        config.limits.followPullToRefreshEnabled = body.enabled;
        if (Buffer.byteLength(JSON.stringify(publicConfig(config))) > 65536) return respond(res, 413, { error: 'Configuration too large' });
        const saved = await saveConfig(database, config);
        if (saved) return respond(res, 200, setting(saved));
      }
      return respond(res, 409, { error: 'Configuration changed. Refresh before saving.' });
    } catch { return respond(res, 503, { error: 'Following cache is unavailable. Try again.' }); }
  };
}

export default createHandler();
