import test from 'node:test';
import assert from 'node:assert/strict';
import { validateUserAgentRules } from '../public/user-agent-rules.js';
import { defaultConfig, validateConfig } from '../public/config-schema.js';

const rule = values => ({ id: 'new-client', name: 'New clients', enabled: false, allVersions: false,
  versions: ['0.1.22'], userAgent: 'MobilismClient', statuses: [], ...values });

test('old configurations retain the shipped User-Agent and rules normalize without activation', () => {
  const legacy = defaultConfig(); delete legacy.userAgentRules;
  assert.deepEqual(validateConfig(legacy).userAgentRules, []);
  const [saved] = validateUserAgentRules([rule({ versions: ['0.1.22', '0.1.22'], statuses: [403, 403, 426] })]);
  assert.equal(saved.enabled, false);
  assert.deepEqual(saved.versions, ['0.1.22']);
  assert.deepEqual(saved.statuses, [403, 426]);
  assert.deepEqual(validateUserAgentRules([rule({ allVersions: true, versions: [] })])[0].versions, []);
});

test('header injection, arbitrary headers, unsupported statuses and ambiguous version targets are rejected', () => {
  for (const values of [{ userAgent: 'value\r\nAuthorization: injected' }, { userAgent: '\tvalue' },
    { userAgent: 'x'.repeat(257) }, { userAgent: 'é' }, { userAgent: '   ' }, { statuses: [401] },
    { statuses: [429] }, { statuses: [503] }, { statuses: ['403'] }, { versions: [] },
    { allVersions: true }, { versions: [22] }, { enabled: 'true' }, { Authorization: 'unexpected' }]) {
    assert.throws(() => validateUserAgentRules([rule(values)]));
  }
  assert.throws(() => validateUserAgentRules([rule(), rule()]));
  assert.throws(() => validateUserAgentRules(Array.from({ length: 11 }, (_, i) => rule({ id: `rule-${i}` }))));
});
