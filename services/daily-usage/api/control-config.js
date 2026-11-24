import { query } from '../lib/database.js';
import { readConfig, saveConfig, publicConfig } from '../lib/app-config.js';
import { sameOrigin } from '../lib/admin-auth.js';
import { authorize, sessionUser } from '../lib/access.js';
import { respond } from '../lib/protocol.js';
import { validateConfig } from '../public/config-schema.js';

function controlsView(config) {
  const limits = { ...config.limits };
  delete limits.followPullToRefreshEnabled;
  const { followingStorage, ...controls } = config;
  return { ...controls, limits };
}

export function createHandler(database = query, session = req => sessionUser(req, { database })) {
  return async (req, res) => {
    const user = await authorize(req, res, 'app-controls', session);
    if (!user) return;
    if (!['GET', 'PUT'].includes(req.method)) {
      res.setHeader('Allow', 'GET, PUT');
      return respond(res, 405, { error: 'Use GET or PUT' });
    }
    if (req.method === 'GET') {
      try { return respond(res, 200, controlsView(await readConfig(database))); }
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
      if ('followingStorage' in config) return respond(res, 403, { error: 'Manage retention on the Following cache page.' });
      if ('followPullToRefreshEnabled' in config.limits) return respond(res, 403, { error: 'Manage pull-to-refresh on the Following cache page.' });
    } catch (error) { return respond(res, 400, { error: error instanceof SyntaxError ? 'Invalid JSON' : error.message }); }
    try {
      const current = await readConfig(database);
      if (config.revision !== current.revision) return respond(res, 409, { error: 'Configuration changed elsewhere. Reload before publishing.' });
      if (current.followingStorage) config.followingStorage = current.followingStorage;
      if (!user.isAdmin) for (const key of ['releaseLatestCacheTtlMs', 'releaseTopCacheTtlMs', 'premiumHostCacheTtlMs']) {
        if (config.limits[key] === undefined && current.limits[key] !== undefined) config.limits[key] = current.limits[key];
        if (config.limits[key] !== current.limits[key]) return respond(res, 403, { error: 'An administrator must change device cache lifetimes.' });
      }
      if (current.cacheResets || config.cacheResets) {
        const merged = { ...current.cacheResets, ...config.cacheResets };
        for (const [target, revision] of Object.entries(merged)) {
          if (revision === current.cacheResets?.[target]) continue;
          if (user.isAdmin !== true) return respond(res, 403, { error: 'An administrator must reset a cache.' });
          if (revision !== current.revision + 1) return respond(res, 400, { error: 'Reload before resetting a cache.' });
        }
        config.cacheResets = merged;
      }
      for (const field of ['imageCacheEnabled', 'imageRetriesEnabled']) {
        if (config[field] === undefined && current[field] !== undefined) config[field] = current[field];
        if ((config[field] ?? true) !== (current[field] ?? true) && user.isAdmin !== true)
          return respond(res, 403, { error: 'An administrator must change image controls.' });
      }
      if (config.imageRetryGeneration === undefined && current.imageRetryGeneration !== undefined)
        config.imageRetryGeneration = current.imageRetryGeneration;
      if (config.imageRetryGeneration !== current.imageRetryGeneration) {
        if (user.isAdmin !== true) return respond(res, 403, { error: 'An administrator must request image retries.' });
        if (config.imageRetriesEnabled === false) return respond(res, 400, { error: 'Enable failed image retries first.' });
        if (config.imageRetryGeneration !== current.revision + 1) return respond(res, 400, { error: 'Invalid image retry generation.' });
      }
      if ('followPullToRefreshEnabled' in current.limits) config.limits.followPullToRefreshEnabled = current.limits.followPullToRefreshEnabled;
      if (Buffer.byteLength(JSON.stringify(publicConfig(config))) > 65536)
        return respond(res, 413, { error: 'Audience configuration too large. Use fewer usernames or notices.' });
      const saved = await saveConfig(database, config);
      return saved ? respond(res, 200, controlsView(saved)) : respond(res, 409, { error: 'Configuration changed elsewhere. Reload before publishing.' });
    } catch { return respond(res, 503, { error: 'Could not publish. Try again.' }); }
  };
}

export default createHandler();
