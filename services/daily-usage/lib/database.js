import { neon } from '@neondatabase/serverless';

let sql;
export function query(text, parameters = []) {
  if (!sql) {
    if (!process.env.DATABASE_URL) throw new Error('Database not configured');
    sql = neon(process.env.DATABASE_URL);
  }
  return sql.query(text, parameters);
}

export async function recordReport(query, report) {
  // Receipt insertion and aggregation share one statement and commit. A lost HTTP
  // response or competing function instance can never increment the counter twice.
  const rows = await query(`
    WITH receipt AS (
      INSERT INTO usage_receipts (day, ping_id)
      SELECT $1::date, $2::uuid
      WHERE $1::date BETWEEN (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 7
                        AND (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date
      ON CONFLICT DO NOTHING
      RETURNING day
    ), usage AS (
    INSERT INTO daily_usage (day, app_version, version_code, android_api, manufacturer, model, devices)
    SELECT day, $3, $4, $5, $6, $7, 1 FROM receipt
    ON CONFLICT (day, app_version, version_code, android_api, manufacturer, model)
    DO UPDATE SET devices = daily_usage.devices + 1
    RETURNING devices
    ), traffic_input AS (
      SELECT * FROM jsonb_to_recordset($8::jsonb)
        AS input(day date, id uuid, "appVersion" text, "versionCode" integer, metrics jsonb, caches jsonb)
    ), traffic_receipts AS (
      INSERT INTO request_receipts (day, ping_id)
      SELECT day, id FROM traffic_input
      WHERE day BETWEEN (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 14
                    AND (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1
      ON CONFLICT DO NOTHING RETURNING day, ping_id
    ), cache_usage AS (
      INSERT INTO daily_cache_usage (day, app_version, version_code, cache, hits, misses)
      SELECT input.day, input."appVersion", input."versionCode", item.key,
        SUM((item.value->>'hits')::bigint), SUM((item.value->>'misses')::bigint)
      FROM traffic_input input JOIN traffic_receipts receipt ON receipt.day = input.day AND receipt.ping_id = input.id
      CROSS JOIN LATERAL jsonb_each(input.caches) item
      GROUP BY input.day, input."appVersion", input."versionCode", item.key
      ON CONFLICT (day, app_version, version_code, cache)
      DO UPDATE SET hits = daily_cache_usage.hits + EXCLUDED.hits, misses = daily_cache_usage.misses + EXCLUDED.misses
    ), traffic AS (
      INSERT INTO daily_requests (day, app_version, version_code, endpoint, method,
        successes, redirects, client_errors, rate_limited, server_errors, network_errors, duration_ms)
      SELECT input.day, input."appVersion", input."versionCode", metric.endpoint, metric.method,
        SUM((metric.counts->>0)::bigint), SUM((metric.counts->>1)::bigint), SUM((metric.counts->>2)::bigint),
        SUM((metric.counts->>3)::bigint), SUM((metric.counts->>4)::bigint), SUM((metric.counts->>5)::bigint), SUM(metric."durationMs")
      FROM traffic_input input JOIN traffic_receipts receipt ON receipt.day = input.day AND receipt.ping_id = input.id
      CROSS JOIN LATERAL jsonb_to_recordset(input.metrics) AS metric(endpoint text, method text, counts jsonb, "durationMs" bigint)
      GROUP BY input.day, input."appVersion", input."versionCode", metric.endpoint, metric.method
      ON CONFLICT (day, app_version, version_code, endpoint, method)
      DO UPDATE SET successes = daily_requests.successes + EXCLUDED.successes,
        redirects = daily_requests.redirects + EXCLUDED.redirects,
        client_errors = daily_requests.client_errors + EXCLUDED.client_errors,
        rate_limited = daily_requests.rate_limited + EXCLUDED.rate_limited,
        server_errors = daily_requests.server_errors + EXCLUDED.server_errors,
        network_errors = daily_requests.network_errors + EXCLUDED.network_errors,
        duration_ms = daily_requests.duration_ms + EXCLUDED.duration_ms
      RETURNING day
    ) SELECT (SELECT count(*) FROM usage)::int AS inserted`, [report.day, report.pingId, report.appVersion, report.versionCode,
      report.androidApi, report.manufacturer, report.model, JSON.stringify(report.requestTraffic || [])]);
  return rows[0]?.inserted > 0;
}

export function cleanReports(query) {
  return query(`WITH receipts AS (
    DELETE FROM usage_receipts WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 7 RETURNING day
  ), aggregates AS (
    DELETE FROM daily_usage WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364 RETURNING day
  ), traffic_receipts AS (
    DELETE FROM request_receipts WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 14 RETURNING day
  ), traffic AS (
    DELETE FROM daily_requests WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364 RETURNING day
  ), cache_usage AS (
    DELETE FROM daily_cache_usage WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364
  ), login_limits AS (
    DELETE FROM admin_login_limits WHERE started_at < CURRENT_TIMESTAMP - interval '1 day' RETURNING key
  ), challenges AS (
    DELETE FROM admin_login_challenges WHERE expires_at < CURRENT_TIMESTAMP RETURNING nonce
  ), sessions AS (
    DELETE FROM dashboard_sessions WHERE expires_at < CURRENT_TIMESTAMP RETURNING nonce
  ) SELECT (SELECT count(*)::int FROM receipts) AS receipts,
           (SELECT count(*)::int FROM aggregates) AS aggregates`);
}
