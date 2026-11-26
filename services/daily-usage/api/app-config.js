import { query } from '../lib/database.js';
import { readConfig, publicConfig } from '../lib/app-config.js';
import { respond } from '../lib/protocol.js';
import { publicConfigTag, publicReadCache } from '../lib/public-cache.js';

export function createHandler(database = query, clock = () => Math.floor(Date.now() / 1000), store) {
  const cached = publicReadCache(clock, store);
  return async (req, res) => {
    if (req.method !== 'GET') {
      res.setHeader('Allow', 'GET');
      return respond(res, 405, { error: 'Use GET' });
    }
    try {
      const { body, expiresAt } = await cached('config', async () => {
        const now = clock();
        return { body: publicConfig(await readConfig(database)), expiresAt: now - now % 900 + 900 };
      }, publicConfigTag);
      res.setHeader('Vercel-Cache-Tag', publicConfigTag);
      res.setHeader('Vercel-CDN-Cache-Control', 'public, s-maxage=' + Math.max(0, expiresAt - clock()));
      return respond(res, 200, body);
    } catch { return respond(res, 503, { error: 'Configuration unavailable' }); }
  };
}

export default createHandler();
