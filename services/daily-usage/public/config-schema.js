import { validateAudience } from './audience.js';

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
];

export function defaultConfig() { return { schema: 2, revision: 0, limits: {}, notices: [] }; }

export function validateConfig(input) {
  const object = value => value && typeof value === 'object' && !Array.isArray(value);
  const fail = message => { throw new Error(message); };
  const integer = (value, min, max) => Number.isInteger(value) && value >= min && value <= max;
  const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value);
  if (!object(input) || input.schema !== 2 || !integer(input.revision, 0, 2147483646) || !object(input.limits) ||
      !Array.isArray(input.notices) || input.notices.length > 10) fail('Invalid configuration. Use up to 10 notices.');
  if (Object.keys(input).some(key => !['schema', 'revision', 'limits', 'notices'].includes(key))) fail('Unknown configuration field.');
  const limits = {};
  for (const key of Object.keys(input.limits)) {
    const field = limitFields.find(field => field[0] === key);
    if (!field) fail('Unknown API threshold.');
    if (!integer(input.limits[key], field[3], field[4])) fail(`${field[1]} must be between ${field[3]} and ${field[4]}.`);
    limits[key] = input.limits[key];
  }
  const ids = new Set();
  const notices = input.notices.map(notice => {
    const fields = ['id', 'title', 'message', 'mode', 'frequency', 'enabled', 'startsAt', 'endsAt',
      'minVersionCode', 'maxVersionCode', 'requiredVersionCode', 'linkText', 'linkUrl', 'audience'];
    if (!object(notice) || Object.keys(notice).some(key => !fields.includes(key)) ||
        fields.some(key => !Object.hasOwn(notice, key))) fail('Invalid notice fields.');
    if (!text(notice.id, 80) || !/^[a-zA-Z0-9_-]+$/.test(notice.id) || ids.has(notice.id)) fail('Notice IDs must be unique.');
    ids.add(notice.id);
    if (!text(notice.title, 120) || !text(notice.message, 2000)) fail('Add a title (120 characters) and message (2000 characters).');
    if (!['notice', 'update', 'required'].includes(notice.mode) || !['once', 'launch', 'always'].includes(notice.frequency) ||
        typeof notice.enabled !== 'boolean') fail('Invalid notice behavior.');
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
    return { ...notice, audience: validateAudience(notice.audience) };
  });
  return { schema: 2, revision: input.revision, limits, notices };
}
