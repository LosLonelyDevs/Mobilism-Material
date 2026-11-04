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
    )
    INSERT INTO daily_usage (day, app_version, version_code, android_api, manufacturer, model, devices)
    SELECT day, $3, $4, $5, $6, $7, 1 FROM receipt
    ON CONFLICT (day, app_version, version_code, android_api, manufacturer, model)
    DO UPDATE SET devices = daily_usage.devices + 1
    RETURNING devices`, [report.day, report.pingId, report.appVersion, report.versionCode,
      report.androidApi, report.manufacturer, report.model]);
  return rows.length > 0;
}

export function cleanReports(query) {
  return query(`WITH receipts AS (
    DELETE FROM usage_receipts WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 7 RETURNING day
  ), aggregates AS (
    DELETE FROM daily_usage WHERE day < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 364 RETURNING day
  ), login_limits AS (
    DELETE FROM admin_login_limits WHERE started_at < CURRENT_TIMESTAMP - interval '1 day' RETURNING key
  ), challenges AS (
    DELETE FROM admin_login_challenges WHERE expires_at < CURRENT_TIMESTAMP RETURNING nonce
  ) SELECT (SELECT count(*)::int FROM receipts) AS receipts,
           (SELECT count(*)::int FROM aggregates) AS aggregates`);
}
