export const requestTrafficMinimumVersion = '0.1.24';
export const requestEndpoints = ['api/login', 'api/register', 'api/restore', 'api/categories', 'api/releases',
  'api/release', 'api/search', 'api/comments', 'api/messages', 'api/reactions', 'api/discussions', 'api/other',
  'forum/topic', 'forum/list', 'forum/profile', 'forum/search', 'forum/other', 'images'];
export const requestMethods = ['GET', 'POST', 'PUT', 'DELETE', 'HEAD', 'OPTIONS', 'OTHER'];
export const requestOutcomeLabels = ['Success', 'Redirects', 'Client errors', '429', 'Server errors', 'Network errors'];
export const cacheLabels = { FollowingBackground: 'Followed apps · background', FollowingManual: 'Followed apps · manual', MemberIdentity: 'Member avatars and badges',
  Search: 'Search results', Read: 'Forum API reads' };

export function validateRequestTraffic(value, reportDay) {
  if (value === undefined) return true;
  if (!Array.isArray(value) || value.length > 8) return false;
  const exact = (object, keys) => object && !Array.isArray(object) && typeof object === 'object' &&
    Object.keys(object).length === keys.length && keys.every(key => Object.hasOwn(object, key));
  const day = value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value;
  const earliest = new Date(Date.parse(reportDay) - 7 * 86400000).toISOString().slice(0, 10);
  const ids = new Set();
  return value.every(bucket => {
    const keys = ['day', 'id', 'appVersion', 'versionCode', 'metrics'];
    if (bucket && Object.hasOwn(bucket, 'caches')) {
      keys.push('caches');
      const caches = bucket.caches;
      if (!caches || typeof caches !== 'object' || Array.isArray(caches) || !Object.keys(caches).length ||
          !Object.entries(caches).every(([name, counts]) => Object.hasOwn(cacheLabels, name) && exact(counts, ['hits', 'misses']) &&
            [counts.hits, counts.misses].every(n => Number.isInteger(n) && n >= 0 && n <= 1000000) && counts.hits + counts.misses > 0)) return false;
    }
    if (!exact(bucket, keys) || !day(bucket.day) ||
        bucket.day < earliest || bucket.day >= reportDay || typeof bucket.id !== 'string' ||
        !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(bucket.id) || ids.has(bucket.id.toLowerCase()) ||
        typeof bucket.appVersion !== 'string' || !/^\d+\.\d+\.\d+$/.test(bucket.appVersion) || bucket.appVersion.length > 50 ||
        !Number.isInteger(bucket.versionCode) || bucket.versionCode < 1 || bucket.versionCode > 2147483647 ||
        !Array.isArray(bucket.metrics) || (!bucket.metrics.length && !bucket.caches) || bucket.metrics.length > 128) return false;
    ids.add(bucket.id.toLowerCase());
    const rows = new Set();
    return bucket.metrics.every(metric => {
      if (!exact(metric, ['endpoint', 'method', 'counts', 'durationMs']) || !requestEndpoints.includes(metric.endpoint) ||
          !requestMethods.includes(metric.method) || !Array.isArray(metric.counts) || metric.counts.length !== 6 ||
          metric.counts.some(n => !Number.isInteger(n) || n < 0 || n > 1000000)) return false;
      const count = metric.counts.reduce((sum, n) => sum + n, 0), key = `${metric.method} ${metric.endpoint}`;
      if (count < 1 || count > 1000000 || rows.has(key) || !Number.isSafeInteger(metric.durationMs) ||
          metric.durationMs < 0 || metric.durationMs > count * 300000) return false;
      rows.add(key); return true;
    });
  });
}

export function groupRequestTraffic(rows, day) {
  const groups = new Map();
  for (const row of rows || []) {
    if (day !== undefined && row.day !== day) continue;
    const key = `${row.method} ${row.endpoint}`;
    const group = groups.get(key) || { endpoint: row.endpoint, method: row.method, counts: Array(6).fill(0n), durationMs: 0n };
    row.counts.forEach((count, index) => { group.counts[index] += BigInt(count); });
    group.durationMs += BigInt(row.durationMs); groups.set(key, group);
  }
  return [...groups.values()].map(group => ({ ...group, requests: group.counts.reduce((sum, n) => sum + n, 0n) }))
    .sort((a, b) => a.requests === b.requests ? a.endpoint.localeCompare(b.endpoint) : a.requests > b.requests ? -1 : 1);
}
