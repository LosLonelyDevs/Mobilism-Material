import { query } from '../lib/database.js';
import { authorized, DAY, respond, utcDay, validDay } from '../lib/protocol.js';
import { authorize, sessionUser } from '../lib/access.js';
import { latestStableVersion, releaseCatalog } from '../lib/releases.js';
import { supportsMinimumVersion } from '../public/limit-availability.js';
import { requestTrafficMinimumVersion } from '../public/request-traffic.js';

const dimensions = 'app_version AS "appVersion", version_code AS "versionCode", android_api AS "androidApi", manufacturer, model';
const usageByDay = `SELECT day::text, ${dimensions}, devices::text FROM daily_usage
  WHERE day BETWEEN $1::date AND $2::date ORDER BY day, app_version, version_code, android_api, manufacturer, model`;
// Both groupings see the same reports even if a new ping arrives during the query.
const usageByPeriod = `SELECT day::text, ${dimensions}, sum(devices)::text AS devices FROM daily_usage
  WHERE day BETWEEN $1::date AND $2::date
  GROUP BY GROUPING SETS ((day), (app_version, version_code, android_api, manufacturer, model))
  ORDER BY day, app_version, version_code, android_api, manufacturer, model`;
const requestsByPeriod = `SELECT endpoint, method, json_build_array(sum(successes)::text, sum(redirects)::text,
  sum(client_errors)::text, sum(rate_limited)::text, sum(server_errors)::text, sum(network_errors)::text) AS counts,
  sum(duration_ms)::text AS "durationMs" FROM daily_requests WHERE day BETWEEN $1::date AND $2::date
  GROUP BY endpoint, method ORDER BY endpoint, method`;
const requestsByDay = `SELECT day::text, app_version AS "appVersion", version_code AS "versionCode",
  endpoint, method, json_build_array(successes::text, redirects::text, client_errors::text,
  rate_limited::text, server_errors::text, network_errors::text) AS counts, duration_ms::text AS "durationMs"
  FROM daily_requests WHERE day BETWEEN $1::date AND $2::date ORDER BY day, endpoint, method, version_code`;

export function createHandler(database = query, token = () => process.env.STATS_TOKEN, now = () => new Date(), session = req => sessionUser(req, { database }),
  catalog = releaseCatalog) {
  return async (req, res) => {
    if (!authorized(req, token()) && !await authorize(req, res, 'app-usage', session)) return;
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return respond(res, 405, { error: 'Use GET' });
    }
    const params = new URL(req.url, 'https://usage.invalid').searchParams;
    const period = params.get('group') === 'period';
    const to = params.get('to') || utcDay(now());
    const from = params.get('from') || utcDay(new Date(now().getTime() - 29 * DAY));
    if (!validDay(from) || !validDay(to) || from > to || to > utcDay(now()) ||
        new Date(to) - new Date(from) > 364 * DAY) return respond(res, 400, { error: 'Invalid date range' });
    try {
      const rows = await database(period ? usageByPeriod : usageByDay, [from, to]);
      const totals = new Map();
      const breakdown = [];
      for (const row of rows) {
        if (row.day) totals.set(row.day, (totals.get(row.day) || 0n) + BigInt(row.devices));
        if (!period || row.day === null) breakdown.push(row);
      }
      const daily = [];
      for (let date = new Date(from); date <= new Date(to); date = new Date(date.getTime() + DAY)) {
        const day = utcDay(date);
        daily.push({ day, devices: (totals.get(day) || 0n).toString() });
      }
      const releases = await catalog().catch(() => null);
      const trafficAvailable = supportsMinimumVersion(releases, requestTrafficMinimumVersion);
      const requestTraffic = trafficAvailable ? await database(period ? requestsByPeriod : requestsByDay, [from, to]) : [];
      const cacheUsage = trafficAvailable ? await database(`SELECT ${period ? '' : 'day::text,'} cache,
        sum(hits)::text AS hits, sum(misses)::text AS misses FROM daily_cache_usage WHERE day BETWEEN $1::date AND $2::date
        AND cache NOT IN ('FollowingBackground', 'FollowingManual')
        GROUP BY ${period ? 'cache' : 'day, cache'} ORDER BY cache`, [from, to]) : [];
      return respond(res, 200, { from, to, daily, breakdown, latestStable: latestStableVersion(releases), trafficAvailable, requestTraffic, cacheUsage });
    } catch { return respond(res, 503, { error: 'Statistics unavailable' }); }
  };
}

export default createHandler();
