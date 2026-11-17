import { createCipheriv, createDecipheriv, createHash, randomBytes, randomInt, randomUUID, scrypt } from 'node:crypto';
import { promisify } from 'node:util';
import { encodeBase32, equal, opaqueKey, readToken, SESSION_COOKIE, SESSION_SECONDS } from './admin-auth.js';
import { PAGES } from '../public/access.js';

const derive = promisify(scrypt);
const options = { N: 32768, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const dummyHash = `scrypt:${'0'.repeat(32)}:${'0'.repeat(128)}`;
export const validPassword = value => typeof value === 'string' && value.length >= 12 && value.length <= 256;
export const validSecret = value => typeof value === 'string' && /^[A-Z2-7]{32}$/.test(value);

export async function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  return `scrypt:${salt}:${(await derive(password, salt, 64, options)).toString('hex')}`;
}

export async function verifyPassword(password, hash = dummyHash) {
  const [algorithm, salt, expected] = (hash || dummyHash).split(':');
  if (algorithm !== 'scrypt' || !/^[a-f0-9]{32}$/.test(salt) || !/^[a-f0-9]{128}$/.test(expected)) return false;
  const actual = await derive(password, salt, 64, options);
  return equal(actual.toString('hex'), expected);
}

const encryptionKey = env => createHash('sha256').update(`dashboard-totp:${env.ADMIN_SESSION_SECRET}`).digest();
export function sealSecret(secret, env) {
  const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', encryptionKey(env), iv);
  const encrypted = Buffer.concat([cipher.update(secret, 'utf8'), cipher.final()]);
  return [iv, cipher.getAuthTag(), encrypted].map(value => value.toString('base64url')).join('.');
}
export function openSecret(value, env) {
  const [iv, tag, encrypted] = value.split('.').map(part => Buffer.from(part, 'base64url'));
  const decipher = createDecipheriv('aes-256-gcm', encryptionKey(env), iv);
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(encrypted), decipher.final()]).toString('utf8');
}

export function totpCounter(user, env, secret = openSecret(user.totp_secret, env)) {
  // Access and password changes must not allow a previously accepted code again.
  return opaqueKey(`totp:${user.id}:${secret}`, env);
}

export function generateCredentials() {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%*-_';
  return { password: Array.from({ length: 20 }, () => alphabet[randomInt(alphabet.length)]).join(''),
    totpSecret: encodeBase32(randomBytes(20)) };
}

export function publicUser(row) {
  return { id: row.id, username: row.username, isOwner: row.id === 'owner', isAdmin: row.is_admin,
    pages: row.id === 'owner' ? PAGES.map(page => page.id) : row.pages, version: row.auth_version };
}

export function userStore(query, env) {
  return {
    async ensureOwner() {
      if ((await query("SELECT id FROM dashboard_users WHERE id = 'owner'")).length) return;
      await query(`INSERT INTO dashboard_users (id, username, password_hash, totp_secret, is_admin, pages)
        VALUES ('owner', $1, $2, $3, true, $4::text[]) ON CONFLICT (id) DO NOTHING`,
      [env.ADMIN_USERNAME, await hashPassword(env.ADMIN_PASSWORD), sealSecret(env.ADMIN_TOTP_SECRET, env), PAGES.map(page => page.id)]);
    },
    async find(username) { return (await query('SELECT * FROM dashboard_users WHERE lower(username) = lower($1)', [username]))[0]; },
    async get(id) { return (await query('SELECT * FROM dashboard_users WHERE id = $1', [id]))[0]; },
    async list() { return (await query("SELECT * FROM dashboard_users ORDER BY (id = 'owner') DESC, lower(username)")).map(publicUser); },
    async create({ username, password, totpSecret, isAdmin, pages }) {
      return publicUser((await query(`INSERT INTO dashboard_users (id, username, password_hash, totp_secret, is_admin, pages)
        VALUES ($1, $2, $3, $4, $5, $6::text[]) RETURNING *`,
      [randomUUID(), username, await hashPassword(password), sealSecret(totpSecret, env), isAdmin, pages]))[0]);
    },
    async updateAccess(id, version, isAdmin, pages) {
      const rows = await query(`UPDATE dashboard_users SET is_admin = $3, pages = $4::text[], auth_version = auth_version + 1
        WHERE id = $1 AND id <> 'owner' AND auth_version = $2 RETURNING *`, [id, version, isAdmin, pages]);
      return rows[0] && publicUser(rows[0]);
    },
    async remove(id, version) {
      return (await query("DELETE FROM dashboard_users WHERE id = $1 AND id <> 'owner' AND auth_version = $2 RETURNING id", [id, version])).length > 0;
    },
    async changeCredential(user, kind, value) {
      const column = kind === 'password' ? 'password_hash' : 'totp_secret';
      const stored = kind === 'password' ? await hashPassword(value) : sealSecret(value, env);
      return (await query(`UPDATE dashboard_users SET ${column} = $3, auth_version = auth_version + 1
        WHERE id = $1 AND auth_version = $2 RETURNING id`, [user.id, user.auth_version, stored])).length > 0;
    },
    async issueSession(nonce, user, expires) {
      await query(`INSERT INTO dashboard_sessions (nonce, user_id, auth_version, expires_at) VALUES ($1, $2, $3, $4::timestamptz)`,
        [nonce, user.id, user.auth_version, new Date(expires).toISOString()]);
    },
    async session(req, now) {
      const token = readToken(req, SESSION_COOKIE, SESSION_SECONDS, env, now);
      if (!token) return null;
      return (await query(`SELECT u.* FROM dashboard_sessions s JOIN dashboard_users u ON u.id = s.user_id AND u.auth_version = s.auth_version
        WHERE s.nonce = $1 AND s.expires_at > $2::timestamptz`, [token.nonce, new Date(now).toISOString()]))[0] || null;
    },
    async revokeSession(nonce) { await query('DELETE FROM dashboard_sessions WHERE nonce = $1', [nonce]); },
  };
}
