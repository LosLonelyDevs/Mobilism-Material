import { neon } from '@neondatabase/serverless';
import { capacitySql, catalogByteLimit, categoryTopByteLimit, retainedCatalogSql, retentionSecondsSql } from './follow-lookup.js';

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

export function cleanReports(query, now = Math.floor(Date.now() / 1000)) {
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
  ), follow_usage AS (
    DELETE FROM daily_followed_apps WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364
  ), lookup_receipts AS (
    DELETE FROM follow_lookup_receipts WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1
  ), login_limits AS (
    DELETE FROM admin_login_limits WHERE started_at < CURRENT_TIMESTAMP - interval '1 day' RETURNING key
  ), challenges AS (
    DELETE FROM admin_login_challenges WHERE expires_at < CURRENT_TIMESTAMP RETURNING nonce
  ), write_windows AS (
    DELETE FROM app_write_windows WHERE window_start < extract(epoch from now())::bigint - 3600
  ), trimmed AS (
    UPDATE latest_apps_cache SET items = (
      WITH unique_apps AS (
        SELECT item, item->>'key' AS app_key, (item->>'postedAt')::bigint AS posted, (item->>'id')::bigint AS id
        FROM jsonb_array_elements(items) item
        WHERE coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint) > $1::bigint - ${retentionSecondsSql}
      ) ${retainedCatalogSql('$1::bigint', '$2::date', '$3::numeric')}
    ), category_top = (
      SELECT coalesce(jsonb_agg(item ORDER BY position), '[]'::jsonb) FROM (
        SELECT item, position, sum(octet_length(item::text) + 2) OVER (ORDER BY position) AS bytes
        FROM jsonb_array_elements(category_top) WITH ORDINALITY AS chart(item, position)
        WHERE (item->>'lastSeen')::bigint > $1::bigint - ${retentionSecondsSql}
      ) live WHERE bytes <= ${categoryTopByteLimit}
    ) WHERE singleton AND (
      jsonb_array_length(items) > ${capacitySql} OR octet_length(items::text) > ${catalogByteLimit}
      OR octet_length(category_top::text) > ${categoryTopByteLimit}
      OR EXISTS (SELECT FROM jsonb_array_elements(items) item
        WHERE coalesce((item->>'lastSeen')::bigint, (item->>'postedAt')::bigint, 0) <= $1::bigint - ${retentionSecondsSql})
      OR EXISTS (SELECT FROM jsonb_array_elements(category_top) item
        WHERE coalesce((item->>'lastSeen')::bigint, 0) <= $1::bigint - ${retentionSecondsSql})
    )
  ), sessions AS (
    DELETE FROM dashboard_sessions WHERE expires_at < CURRENT_TIMESTAMP RETURNING nonce
  ) SELECT (SELECT count(*)::int FROM receipts) AS receipts,
           (SELECT count(*)::int FROM aggregates) AS aggregates`,
    [now, new Date(now * 1000).toISOString().slice(0, 10), 1 - (now % 86400) / 86400]);
}
