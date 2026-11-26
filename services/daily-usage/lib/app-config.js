import { defaultConfig, validateConfig } from '../public/config-schema.js';
import { createHash } from 'node:crypto';
import { expirePublicConfig } from './public-cache.js';

export function publicConfig(config) {
  function audience(rule) {
    if (rule.field === 'username') return { ...rule, field: 'usernameHash', values: rule.values.map(value =>
      createHash('sha256').update(`mobilism-notice-user-v1\0${value}`).digest('hex')) };
    return rule.conditions ? { ...rule, conditions: rule.conditions.map(audience) } : rule;
  }
  const { followingStorage, ...client } = config;
  return { ...client, notices: config.notices.map(notice => ({ ...notice, audience: audience(notice.audience) })) };
}

export async function readConfig(database) {
  const rows = await database('SELECT revision, config FROM app_control WHERE id = 1');
  if (!rows.length) return defaultConfig();
  let config = rows[0].config;
  // Only stored legacy records migrate. Legacy editors cannot publish and erase targeting.
  if (config.schema === 1) config = { ...config, schema: 2,
    notices: config.notices.map(notice => ({ ...notice, audience: { type: 'everyone' } })) };
  return validateConfig({ ...config, revision: rows[0].revision });
}

export async function saveConfig(database, config, expire = expirePublicConfig) {
  const rows = await database(`INSERT INTO app_control (id, revision, config, updated_at)
    SELECT 1, 1, $2::jsonb, CURRENT_TIMESTAMP WHERE $1::integer = 0
    ON CONFLICT (id) DO UPDATE SET revision = app_control.revision + 1,
      config = EXCLUDED.config, updated_at = CURRENT_TIMESTAMP
    WHERE app_control.revision = $1::integer RETURNING revision`, [config.revision, JSON.stringify(config)]);
  let saved = rows[0];
  if (!saved && config.revision !== 0) [saved] = await database(`UPDATE app_control SET revision = revision + 1, config = $2::jsonb,
    updated_at = CURRENT_TIMESTAMP WHERE id = 1 AND revision = $1::integer RETURNING revision`,
  [config.revision, JSON.stringify(config)]);
  if (!saved) return null;
  try { await expire(); }
  catch { console.warn('Public configuration cache invalidation failed. Cached entries expire within 15 minutes.'); }
  return { ...config, revision: saved.revision };
}
