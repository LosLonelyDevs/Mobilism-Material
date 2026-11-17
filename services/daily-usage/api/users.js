import { query } from '../lib/database.js';
import { respond } from '../lib/protocol.js';
import { authorize, sessionUser } from '../lib/access.js';
import { sameOrigin } from '../lib/admin-auth.js';
import { generateCredentials, userStore, validPassword, validSecret } from '../lib/user-store.js';
import { requestBody } from '../lib/request-body.js';
import { PAGES } from '../public/access.js';

export function createHandler({ database = query, env = process.env, now = Date.now } = {}) {
  const users = userStore(database, env);
  return async (req, res) => {
    const actor = await authorize(req, res, 'users', request => sessionUser(request, { database, env, now: now() }));
    if (!actor) return;
    if (!['GET', 'POST', 'PUT', 'DELETE'].includes(req.method)) {
      res.setHeader('Allow', 'GET, POST, PUT, DELETE'); return respond(res, 405, { error: 'Method not allowed' });
    }
    if (req.method !== 'GET' && !sameOrigin(req)) return respond(res, 403, { error: 'Request denied' });
    try {
      if (req.method === 'GET') return respond(res, 200, { users: await users.list() });
      const body = requestBody(req, res, respond);
      if (!body) return;
      if (req.method === 'POST' && body.action === 'generate') return respond(res, 200, generateCredentials());
      if (req.method === 'DELETE') {
        if (body.id === 'owner' || body.id === actor.id) return respond(res, 403, { error: 'This account cannot be deleted.' });
        if (typeof body.id !== 'string' || !Number.isInteger(body.version)) return respond(res, 400, { error: 'Invalid user' });
        return await users.remove(body.id, body.version) ? respond(res, 200, { deleted: true }) : respond(res, 409, { error: 'User changed. Reload before deleting.' });
      }
      if (typeof body.isAdmin !== 'boolean' || !Array.isArray(body.pages) || body.pages.length > PAGES.length ||
        body.pages.some(page => !PAGES.some(item => item.id === page))) return respond(res, 400, { error: 'Choose valid page access and role.' });
      if (req.method === 'PUT') {
        if (body.id === 'owner') return respond(res, 403, { error: 'The owner always has admin access to every page.' });
        if (typeof body.id !== 'string' || !Number.isInteger(body.version)) return respond(res, 400, { error: 'Invalid user' });
        const user = await users.updateAccess(body.id, body.version, body.isAdmin, [...new Set(body.pages)]);
        return user ? respond(res, 200, { user, signInRequired: body.id === actor.id }) : respond(res, 409, { error: 'User changed. Reload before saving.' });
      }
      const username = typeof body.username === 'string' ? body.username.trim() : '';
      if (!/^[A-Za-z0-9._-]{3,64}$/.test(username)) return respond(res, 400, { error: 'Use 3 to 64 letters, numbers, dots, underscores, or hyphens for the username.' });
      if (!validPassword(body.password)) return respond(res, 400, { error: 'Use a password with 12 to 256 characters.' });
      if (!validSecret(body.totpSecret)) return respond(res, 400, { error: 'Generate a valid authenticator secret.' });
      return respond(res, 201, { user: await users.create({ ...body, username, pages: [...new Set(body.pages)] }) });
    } catch (error) {
      return respond(res, error.code === '23505' ? 409 : 503,
        { error: error.code === '23505' ? 'That username is already in use.' : 'User management unavailable. Try again.' });
    }
  };
}
export default createHandler();
