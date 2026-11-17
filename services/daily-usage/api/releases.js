import { isAdminRequest } from '../lib/admin-auth.js';
import { respond } from '../lib/protocol.js';
import { releaseCatalog } from '../lib/releases.js';

export function createHandler(catalog = releaseCatalog, session = isAdminRequest) {
  return async (req, res) => {
    if (!session(req)) return respond(res, 401, { error: 'Sign in to view app versions.' });
    if (req.method !== 'GET') { res.setHeader('Allow', 'GET'); return respond(res, 405, { error: 'Use GET' }); }
    try { return respond(res, 200, await catalog()); }
    catch { return respond(res, 503, { error: 'Could not load GitHub releases. Try again.' }); }
  };
}

export default createHandler();
