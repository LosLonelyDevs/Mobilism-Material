import { query } from '../lib/database.js';
import { readConfig, publicConfig } from '../lib/app-config.js';
import { respond } from '../lib/protocol.js';

export function createHandler(database = query) {
  return async (req, res) => {
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return respond(res, 405, { error: 'Use GET' });
    }
    try { return respond(res, 200, publicConfig(await readConfig(database))); }
    catch { return respond(res, 503, { error: 'Configuration unavailable' }); }
  };
}

export default createHandler();
