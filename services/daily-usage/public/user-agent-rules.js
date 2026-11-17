export const userAgentStatuses = [[403, '403 · Forbidden'], [406, '406 · Not acceptable'], [426, '426 · Upgrade required']];

export function validateUserAgentRules(value = []) {
  const fail = message => { throw new Error(message); };
  if (!Array.isArray(value) || value.length > 10) fail('Use up to 10 User-Agent rules.');
  const ids = new Set();
  return value.map(rule => {
    const fields = ['id', 'name', 'enabled', 'allVersions', 'versions', 'userAgent', 'statuses'];
    if (!rule || typeof rule !== 'object' || Array.isArray(rule) ||
        Object.keys(rule).some(key => !fields.includes(key)) || fields.some(key => !Object.hasOwn(rule, key))) fail('Invalid User-Agent rule.');
    if (typeof rule.id !== 'string' || !/^[A-Za-z0-9_-]{1,80}$/.test(rule.id) || ids.has(rule.id)) fail('User-Agent rule IDs must be unique.');
    ids.add(rule.id);
    if (typeof rule.name !== 'string' || !rule.name.trim() || rule.name.length > 80 || /[\u0000-\u001f\u007f]/.test(rule.name)) fail('Add a rule name, up to 80 characters.');
    if (typeof rule.enabled !== 'boolean' || typeof rule.allVersions !== 'boolean') fail('Invalid User-Agent rule state.');
    if (!Array.isArray(rule.versions) || rule.versions.length > 100 || rule.versions.some(version =>
      typeof version !== 'string' || version.length > 50 || !/^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/.test(version)) ||
      (rule.allVersions ? rule.versions.length !== 0 : rule.versions.length === 0)) fail('Choose app versions or target all versions.');
    if (typeof rule.userAgent !== 'string' || !/^[\x20-\x7e]{1,256}$/.test(rule.userAgent) || !rule.userAgent.trim()) fail('Use 1 to 256 printable ASCII characters for the User-Agent prefix.');
    if (!Array.isArray(rule.statuses) || rule.statuses.length > userAgentStatuses.length ||
        rule.statuses.some(status => !userAgentStatuses.some(([code]) => code === status))) fail('Choose supported HTTP statuses: 403, 406, or 426.');
    return { ...rule, name: rule.name.trim(), userAgent: rule.userAgent.trim(),
      versions: [...new Set(rule.versions)], statuses: [...new Set(rule.statuses)] };
  });
}
