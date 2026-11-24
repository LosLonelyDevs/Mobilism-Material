import { createHash, createHmac, createPublicKey, randomUUID, timingSafeEqual, verify } from 'node:crypto';

const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/;
export const seconds = () => Math.floor(Date.now() / 1000);
export const digest = value => createHash('sha256').update(value).digest();
export function enrollmentEnabled(env) {
  return /^[a-f0-9]{64}$/i.test(env.ANDROID_RELEASE_CERT_SHA256 || '') && env.APP_ATTESTATION_SECRET?.length >= 32;
}
function mac(type, body, secret) {
  return createHmac('sha256', secret).update(type + ':' + body).digest();
}
export function issueTicket(type, value, secret) {
  const body = Buffer.from(JSON.stringify(value)).toString('base64url');
  return body + '.' + mac(type, body, secret).toString('base64url');
}
export function openTicket(type, token, secret, now) {
  if (typeof token !== 'string' || token.length > 1024 || !/^[\w-]+\.[\w-]{43}$/.test(token)) throw Error('Invalid ticket');
  const [body, sig] = token.split('.');
  if (!timingSafeEqual(mac(type, body, secret), Buffer.from(sig, 'base64url'))) throw Error('Invalid ticket');
  const data = JSON.parse(Buffer.from(body, 'base64url'));
  if (!Number.isSafeInteger(data.exp) || data.exp <= now) throw Error('Expired ticket');
  return data;
}

// One bounded row per key and time window. Concurrent retries cannot spend the same nonce twice.
export async function spend(database, key, window, nonce, limit) {
  const rows = await database(`INSERT INTO app_write_windows(bucket, window_start, nonces)
    VALUES ($1, $2, ARRAY[$3::uuid]) ON CONFLICT (bucket, window_start) DO UPDATE
    SET nonces = array_append(app_write_windows.nonces, $3::uuid)
    WHERE cardinality(app_write_windows.nonces) < $4 AND NOT $3::uuid = ANY(app_write_windows.nonces)
    RETURNING 1`, [key, window, nonce, limit]);
  return rows.length === 1;
}
export function signatureInput(purpose, headers, envelope) {
  return ['mm-write-1', purpose, headers?.['x-following-check'] || '', headers?.['user-agent'] || '',
    envelope.at, envelope.nonce, envelope.ticket, envelope.payload].join('\n');
}
export async function authorizeAppWrite(body, req, purpose, database, env = process.env, now = seconds()) {
  const signed = body && typeof body === 'object' && Object.hasOwn(body, 'payload');
  const mode = env.APP_WRITE_POLICY ?? 'observe';
  if (!signed) {
    if (enrollmentEnabled(env)) console.info('app-write', purpose, 'unsigned');
    return { body, allowed: mode === 'observe' };
  }
  // Keep lookup data readable even if a proof expires or receipt storage is unavailable.
  const payload = typeof body.payload === 'string' ? JSON.parse(body.payload) : null;
  let allowed = false, renew = false, unavailable = false;
  try {
    if (!enrollmentEnabled(env) || typeof body.nonce !== 'string' || !uuid.test(body.nonce) || !Number.isSafeInteger(body.at) ||
        Math.abs(now - body.at) > 120 || typeof body.signature !== 'string' || !/^[\w-]{90,100}$/.test(body.signature))
      throw Error('Invalid proof');
    let ticket;
    try { ticket = openTicket('write', body.ticket, env.APP_ATTESTATION_SECRET, now); }
    catch { renew = true; throw Error('Ticket must be renewed'); }
    const { key } = ticket;
    const publicKey = createPublicKey({ format: 'der', type: 'spki', key: Buffer.from(key, 'base64url') });
    if (verify('sha256', Buffer.from(signatureInput(purpose, req.headers, body)), publicKey, Buffer.from(body.signature, 'base64url'))) {
      try { allowed = await spend(database, digest(key).toString('hex'), Math.floor(body.at / 60) * 60, body.nonce, 60); }
      catch { unavailable = true; }
    }
  } catch { /* An unverifiable signature grants no writes. */ }
  console.info('app-write', purpose, unavailable ? 'unavailable' : allowed ? 'verified' : 'denied');
  return { body: payload, allowed, renew, unavailable };
}

export function newChallenge(env, now) {
  return issueTicket('enroll', { nonce: randomUUID(), exp: now + 120 }, env.APP_ATTESTATION_SECRET);
}
