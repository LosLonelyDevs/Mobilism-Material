import { authorize, sessionUser } from '../lib/access.js';
import { respond } from '../lib/protocol.js';
import { releaseCatalog } from '../lib/releases.js';

export function createHandler(catalog = releaseCatalog, session = sessionUser) {
  return async (req, res) => {
    if (!await authorize(req, res, 'app-controls', session)) return;
    if (req.method !== 'GET') { res.setHeader('Allow', 'GET'); return respond(res, 405, { error: 'Use GET' }); }
    try { return respond(res, 200, await catalog()); }
    catch { return respond(res, 503, { error: 'Could not load GitHub releases. Try again.' }); }
  };
}

export default createHandler();
