export function adminStore(query) {
  return {
    async blocked(keys, now) {
      const rows = await query('SELECT blocked_until FROM admin_login_limits WHERE key = ANY($1::text[]) AND blocked_until > $2::timestamptz', [keys, new Date(now).toISOString()]);
      return Math.max(0, ...rows.map(row => Math.ceil((new Date(row.blocked_until).getTime() - now) / 1000)));
    },
    async fail(keys, now) {
      const rows = await query(`INSERT INTO admin_login_limits (key, failures, started_at, blocked_until)
        SELECT key, 1, $2::timestamptz, $2::timestamptz FROM unnest($1::text[]) AS key
        ON CONFLICT (key) DO UPDATE SET
          failures = CASE WHEN admin_login_limits.started_at <= $2::timestamptz - interval '15 minutes' THEN 1 ELSE admin_login_limits.failures + 1 END,
          started_at = CASE WHEN admin_login_limits.started_at <= $2::timestamptz - interval '15 minutes' THEN $2::timestamptz ELSE admin_login_limits.started_at END,
          blocked_until = CASE WHEN admin_login_limits.started_at > $2::timestamptz - interval '15 minutes' AND admin_login_limits.failures >= 4
            THEN $2::timestamptz + interval '15 minutes' ELSE $2::timestamptz END
        RETURNING blocked_until`, [keys, new Date(now).toISOString()]);
      return Math.max(0, ...rows.map(row => Math.ceil((new Date(row.blocked_until).getTime() - now) / 1000)));
    },
    async reset(keys) { await query('DELETE FROM admin_login_limits WHERE key = ANY($1::text[])', [keys]); },
    async create(nonce, client, expires) {
      await query(`INSERT INTO admin_login_challenges (nonce, client, expires_at) VALUES ($1, $2, $3::timestamptz)`, [nonce, client, new Date(expires).toISOString()]);
    },
    async pending(nonce, client, now) {
      return (await query('SELECT nonce FROM admin_login_challenges WHERE nonce = $1 AND client = $2 AND expires_at > $3::timestamptz AND attempts < 5', [nonce, client, new Date(now).toISOString()])).length > 0;
    },
    async attempt(nonce, client, now) {
      return (await query(`UPDATE admin_login_challenges SET attempts = attempts + 1 WHERE nonce = $1 AND client = $2
        AND expires_at > $3::timestamptz AND attempts < 5 RETURNING nonce`, [nonce, client, new Date(now).toISOString()])).length > 0;
    },
    async consume(nonce, client, account, step, now) {
      // Deleting the challenge and advancing the TOTP counter commit together. Concurrent replays cannot log in twice.
      return (await query(`WITH challenge AS (
          DELETE FROM admin_login_challenges WHERE nonce = $1 AND client = $2 AND expires_at > $5::timestamptz RETURNING nonce
        ) INSERT INTO admin_totp_counters (account, step) SELECT $3, $4::bigint FROM challenge
        ON CONFLICT (account) DO UPDATE SET step = EXCLUDED.step WHERE admin_totp_counters.step < EXCLUDED.step
        RETURNING step`, [nonce, client, account, step, new Date(now).toISOString()])).length > 0;
    },
    async remove(nonce) { await query('DELETE FROM admin_login_challenges WHERE nonce = $1', [nonce]); },
  };
}
