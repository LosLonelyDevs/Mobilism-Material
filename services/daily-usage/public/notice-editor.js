const maximumVersionCode = 2147483647;
export const versionOperators = [['in', 'is one of'], ['notIn', 'is not one of'], ['lt', 'is less than'], ['lte', 'is at most'], ['gt', 'is greater than'], ['gte', 'is at least']];
export const isVersionRule = rule => ['versionName', 'versionCode'].includes(rule.field);

export function versionRule(operator, selection, versions) {
  if (operator === 'in' || operator === 'notIn') return { type: 'rule', field: 'versionName', operator, values: selection };
  const release = versions.find(row => row.version === selection[0]);
  if (!release) throw new Error('Choose a verified app version.');
  return { type: 'rule', field: 'versionCode', operator, value: release.versionCode };
}
export function selectedVersions(rule, versions) {
  return rule.field === 'versionName' ? rule.values : versions.filter(row => row.versionCode === rule.value).map(row => row.version);
}

export function noticeForEditor(notice) {
  const restrictions = [];
  if (notice.minVersionCode > 1) restrictions.push({ type: 'rule', field: 'versionCode', operator: 'gte', value: notice.minVersionCode });
  if (notice.maxVersionCode !== null) restrictions.push({ type: 'rule', field: 'versionCode', operator: 'lte', value: notice.maxVersionCode });
  if (notice.mode === 'required' && notice.requiredVersionCode && notice.requiredVersionCode < maximumVersionCode)
    restrictions.push({ type: 'rule', field: 'versionCode', operator: 'lt', value: notice.requiredVersionCode });
  let audience = notice.audience;
  if (restrictions.length) audience = { type: 'all', conditions: [...(audience.type === 'everyone' ? [] : audience.type === 'all' ? audience.conditions : [audience]), ...restrictions] };
  return { ...notice, audience, minVersionCode: 1, maxVersionCode: null,
    requiredVersionCode: notice.mode === 'required' ? maximumVersionCode : null };
}

export function hasVersionRestriction(audience) {
  if (audience.type === 'rule') return isVersionRule(audience);
  if (!audience.conditions?.length) return false;
  return audience.type === 'all' ? audience.conditions.some(hasVersionRestriction) : audience.conditions.every(hasVersionRestriction);
}
export function noticesWithoutVersionTargeting(draft, published) {
  return draft.notices.filter(notice => notice.enabled && !hasVersionRestriction(notice.audience) &&
    JSON.stringify(notice) !== JSON.stringify(published.notices.find(old => old.id === notice.id)));
}

export function audienceSummary(audience, versions = []) {
  if (audience.type === 'everyone') return 'Everyone';
  if (audience.conditions) return `(${audience.conditions.map(child => audienceSummary(child, versions)).join(audience.type === 'all' ? ' AND ' : ' OR ')})`;
  const operator = { in: 'is', notIn: 'is not', eq: '=', lt: '<', lte: '≤', gt: '>', gte: '≥' }[audience.operator];
  if (isVersionRule(audience)) {
    const names = selectedVersions(audience, versions);
    return `Version ${operator} ${names.length ? names.join(', ') : audience.field === 'versionCode' ? 'unavailable release' : 'not selected'}`;
  }
  if (audience.field === 'username') return `${audience.operator === 'notIn' ? 'Except ' : ''}${audience.values.length ? audience.values.join(', ') : 'No users selected'}`;
  if (audience.field === 'signedIn') return audience.value ? 'Signed in' : 'Signed out';
  return `Android API ${operator} ${audience.value}`;
}

export function noticeSummary(notice, versions) {
  const parts = [{ notice: 'Notice', update: 'Update', required: 'Required update' }[notice.mode], audienceSummary(notice.audience, versions)];
  if (notice.startsAt || notice.endsAt) parts.push('Scheduled');
  return parts.join(' · ');
}
