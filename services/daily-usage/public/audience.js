export const ruleFields = [
  ['username', 'Username'], ['versionName', 'App version'], ['versionCode', 'App build'],
  ['androidApi', 'Android API level'], ['signedIn', 'Signed in'],
];
export const listOperators = [['in', 'is one of'], ['notIn', 'is not one of']];
export const numberOperators = [['eq', 'equals'], ['lt', 'is below'], ['lte', 'is at most'], ['gt', 'is above'], ['gte', 'is at least']];
export const normalizeUsername = value => value.normalize('NFKC').trim().toLowerCase();
export function normalizeVersion(value) {
  const version = value.trim().replace(/^v/, '');
  if (!/^\d+\.\d+\.\d+$/.test(version) || version.split('.').some(part => Number(part) > 2147483647))
    throw new Error('App versions must look like 0.1.20.');
  return version.split('.').map(Number).join('.');
}

export function validateAudience(audience) {
  let count = 0;
  function visit(item, depth) {
    const fail = () => { throw new Error('Invalid audience. Use nonempty ALL/ANY groups, up to 3 levels and 24 rules.'); };
    if (!item || typeof item !== 'object' || Array.isArray(item) || ++count > 24 || depth > 3) fail();
    const keys = allowed => { if (Object.keys(item).some(key => !allowed.includes(key)) || allowed.some(key => !Object.hasOwn(item, key))) fail(); };
    if (item.type === 'everyone' && depth === 0) { keys(['type']); return { type: 'everyone' }; }
    if (item.type === 'all' || item.type === 'any') {
      keys(['type', 'conditions']);
      if (!Array.isArray(item.conditions) || item.conditions.length === 0 || item.conditions.length > 12) fail();
      return { type: item.type, conditions: item.conditions.map(child => visit(child, depth + 1)) };
    }
    if (item.type !== 'rule' || !ruleFields.some(([field]) => field === item.field)) fail();
    if (item.field === 'username' || item.field === 'versionName') {
      keys(['type', 'field', 'operator', 'values']);
      if (!listOperators.some(([op]) => op === item.operator) || !Array.isArray(item.values) || item.values.length < 1 || item.values.length > 100) fail();
      const values = item.values.map(value => {
        if (typeof value !== 'string' || !value.trim() || value.length > 100 || /[\u0000-\u001f\u007f]/.test(value)) fail();
        const normalized = item.field === 'username' ? normalizeUsername(value) : normalizeVersion(value);
        if (!normalized || normalized.length > 100) fail();
        return normalized;
      });
      return { type: 'rule', field: item.field, operator: item.operator, values: [...new Set(values)] };
    }
    keys(['type', 'field', 'operator', 'value']);
    if (item.field === 'signedIn') {
      if (item.operator !== 'eq' || typeof item.value !== 'boolean') fail();
    } else if (!numberOperators.some(([op]) => op === item.operator) || !Number.isInteger(item.value) ||
        item.value < 1 || item.value > (item.field === 'androidApi' ? 100 : 2147483647)) fail();
    return { type: 'rule', field: item.field, operator: item.operator, value: item.value };
  }
  return visit(audience, 0);
}

export function matchesAudience(audience, context) {
  if (audience.type === 'everyone') return true;
  if (audience.type === 'all') return audience.conditions.every(rule => matchesAudience(rule, context));
  if (audience.type === 'any') return audience.conditions.some(rule => matchesAudience(rule, context));
  if (audience.field === 'username' || audience.field === 'versionName') {
    const raw = context[audience.field];
    // Missing identity never satisfies a username exclusion either.
    if (typeof raw !== 'string' || !raw.trim()) return false;
    let value;
    try { value = audience.field === 'username' ? normalizeUsername(raw) : normalizeVersion(raw); } catch { return false; }
    const included = audience.values.includes(value);
    return audience.operator === 'in' ? included : !included;
  }
  const value = context[audience.field];
  if (audience.field === 'signedIn') return typeof value === 'boolean' && value === audience.value;
  if (!Number.isInteger(value) || value < 1) return false;
  return { eq: () => value === audience.value, lt: () => value < audience.value, lte: () => value <= audience.value,
    gt: () => value > audience.value, gte: () => value >= audience.value }[audience.operator]?.() ?? false;
}

export function collectUsernames(notices) {
  const names = new Set();
  function visit(rule) {
    if (rule?.field === 'username') rule.values.forEach(name => names.add(name));
    rule?.conditions?.forEach(visit);
  }
  notices.forEach(notice => visit(notice.audience));
  return [...names].sort();
}
