import { query } from '../lib/database.js';
import { authorized, DAY, respond, utcDay, validDay } from '../lib/protocol.js';
import { authorize, sessionUser } from '../lib/access.js';
import { latestStableVersion, releaseCatalog } from '../lib/releases.js';
import { supportsMinimumVersion } from '../public/limit-availability.js';
import { requestTrafficMinimumVersion } from '../public/request-traffic.js';

const dimensions = 'app_version AS "appVersion", version_code AS "versionCode", android_api AS "androidApi", manufacturer, model';
const usageRecord = `WITH totals AS (
  SELECT day, sum(devices) AS devices FROM daily_usage GROUP BY day
  UNION ALL SELECT day, devices FROM archived_usage_record
) SELECT day::text, devices::text FROM totals ORDER BY totals.devices DESC, day LIMIT 2`;
const usageByDay = `SELECT day::text, ${dimensions}, devices::text FROM daily_usage
  WHERE day BETWEEN $1::date AND $2::date ORDER BY day, app_version, version_code, android_api, manufacturer, model`;
// Both groupings see the same reports even if a new ping arrives during the query.
const usageByPeriod = `SELECT day::text, ${dimensions}, sum(devices)::text AS devices FROM daily_usage
  WHERE day BETWEEN $1::date AND $2::date
  GROUP BY GROUPING SETS ((day, app_version), (app_version, version_code, android_api, manufacturer, model))
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
      const versionsByDay = new Map();
      const breakdown = [];
      for (const row of rows) {
        if (row.day) {
          const versions = versionsByDay.get(row.day) ?? new Map();
          versions.set(row.appVersion, (versions.get(row.appVersion) ?? 0n) + BigInt(row.devices));
          versionsByDay.set(row.day, versions);
        }
        if (!period || row.day === null) breakdown.push(row);
      }
      const daily = [];
      for (let date = new Date(from); date <= new Date(to); date = new Date(date.getTime() + DAY)) {
        const day = utcDay(date);
        const versions = Array.from(versionsByDay.get(day) ?? [], ([appVersion, count]) => ({ appVersion, devices: count.toString() }));
        const devices = versions.reduce((sum, version) => sum + BigInt(version.devices), 0n);
        daily.push({ day, devices: devices.toString(), versions });
      }
      const releases = await catalog().catch(() => null);
      const trafficAvailable = supportsMinimumVersion(releases, requestTrafficMinimumVersion);
      const requestTraffic = trafficAvailable ? await database(period ? requestsByPeriod : requestsByDay, [from, to]) : [];
      const cacheUsage = trafficAvailable ? await database(`SELECT ${period ? '' : 'day::text,'} cache,
        sum(hits)::text AS hits, sum(misses)::text AS misses FROM daily_cache_usage WHERE day BETWEEN $1::date AND $2::date
        AND cache NOT IN ('FollowingBackground', 'FollowingManual')
        GROUP BY ${period ? 'cache' : 'day, cache'} ORDER BY cache`, [from, to]) : [];
      let record;
      if (params.get('record') === '1') {
        const [best, runnerUp] = await database(usageRecord);
        record = best ? { ...best, previous: runnerUp?.devices ?? null } : null;
      }
      return respond(res, 200, { from, to, daily, breakdown, record, latestStable: latestStableVersion(releases), trafficAvailable, requestTraffic, cacheUsage });
    } catch { return respond(res, 503, { error: 'Statistics unavailable' }); }
  };
}

export default createHandler();
