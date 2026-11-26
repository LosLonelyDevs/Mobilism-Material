import { validateAudience } from './audience.js';
import { validateUserAgentRules } from './user-agent-rules.js';

export const limitFields = [
  ['searchSpacingMs', 'Minimum search spacing (ms)', 3000, 1000, 60000],
  ['searchCacheTtlMs', 'Search cache lifetime (ms)', 300000, 0, 3600000],
  ['searchFailureCooldownMs', 'Failed search cooldown (ms)', 60000, 60000, 3600000],
  ['categorySearchRequestLimit', 'Search requests per category', 6, 1, 30],
  ['categorySearchConcurrency', 'Concurrent category searches', 4, 1, 4],
  ['readRetryDelayMs', 'Read retry delay (ms)', 500, 0, 5000],
  ['readSpacingMs', 'Minimum read spacing (ms)', 250, 100, 60000],
  ['readConcurrency', 'Concurrent reads per endpoint', 3, 1, 3],
  ['readFailureCooldownMs', 'Initial failed read cooldown (ms)', 60000, 60000, 3600000],
  ['readFailureMaxCooldownMs', 'Maximum failed read cooldown (ms)', 900000, 60000, 3600000],
  ['discussionCategoriesCacheTtlMs', 'Discussion category cache lifetime (ms)', 300000, 0, 3600000],
  ['discussionFirstPageCacheTtlMs', 'Discussion first-page cache lifetime (ms)', 60000, 0, 3600000],
  ['messagePollIntervalMs', 'Minimum message check interval (ms)', 60000, 60000, 3600000],
  ['messageFailureMaxCooldownMs', 'Maximum failed message check cooldown (ms)', 900000, 60000, 3600000],
  ['memberLookupSpacingMs', 'Minimum member lookup spacing (ms)', 1000, 1000, 60000],
  ['memberCacheTtlMs', 'Member identity cache lifetime (ms)', 3600000, 60000, 86400000],
  ['memberMissingCacheTtlMs', 'Missing member cache lifetime (ms)', 3600000, 60000, 86400000],
  ['memberFailureCooldownMs', 'Initial failed member lookup cooldown (ms)', 60000, 60000, 3600000],
  ['memberFailureMaxCooldownMs', 'Maximum failed member lookup cooldown (ms)', 900000, 60000, 3600000],
  ['debridRefreshIntervalMs', 'Minimum debrid refresh interval (ms)', 900000, 60000, 3600000],
  ['releaseLatestCacheTtlMs', 'Latest release cache lifetime (ms)', 300000, 0, 86400000],
  ['releaseTopCacheTtlMs', 'Top release cache lifetime (ms)', 900000, 0, 86400000],
  ['premiumHostCacheTtlMs', 'Premium host catalog lifetime (ms)', 0, 0, 604800000],
  ['followBatchSize', 'Following apps per batch', 5, 1, 20],
  ['followAppIntervalMs', 'Minimum interval per followed app (ms)', 21600000, 3600000, 604800000],
  ['followAttemptIntervalMs', 'Minimum following batch interval (ms)', 900000, 900000, 86400000],
  ['followCacheIntervalMinutes', 'Shared-cache background interval (minutes)', 60, 15, 1440],
  ['followCacheBatchSize', 'Apps per background cache request (0 disables cache)', 200, 0, 512],
  ['followCacheManualCooldownMs', 'Manual cache check cooldown (ms)', 300000, 60000, 3600000],
];

export const cacheResetTargets = ['Latest', 'Top', 'RealDebrid', 'MegaDebrid', 'Mobilism'];
export const defaultStorage = { capacity: 1000, retentionHours: 0 };
export function validStorage(value) {
  return value && Object.keys(value).length === 2 && Number.isInteger(value.capacity) &&
    value.capacity >= 500 && value.capacity <= 2000 && Number.isInteger(value.retentionHours) &&
    (value.retentionHours === 0 || (value.retentionHours >= 6 && value.retentionHours <= 72));
}

export function defaultConfig() { return { schema: 2, revision: 0, limits: {}, notices: [], userAgentRules: [] }; }

