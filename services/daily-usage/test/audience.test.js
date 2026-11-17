import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateAudience, matchesAudience, normalizeUsername, collectUsernames } from '../public/audience.js';
import { publicConfig } from '../lib/app-config.js';

const users = (...values) => ({ type: 'rule', field: 'username', operator: 'in', values });
const versions = (...values) => ({ type: 'rule', field: 'versionName', operator: 'in', values });
const all = (...conditions) => ({ type: 'all', conditions });
const any = (...conditions) => ({ type: 'any', conditions });
const device = (username = 'alice', versionName = '0.1.20', values = {}) => ({ username, versionName,
  versionCode: 21, androidApi: 36, signedIn: !!username, ...values });

test('allowlist and version selections combine with AND and exclude every other user', () => {
  const audience = validateAudience(all(users(' Alice ', 'BOB'), versions('v0.1.20', '0.1.19')));
  assert.equal(matchesAudience(audience, device('ALICE')), true);
  assert.equal(matchesAudience(audience, device('bob', '0.1.19')), true);
  for (const context of [device('charlie'), device(''), device('alice', '0.1.21')]) assert.equal(matchesAudience(audience, context), false);
});
test('nested OR supports different users under different circumstances', () => {
  const audience = validateAudience(all(any(all(users('alice'), versions('0.1.20')), all(users('bob'), versions('0.1.19'))),
    { type: 'rule', field: 'androidApi', operator: 'gte', value: 33 }));
  assert.equal(matchesAudience(audience, device()), true);
  assert.equal(matchesAudience(audience, device('bob', '0.1.19')), true);
  for (const context of [device('bob'), device('alice', '0.1.19'), device('alice', '0.1.20', { androidApi: 32 }), device('')])
    assert.equal(matchesAudience(audience, context), false);
});
test('everyone, signed-in state, exclusions and numeric boundaries are explicit', () => {
  assert.equal(matchesAudience(validateAudience({ type: 'everyone' }), device('')), true);
  const excluded = validateAudience({ ...users('alice'), operator: 'notIn' });
  assert.equal(matchesAudience(excluded, device('')), false);
  assert.equal(matchesAudience(excluded, device('bob')), true);
  assert.equal(matchesAudience(validateAudience({ type: 'rule', field: 'signedIn', operator: 'eq', value: false }), device('')), true);
  for (const [operator, values] of [['eq', [21]], ['lt', [20]], ['lte', [20, 21]], ['gt', [22]], ['gte', [21, 22]]]) {
    const audience = validateAudience({ type: 'rule', field: 'versionCode', operator, value: 21 });
    for (const value of [20, 21, 22]) assert.equal(matchesAudience(audience, device('alice', '0.1.20', { versionCode: value })), values.includes(value));
  }
});
test('empty, unknown, malformed and excessively nested rules cannot broaden a notice', () => {
  const invalid = [{}, all(), any(), users(), { ...users('alice'), operator: 'contains' },
    { ...users('alice'), field: 'usernameHash' }, versions('latest'),
    any(users('alice'), { type: 'everyone' }), all(all(all(all(users('alice'))))),
    { type: 'rule', field: 'versionCode', operator: 'gte', value: '21' },
    { type: 'rule', field: 'signedIn', operator: 'eq', value: 'true' },
    { type: 'everyone', conditions: [users('alice')] }];
  for (const rule of invalid) assert.throws(() => validateAudience(rule));
});
test('public rules contain normalized hashes and never readable usernames', () => {
  const audience = validateAudience(all(users(' Alice ', 'Ａlice'), versions('v0.1.020')));
  assert.equal(normalizeUsername(' ＡLICE '), 'alice');
  assert.deepEqual(collectUsernames([{ audience }]), ['alice']);
  const config = publicConfig({ schema: 2, revision: 1, notices: [{ audience }] });
  assert.equal(JSON.stringify(config).includes('alice'), false);
  assert.deepEqual(config.notices[0].audience.conditions[0], { type: 'rule', field: 'usernameHash', operator: 'in',
    values: ['148224b0bb7b29e47327e246bdcb1cd727fbac22780e4cdac6775731bc06543c'] });
  assert.deepEqual(config.notices[0].audience.conditions[1].values, ['0.1.20']);
});
