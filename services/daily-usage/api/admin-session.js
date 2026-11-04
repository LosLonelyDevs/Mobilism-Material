import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';
import { adminStore } from '../lib/admin-store.js';
import { CHALLENGE_COOKIE, CHALLENGE_SECONDS, SESSION_COOKIE, SESSION_SECONDS, configured, cookie,
  equal, isAdminRequest, makeToken, matchingStep, opaqueKey, readToken, sameOrigin } from '../lib/admin-auth.js';

export function createHandler({ database = query, env = process.env, now = Date.now } = {}) {
  const store = adminStore(database);
  return async (req, res) => {
    if (!configured(env)) return respond(res, 503, { error: 'Sign-in unavailable' });
    if (!['GET', 'POST', 'DELETE'].includes(req.method)) {
      res.setHeader('Allow', 'GET, POST, DELETE');
      return respond(res, 405, { error: 'Method not allowed' });
    }
    if (req.method !== 'GET' && !sameOrigin(req)) return respond(res, 403, { error: 'Request denied' });
    const time = now();
    const ip = env.VERCEL ? req.headers?.['x-vercel-forwarded-for'] || 'unknown' : req.socket?.remoteAddress || 'local';
    const client = opaqueKey(`client:${String(ip).split(',')[0].trim()}`, env);
    const account = opaqueKey(`account:${env.ADMIN_USERNAME}`, env);
    const challenge = readToken(req, CHALLENGE_COOKIE, CHALLENGE_SECONDS, env, time);
    const clearChallenge = cookie(req, CHALLENGE_COOKIE, '', 0, env);
    const locked = seconds => {
      res.setHeader('Retry-After', String(seconds));
      return respond(res, 429, { error: 'Too many attempts. Try again after the timer ends.', retryAfter: seconds });
    };
    try {
      if (req.method === 'DELETE') {
        if (challenge) await store.remove(challenge.nonce);
        res.setHeader('Set-Cookie', [clearChallenge, cookie(req, SESSION_COOKIE, '', 0, env)]);
        return respond(res, 200, { isAdmin: false, stage: 'password' });
      }
      if (req.method === 'GET') {
        if (isAdminRequest(req, env, time)) return respond(res, 200, { isAdmin: true });
        const seconds = await store.blocked([client, account], time);
        const pending = challenge && await store.pending(challenge.nonce, client, time);
        return respond(res, 200, { isAdmin: false, stage: pending ? 'otp' : 'password', retryAfter: seconds });
      }
      if (!req.headers?.['content-type']?.startsWith('application/json')) return respond(res, 415, { error: 'Use JSON' });
      let body = req.body;
      if (Buffer.isBuffer(body) || typeof body === 'string') {
        if (body.length > 2048) return respond(res, 413, { error: 'Request too large' });
        try { body = JSON.parse(body.toString()); } catch { return respond(res, 400, { error: 'Invalid request' }); }
      }
      if (!body || typeof body !== 'object' || Array.isArray(body)) return respond(res, 400, { error: 'Invalid request' });
      const blocked = await store.blocked([client, account], time);
      if (blocked) return locked(blocked);
      if (body.step === 'password') {
        const username = typeof body.username === 'string' ? body.username : '';
        const password = typeof body.password === 'string' ? body.password : '';
        if (username.length > 64 || password.length > 256 || !equal(`${username}\0${password}`, `${env.ADMIN_USERNAME}\0${env.ADMIN_PASSWORD}`)) {
          const seconds = await store.fail(equal(username, env.ADMIN_USERNAME) ? [client, account] : [client], time);
          return seconds ? locked(seconds) : respond(res, 401, { error: 'Invalid username or password.' });
        }
        if (challenge) await store.remove(challenge.nonce);
        const token = makeToken(CHALLENGE_COOKIE, CHALLENGE_SECONDS, env, time);
        await store.create(token.split('.')[1], client, time + CHALLENGE_SECONDS * 1000);
        res.setHeader('Set-Cookie', cookie(req, CHALLENGE_COOKIE, token, CHALLENGE_SECONDS, env));
        return respond(res, 200, { isAdmin: false, stage: 'otp' });
      }
      if (body.step !== 'otp') return respond(res, 400, { error: 'Invalid request' });
      if (!challenge || !await store.attempt(challenge.nonce, client, time)) {
        res.setHeader('Set-Cookie', clearChallenge);
        return respond(res, 401, { error: 'Sign in again to continue.', stage: 'password' });
      }
      const step = matchingStep(env.ADMIN_TOTP_SECRET, body.code, time);
      if (step === null || !await store.consume(challenge.nonce, client, account, step, time)) {
        const seconds = await store.fail([client, account], time);
        return seconds ? locked(seconds) : respond(res, 401, { error: 'Invalid or already used code. Wait for a new code and try again.' });
      }
      await store.reset([client, account]);
      res.setHeader('Set-Cookie', [clearChallenge, cookie(req, SESSION_COOKIE,
        makeToken(SESSION_COOKIE, SESSION_SECONDS, env, time), SESSION_SECONDS, env)]);
      return respond(res, 200, { isAdmin: true });
    } catch { return respond(res, 503, { error: 'Sign-in unavailable. Try again shortly.' }); }
  };
}

export default createHandler();
