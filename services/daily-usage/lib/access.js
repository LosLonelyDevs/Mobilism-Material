import { query } from './database.js';
import { publicUser, userStore } from './user-store.js';
import { canAccess } from '../public/access.js';
import { respond } from './protocol.js';

export async function sessionUser(req, { database = query, env = process.env, now = Date.now() } = {}) {
  const row = await userStore(database, env).session(req, now);
  return row && publicUser(row);
}

export async function authorize(req, res, page, session) {
  try {
    const user = await session(req);
    if (!user) { respond(res, 401, { error: 'Sign in to continue.' }); return false; }
    if (user !== true && !canAccess(user, page)) { respond(res, 403, { error: 'You do not have access to this page.' }); return false; }
    return user;
  } catch { respond(res, 503, { error: 'Access check unavailable. Try again.' }); return false; }
}
