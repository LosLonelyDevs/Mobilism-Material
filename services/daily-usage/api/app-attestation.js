import { createHmac, randomUUID } from 'node:crypto';
import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';
import { verifyAndroidKey } from '../lib/android-attestation.js';
import { digest, enrollmentEnabled, issueTicket, newChallenge, openTicket, seconds, spend } from '../lib/app-writes.js';

export function createHandler(database = query, env = process.env, clock = seconds, verifyKey = verifyAndroidKey) {
  return async (req, res) => {
    if (!enrollmentEnabled(env)) return respond(res, 503, { error: 'Enrollment unavailable' });
    const now = clock();
    if (req.method === 'GET') return respond(res, 200, { challenge: newChallenge(env, now) });
    if (req.method !== 'POST') return respond(res, 405, { error: 'Unsupported method' });
    let body, challenge;
    try {
      if (!/^application\/json(?:;|$)/i.test(req.headers?.['content-type'] || '')) throw Error();
      const raw = typeof req.body === 'string' || Buffer.isBuffer(req.body) ? req.body : JSON.stringify(req.body);
      if (!raw || Buffer.byteLength(raw) > 65536) throw Error();
      body = JSON.parse(raw);
      challenge = openTicket('enroll', body.challenge, env.APP_ATTESTATION_SECRET, now);
    } catch { return respond(res, 400, { error: 'Invalid enrollment' }); }
    try {
      const address = env.VERCEL ? req.headers?.['x-vercel-forwarded-for'] : req.socket?.remoteAddress;
      const window = Math.floor(now / 600) * 600;
      const bucket = createHmac('sha256', env.APP_ATTESTATION_SECRET).update('enroll:' + window + ':' + (address || 'unknown')).digest('hex');
      if (!await spend(database, bucket, window, randomUUID(), 120)) return respond(res, 429, { error: 'Try later' });
      if (!await spend(database, 'challenge:' + challenge.nonce, challenge.exp, challenge.nonce, 1))
        return respond(res, 409, { error: 'Used challenge' });
    } catch { return respond(res, 503, { error: 'Enrollment unavailable' }); }
    try {
      const key = await verifyKey(body.chain, digest(body.challenge), env.ANDROID_RELEASE_CERT_SHA256.toLowerCase());
      const exp = now + 86400;
      const ticket = issueTicket('write', { key, exp }, env.APP_ATTESTATION_SECRET);
      return respond(res, 200, { ticket, expiresAt: exp });
    } catch { return respond(res, 403, { error: 'Unable to verify install' }); }
  };
}
export default createHandler();