export function validateConfig(input) {
  const object = value => value && typeof value === 'object' && !Array.isArray(value);
  const fail = message => { throw new Error(message); };
  const integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value);
  if (!object(input) || input.schema !== 2 || !integer(input.revision, 0, 2147483646) || !object(input.limits) ||
      !Array.isArray(input.notices) || input.notices.length > 10) fail('Invalid configuration. Use up to 10 notices.');
  if (Object.keys(input).some(key => !['schema', 'revision', 'limits', 'notices', 'userAgentRules', 'imageRetryGeneration', 'imageCacheEnabled', 'imageRetriesEnabled', 'cacheResets', 'followingStorage'].includes(key))) fail('Unknown configuration field.');
  const cache = {};
  if ('followingStorage' in input) {
    if (!validStorage(input.followingStorage)) fail('Choose 500 to 2,000 apps. Use 0 for no time limit or 6 to 72 hours.');
    cache.followingStorage = { ...input.followingStorage };
  }
  if ('cacheResets' in input) {
    if (!object(input.cacheResets) || Object.entries(input.cacheResets).some(([target, revision]) =>
      !cacheResetTargets.includes(target) || !integer(revision, 1, 2147483646))) fail('Invalid cache reset.');
    cache.cacheResets = { ...input.cacheResets };
  }
  const images = {};
  for (const field of ['imageCacheEnabled', 'imageRetriesEnabled']) {
    if (!Object.hasOwn(input, field)) continue;
    if (typeof input[field] !== 'boolean') fail('Image switches must be on or off.');
    images[field] = input[field];
  }
  if (Object.hasOwn(input, 'imageRetryGeneration')) {
    if (!integer(input.imageRetryGeneration, 1, 2147483646)) fail('Invalid image retry generation.');
    images.imageRetryGeneration = input.imageRetryGeneration;
  }
  const limits = {};
  for (const key of Object.keys(input.limits)) {
    if (key === 'followPullToRefreshEnabled') {
      if (typeof input.limits[key] !== 'boolean') fail('Pull to refresh must be on or off.');
      limits[key] = input.limits[key];
      continue;
    }
    const field = limitFields.find(field => field[0] === key);
    if (!field) fail('Unknown API threshold.');
    if (!integer(input.limits[key], field[3], field[4])) fail(`${field[1]} must be between ${field[3]} and ${field[4]}.`);
    limits[key] = input.limits[key];
  }
  const ids = new Set();
  const notices = input.notices.map(notice => {
    const fields = ['id', 'title', 'message', 'mode', 'frequency', 'enabled', 'startsAt', 'endsAt',
      'minVersionCode', 'maxVersionCode', 'requiredVersionCode', 'linkText', 'linkUrl', 'audience'];
    if (!object(notice) || Object.keys(notice).some(key => !fields.includes(key) && key !== 'deviceNotification') ||
        fields.some(key => !Object.hasOwn(notice, key))) fail('Invalid notice fields.');
    if (!text(notice.id, 80) || !/^[a-zA-Z0-9_-]+$/.test(notice.id) || ids.has(notice.id)) fail('Notice IDs must be unique.');
    ids.add(notice.id);
    if (!text(notice.title, 120) || !text(notice.message, 2000)) fail('Add a title (120 characters) and message (2000 characters).');
    if (!['notice', 'update', 'required'].includes(notice.mode) || !['once', 'launch', 'always'].includes(notice.frequency) ||
        typeof notice.enabled !== 'boolean') fail('Invalid notice behavior.');
    if (notice.deviceNotification !== undefined && typeof notice.deviceNotification !== 'boolean') fail('Invalid device notification setting.');
    const date = value => value === null || (typeof value === 'string' && /^\d{4}-\d\d-\d\dT/.test(value) &&
      Number.isFinite(Date.parse(value)) && new Date(value).toISOString() === value);
    if (!date(notice.startsAt) || !date(notice.endsAt) || (notice.startsAt && notice.endsAt && notice.startsAt >= notice.endsAt))
      fail('End time must follow start time.');
    if (!integer(notice.minVersionCode, 1, 2147483647) ||
        (notice.maxVersionCode !== null && !integer(notice.maxVersionCode, notice.minVersionCode, 2147483647)) ||
        (notice.requiredVersionCode !== null && !integer(notice.requiredVersionCode, 1, 2147483647))) fail('Invalid build range.');
    if (notice.mode === 'required' && (notice.requiredVersionCode === null || notice.requiredVersionCode <= notice.minVersionCode))
      fail('Required updates need a target build above the minimum audience build.');
    if (notice.linkUrl !== null) {
      let url;
      try { url = new URL(notice.linkUrl); } catch { fail('Link must be an HTTPS URL.'); }
      if (!text(notice.linkUrl, 2048) || url.protocol !== 'https:' || url.username || url.password || !text(notice.linkText, 80))
        fail('Use an HTTPS link without credentials and add its link text.');
    } else if (notice.linkText !== null) fail('Link text needs a URL.');
    return { ...notice, deviceNotification: notice.deviceNotification ?? false, audience: validateAudience(notice.audience) };
  });
  return { schema: 2, revision: input.revision, limits, notices, userAgentRules: validateUserAgentRules(input.userAgentRules), ...images, ...cache };
}
