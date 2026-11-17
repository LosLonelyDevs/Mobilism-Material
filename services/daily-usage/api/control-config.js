import { query } from '../lib/database.js';
import { readConfig, saveConfig, publicConfig } from '../lib/app-config.js';
import { sameOrigin } from '../lib/admin-auth.js';
import { authorize, sessionUser } from '../lib/access.js';
import { respond } from '../lib/protocol.js';
import { validateConfig } from '../public/config-schema.js';

export function createHandler(database = query, session = req => sessionUser(req, { database })) {
  return async (req, res) => {
    if (!await authorize(req, res, 'app-controls', session)) return;
    if (!['GET', 'PUT'].includes(req.method)) {
      res.setHeader('Allow', 'GET, PUT');
      return respond(res, 405, { error: 'Use GET or PUT' });
    }
    if (req.method === 'GET') {
      try { return respond(res, 200, await readConfig(database)); }
      catch { return respond(res, 503, { error: 'Configuration unavailable' }); }
    }
    if (!sameOrigin(req)) return respond(res, 403, { error: 'Request denied' });
    if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) return respond(res, 415, { error: 'Use JSON' });
    let config;
    try {
      let body = req.body;
      if (Buffer.isBuffer(body)) body = body.toString('utf8');
      if (typeof body !== 'string') body = JSON.stringify(body);
      if (!body || Buffer.byteLength(body) > 65536) return respond(res, 413, { error: 'Configuration too large' });
      config = validateConfig(JSON.parse(body));
      if (Buffer.byteLength(JSON.stringify(publicConfig(config))) > 65536)
        return respond(res, 413, { error: 'Audience configuration too large. Use fewer usernames or notices.' });
    } catch (error) { return respond(res, 400, { error: error instanceof SyntaxError ? 'Invalid JSON' : error.message }); }
    try {
      const saved = await saveConfig(database, config);
      return saved ? respond(res, 200, saved) : respond(res, 409, { error: 'Configuration changed elsewhere. Reload before publishing.' });
    } catch { return respond(res, 503, { error: 'Could not publish. Try again.' }); }
  };
}

export default createHandler();
