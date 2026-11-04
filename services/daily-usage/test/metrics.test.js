import { test } from 'node:test';
import assert from 'node:assert/strict';
import { groupUsage, percentage } from '../public/metrics.js';

const row = overrides => ({ appVersion: '0.1.19', versionCode: 20, manufacturer: 'Google', model: 'Pixel', androidApi: 36, devices: '1', ...overrides });

test('version shares combine builds and device rows without double counting', () => {
  const groups = groupUsage([row({ devices: '30' }), row({ devices: '19', versionCode: 21 }), row({ appVersion: '0.1.18', versionCode: 19, devices: '2' })], 'versions');
  assert.equal(groups.length, 2);
  assert.equal(groups[0].devices, 49n);
  assert.equal(groups[0].share, 96.1);
  assert.equal(groups[0].detail, 'Build 20, 21');
  assert.equal(groups[1].share, 3.9);
});

test('device shares combine app versions and Android versions on the same model', () => {
  const rows = [row({ devices: '2' }), row({ manufacturer: 'google', appVersion: '0.1.18', androidApi: 35 }), row({ manufacturer: 'Samsung', model: 'Other' })];
  const groups = groupUsage(rows, 'models');
  assert.equal(groups.length, 2);
  assert.equal(groups[0].devices, 3n);
  assert.equal(groups[0].share, 75);
  assert.equal(groupUsage(rows, 'brands')[0].share, 75);
});

test('Android shares identify version and API, including 12L and unknown APIs', () => {
  assert.deepEqual(groupUsage([row({ androidApi: 32 }), row({ androidApi: 37 }), row({ androidApi: 99 })], 'android').map(group => group.label),
    ['Android 12L', 'Android 17', 'Android API 99']);
});

test('empty dates and large counts remain valid', () => {
  assert.deepEqual(groupUsage([], 'versions'), []);
  assert.equal(percentage(0, 0), 0);
  assert.equal(percentage('90071992547409930', '180143985094819860'), 50);
});
