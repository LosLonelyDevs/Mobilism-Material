import { query } from '../lib/database.js';
import { authorized, DAY, respond, utcDay, validDay } from '../lib/protocol.js';
import { authorize, sessionUser } from '../lib/access.js';
import { releaseCatalog } from '../lib/releases.js';
import { supportsMinimumVersion } from '../public/limit-availability.js';
import { requestTrafficMinimumVersion } from '../public/request-traffic.js';

export function createHandler(database = query, token = () => process.env.STATS_TOKEN, now = () => new Date(), session = req => sessionUser(req, { database }),
  catalog = releaseCatalog) {
  return async (req, res) => {
    if (!authorized(req, token()) && !await authorize(req, res, 'app-usage', session)) return;
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return respond(res, 405, { error: 'Use GET' });
    }
    const params = new URL(req.url, 'https://usage.invalid').searchParams;
    const to = params.get('to') || utcDay(now());
    const from = params.get('from') || utcDay(new Date(now().getTime() - 29 * DAY));
    if (!validDay(from) || !validDay(to) || from > to || to > utcDay(now()) ||
        new Date(to) - new Date(from) > 364 * DAY) return respond(res, 400, { error: 'Invalid date range' });
    try {
      const breakdown = await database(`SELECT day::text, app_version AS "appVersion", version_code AS "versionCode",
        android_api AS "androidApi", manufacturer, model, devices::text
        FROM daily_usage WHERE day BETWEEN $1::date AND $2::date
        ORDER BY day, app_version, version_code, android_api, manufacturer, model`, [from, to]);
      const totals = new Map();
      for (const row of breakdown) totals.set(row.day, (totals.get(row.day) || 0n) + BigInt(row.devices));
      const daily = [];
      for (let date = new Date(from); date <= new Date(to); date = new Date(date.getTime() + DAY)) {
        const day = utcDay(date);
        daily.push({ day, devices: (totals.get(day) || 0n).toString() });
      }
      const trafficAvailable = await catalog().then(value => supportsMinimumVersion(value, requestTrafficMinimumVersion)).catch(() => false);
      const requestTraffic = trafficAvailable ? await database(`SELECT day::text, app_version AS "appVersion", version_code AS "versionCode",
        endpoint, method, json_build_array(successes::text, redirects::text, client_errors::text,
          rate_limited::text, server_errors::text, network_errors::text) AS counts, duration_ms::text AS "durationMs"
        FROM daily_requests WHERE day BETWEEN $1::date AND $2::date ORDER BY day, endpoint, method, version_code`, [from, to]) : [];
      return respond(res, 200, { from, to, daily, breakdown, trafficAvailable, requestTraffic });
    } catch { return respond(res, 503, { error: 'Statistics unavailable' }); }
  };
}

export default createHandler();
