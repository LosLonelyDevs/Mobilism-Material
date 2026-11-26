import { dangerouslyDeleteByTag, getCache } from '@vercel/functions';

const platformCache = () => process.env.VERCEL ? getCache({ namespace: 'public-app-reads-v1' }) : null;
export const publicConfigTag = 'public-app-config';

export async function expirePublicConfig() {
  const cache = platformCache();
  if (!cache) return;
  try { await cache.expireTag(publicConfigTag); }
  finally { await dangerouslyDeleteByTag(publicConfigTag); }
}

// Only shared, public GET results belong here. Keep their original expiry across cache layers.
export function publicReadCache(clock, store = platformCache) {
  const pending = new Map();
  async function read(key, load, tag) {
    let cache;
    try {
      cache = store();
      const hit = await cache?.get(key);
      if (hit && hit.expiresAt > clock()) return hit;
    } catch { /* The origin still serves reads if the regional cache is unavailable. */ }
    const result = await load();
    const ttl = result.expiresAt - clock();
    if (cache && ttl > 0) {
      try { await cache.set(key, result, { ttl, ...(tag && { tags: [tag] }) }); }
      catch { /* Return the fresh origin result. */ }
    }
    return result;
  }
  return (key, load, tag) => {
    if (!pending.has(key)) pending.set(key, read(key, load, tag).finally(() => pending.delete(key)));
    return pending.get(key);
  };
}
