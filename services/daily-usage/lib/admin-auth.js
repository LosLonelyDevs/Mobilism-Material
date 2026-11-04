import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

export const SESSION_COOKIE = 'mobilism_usage_session';
export const CHALLENGE_COOKIE = 'mobilism_usage_challenge';
export const SESSION_SECONDS = 12 * 60 * 60;
export const CHALLENGE_SECONDS = 5 * 60;
const digest = value => createHash('sha256').update(String(value)).digest();
export const equal = (a, b) => timingSafeEqual(digest(a), digest(b));

export function configured(env) {
  return typeof env.ADMIN_USERNAME === 'string' && env.ADMIN_USERNAME.length >= 4 &&
    typeof env.ADMIN_PASSWORD === 'string' && env.ADMIN_PASSWORD.length >= 24 &&
    /^[A-Z2-7]{32,64}$/.test(env.ADMIN_TOTP_SECRET || '') && (env.ADMIN_SESSION_SECRET?.length || 0) >= 32;
}

function key(env) {
  // Credential rotation invalidates both sessions and pending second-factor challenges.
  return createHmac('sha256', env.ADMIN_SESSION_SECRET)
    .update(JSON.stringify([env.ADMIN_USERNAME, env.ADMIN_PASSWORD, env.ADMIN_TOTP_SECRET])).digest();
}

export function opaqueKey(value, env) {
  return createHmac('sha256', key(env)).update(value).digest('hex');
}

export function makeToken(purpose, seconds, env, now) {
  const payload = `${now + seconds * 1000}.${randomBytes(16).toString('hex')}`;
  return `${payload}.${createHmac('sha256', key(env)).update(`${purpose}:${payload}`).digest('hex')}`;
}

export function readToken(req, purpose, seconds, env, now) {
  if (!configured(env)) return null;
  const value = (req.headers?.cookie || '').split(';').map(part => part.trim())
    .find(part => part.startsWith(`${purpose}=`))?.slice(purpose.length + 1);
  if (!value || value.length > 200) return null;
  const [expiry, nonce, signature, extra] = value.split('.');
  if (extra !== undefined || !/^\d{13}$/.test(expiry) || !/^[a-f0-9]{32}$/.test(nonce) || !/^[a-f0-9]{64}$/.test(signature)) return null;
  const expires = Number(expiry);
  if (expires <= now || expires > now + seconds * 1000) return null;
  const expected = createHmac('sha256', key(env)).update(`${purpose}:${expiry}.${nonce}`).digest('hex');
  return equal(signature, expected) ? { value, nonce, expires } : null;
}

export function isAdminRequest(req, env = process.env, now = Date.now()) {
  return !!readToken(req, SESSION_COOKIE, SESSION_SECONDS, env, now);
}

export function cookie(req, name, value, seconds, env) {
  const secure = env.VERCEL || req.socket?.encrypted || req.headers?.['x-forwarded-proto'] === 'https';
  return `${name}=${value}; Path=/; HttpOnly; SameSite=Strict; Max-Age=${seconds}${secure ? '; Secure' : ''}`;
}

export function sameOrigin(req) {
  try {
    const origin = new URL(req.headers.origin);
    const protocol = req.headers['x-forwarded-proto'] || (req.socket?.encrypted ? 'https' : 'http');
    return origin.host === req.headers.host && origin.protocol === `${protocol}:`;
  } catch { return false; }
}

export function encodeBase32(bytes) {
  let bits = 0, value = 0, result = '';
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      bits -= 5;
      const index = (value >>> bits) & 31;
      result += String.fromCharCode(index < 26 ? 65 + index : 50 + index - 26);
    }
  }
  if (bits) {
    const index = (value << (5 - bits)) & 31;
    result += String.fromCharCode(index < 26 ? 65 + index : 50 + index - 26);
  }
  return result;
}

export function totp(secret, step, digits = 6) {
  let bits = 0, value = 0;
  const bytes = [];
  for (const character of secret) {
    const code = character.charCodeAt(0);
    const index = code >= 65 && code <= 90 ? code - 65 : code >= 50 && code <= 55 ? code - 24 : -1;
    if (index < 0) throw new Error('Invalid authenticator secret');
    value = (value << 5) | index;
    bits += 5;
    if (bits >= 8) { bits -= 8; bytes.push((value >>> bits) & 255); }
  }
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hash = createHmac('sha1', Buffer.from(bytes)).update(counter).digest();
  const offset = hash[hash.length - 1] & 15;
  return ((hash.readUInt32BE(offset) & 0x7fffffff) % (10 ** digits)).toString().padStart(digits, '0');
}

export function matchingStep(secret, code, now) {
  if (typeof code !== 'string' || !/^\d{6}$/.test(code)) return null;
  const current = Math.floor(now / 30_000);
  for (const step of [current, current - 1, current + 1]) {
    if (equal(code, totp(secret, step))) return step;
  }
  return null;
}
