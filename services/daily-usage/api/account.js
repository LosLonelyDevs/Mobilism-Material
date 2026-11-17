import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';
import { adminStore } from '../lib/admin-store.js';
import { requestBody } from '../lib/request-body.js';
import { cookie, matchingStep, opaqueKey, sameOrigin, SESSION_COOKIE, CHALLENGE_COOKIE } from '../lib/admin-auth.js';
import { generateCredentials, openSecret, totpCounter, userStore, validPassword, validSecret, verifyPassword } from '../lib/user-store.js';

export function createHandler({ database = query, env = process.env, now = Date.now } = {}) {
  const users = userStore(database, env), limits = adminStore(database);
  return async (req, res) => {
    try {
      const time = now(), user = await users.session(req, time);
      if (!user) return respond(res, 401, { error: 'Sign in to continue.' });
      if (req.method !== 'POST') { res.setHeader('Allow', 'POST'); return respond(res, 405, { error: 'Use POST' }); }
      if (!sameOrigin(req)) return respond(res, 403, { error: 'Request denied' });
      const body = requestBody(req, res, respond);
      if (!body) return;
      if (body.action === 'generate-password') return respond(res, 200, { password: generateCredentials().password });
      if (body.action === 'generate-totp') return respond(res, 200, { totpSecret: generateCredentials().totpSecret });
      if (!['password', 'totp'].includes(body.action)) return respond(res, 400, { error: 'Invalid action' });
      if (body.action === 'password' && !validPassword(body.newPassword)) return respond(res, 400, { error: 'Use a password with 12 to 256 characters.' });
      if (body.action === 'totp' && !validSecret(body.totpSecret)) return respond(res, 400, { error: 'Generate a valid authenticator secret.' });
      const ip = env.VERCEL ? req.headers?.['x-vercel-forwarded-for'] || 'unknown' : req.socket?.remoteAddress || 'local';
      const keys = [opaqueKey(`client:${String(ip).split(',')[0].trim()}`, env), opaqueKey(`account:${user.username.toLowerCase()}`, env)];
      const locked = seconds => { res.setHeader('Retry-After', String(seconds)); return respond(res, 429, { error: 'Too many attempts. Try again after the timer ends.', retryAfter: seconds }); };
      const blocked = await limits.blocked(keys, time);
      if (blocked) return locked(blocked);
      const password = typeof body.currentPassword === 'string' && body.currentPassword.length <= 256 ? body.currentPassword : '';
      const step = matchingStep(openSecret(user.totp_secret, env), body.currentCode, time);
      const newStep = body.action === 'totp' ? matchingStep(body.totpSecret, body.newCode, time) : 0;
      if (!await verifyPassword(password, user.password_hash) || step === null || newStep === null ||
        !await limits.useCode(totpCounter(user, env), step) ||
        (body.action === 'totp' && !await limits.useCode(totpCounter(user, env, body.totpSecret), newStep))) {
        const seconds = await limits.fail(keys, time);
        return seconds ? locked(seconds) : respond(res, 400, { error: 'Check your current password and authenticator codes. Use a fresh code.' });
      }
      await limits.reset(keys);
      if (!await users.changeCredential(user, body.action, body.action === 'password' ? body.newPassword : body.totpSecret))
        return respond(res, 409, { error: 'Account changed. Sign in again before saving.' });
      res.setHeader('Set-Cookie', [cookie(req, SESSION_COOKIE, '', 0, env), cookie(req, CHALLENGE_COOKIE, '', 0, env)]);
      return respond(res, 200, { signInRequired: true });
    } catch { return respond(res, 503, { error: 'Account update unavailable. Try again.' }); }
  };
}
export default createHandler();
