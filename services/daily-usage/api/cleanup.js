import { query, cleanReports } from '../lib/database.js';
import { authorized, respond } from '../lib/protocol.js';

export function createHandler(database = query, token = () => process.env.CRON_SECRET) {
  return async (req, res) => {
    if (!authorized(req, token())) return respond(res, 401, { error: 'Unauthorized' });
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return respond(res, 405, { error: 'Use GET' });
    }
    try { return respond(res, 200, (await cleanReports(database))[0]); }
    catch { return respond(res, 503, { error: 'Cleanup unavailable' }); }
  };
}

export default createHandler();
