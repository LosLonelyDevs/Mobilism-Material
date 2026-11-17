import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateConfig, defaultConfig } from '../public/config-schema.js';
import { matchesAudience } from '../public/audience.js';
import { versionRule, selectedVersions, noticeForEditor, hasVersionRestriction, noticesWithoutVersionTargeting, noticeSummary } from '../public/notice-editor.js';

const versions = [{ version: '0.1.21', versionCode: 22 }, { version: '0.1.20', versionCode: 21 }, { version: '0.1.9', versionCode: 10 }];
const everyone = { type: 'everyone' };
const user = { type: 'rule', field: 'username', operator: 'in', values: ['alice'] };
const notice = (changes = {}) => ({ id: 'test', title: 'Notice', message: 'A message', enabled: true, mode: 'notice', frequency: 'once',
  startsAt: null, endsAt: null, minVersionCode: 1, maxVersionCode: null, requiredVersionCode: null, linkText: null, linkUrl: null, audience: everyone, ...changes });

test('version dropdown ranges match existing APK rules at their boundaries', () => {
  for (const [operator, expected] of [['lt', [true, false, false]], ['lte', [true, true, false]], ['gt', [false, false, true]], ['gte', [false, true, true]]]) {
    const rule = versionRule(operator, ['0.1.20'], versions);
    assert.deepEqual([10, 21, 22].map(versionCode => matchesAudience(rule, { versionCode })), expected);
    assert.deepEqual(selectedVersions(rule, versions), ['0.1.20']);
  }
  const multiple = versionRule('in', ['0.1.9', '0.1.21'], versions);
  assert.equal(matchesAudience(multiple, { versionName: '0.1.20' }), false);
  assert.equal(matchesAudience(multiple, { versionName: '0.1.21' }), true);
  assert.throws(() => versionRule('lt', ['0.1.22'], versions));
});
test('removing separate gates preserves legacy targeting, including forced updates', () => {
  const previous = notice({ mode: 'required', minVersionCode: 10, maxVersionCode: 21, requiredVersionCode: 22, audience: { type: 'any', conditions: [user, versionRule('in', ['0.1.9'], versions)] } });
  const migrated = noticeForEditor(previous);
  validateConfig({ ...defaultConfig(), notices: [migrated] });
  for (const versionCode of [9, 10, 21, 22, 23]) for (const username of ['alice', 'bob']) {
    const context = { versionCode, versionName: versions.find(v => v.versionCode === versionCode)?.version || '0.1.0', username };
    const before = versionCode >= 10 && versionCode <= 21 && versionCode < 22 && matchesAudience(previous.audience, context);
    const after = versionCode >= migrated.minVersionCode && versionCode < migrated.requiredVersionCode && matchesAudience(migrated.audience, context);
    assert.equal(after, before);
  }
  assert.deepEqual(noticeForEditor(migrated), migrated);
});
test('schedules stay optional for every notice type and forced targeting lives in the audience', () => {
  for (const mode of ['notice', 'update', 'required']) {
    const value = noticeForEditor(notice({ mode, audience: versionRule('lt', ['0.1.21'], versions) }));
    validateConfig({ ...defaultConfig(), notices: [value] });
    assert.equal(value.startsAt, null); assert.equal(value.endsAt, null);
    assert.equal(matchesAudience(value.audience, { versionCode: 21 }), true);
    assert.equal(matchesAudience(value.audience, { versionCode: 22 }), false);
  }
});
test('save confirmation covers everyone and OR branches that bypass version targeting', () => {
  const version = versionRule('lt', ['0.1.21'], versions);
  assert.equal(hasVersionRestriction({ type: 'all', conditions: [user, version] }), true);
  assert.equal(hasVersionRestriction({ type: 'any', conditions: [user, version] }), false);
  assert.equal(hasVersionRestriction({ type: 'any', conditions: [version, { type: 'all', conditions: [user, version] }] }), true);
  const broad = notice();
  assert.equal(noticesWithoutVersionTargeting({ notices: [broad] }, { notices: [] }).length, 1);
  assert.equal(noticesWithoutVersionTargeting({ notices: [broad] }, { notices: [broad] }).length, 0);
  assert.equal(noticesWithoutVersionTargeting({ notices: [notice({ enabled: false })] }, { notices: [] }).length, 0);
  assert.equal(noticesWithoutVersionTargeting({ notices: [notice({ audience: version })] }, { notices: [] }).length, 0);
});
test('header summaries follow nested audience and schedule edits without exposing build numbers', () => {
  const value = notice({ mode: 'required', audience: { type: 'all', conditions: [user, versionRule('lt', ['0.1.21'], versions)] }, startsAt: '2026-12-01T00:00:00.000Z' });
  assert.equal(noticeSummary(value, versions), 'Required update · (alice AND Version < 0.1.21) · Scheduled');
  assert.equal(noticeSummary(notice(), versions), 'Notice · Everyone');
});
