import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { createHandler as publicHandler } from '../api/app-config.js';
import { createHandler as adminHandler } from '../api/control-config.js';
import { defaultConfig, validateConfig, limitFields } from '../public/config-schema.js';

let db;
const query = async (sql, values) => (await db.query(sql, values)).rows;
const notice = (values = {}) => ({ id: 'test-notice', title: 'Update available', message: 'Install the latest build.',
  enabled: true, mode: 'notice', frequency: 'once', startsAt: null, endsAt: null,
  minVersionCode: 1, maxVersionCode: null, requiredVersionCode: null, linkText: null, linkUrl: null,
  audience: { type: 'everyone' }, ...values });
async function call(handler, { method = 'GET', body, headers = {} } = {}) {
  const res = { headers: {}, setHeader(key, value) { this.headers[key] = value; }, end(body) { this.body = JSON.parse(body); } };
  await handler({ method, body, headers: { host: 'controls.test', origin: 'https://controls.test',
    'x-forwarded-proto': 'https', 'content-type': 'application/json', ...headers } }, res);
  return res;
}
before(async () => { db = new PGlite(); await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8')); });
beforeEach(async () => { await db.exec('TRUNCATE app_control'); });
after(async () => { await db.close(); });

test('empty database returns no overrides and no notices publicly', async () => {
  const result = await call(publicHandler(query));
  assert.equal(result.statusCode, 200);
  assert.deepEqual(result.body, defaultConfig());
  assert.equal(result.headers['Cache-Control'], 'no-store');
});
test('legacy stored configuration migrates but old editors cannot erase targeting', async () => {
  const legacy = notice(); delete legacy.audience;
  await query('INSERT INTO app_control VALUES (1, 1, $1::jsonb, CURRENT_TIMESTAMP)',
    [JSON.stringify({ schema: 1, revision: 1, limits: {}, notices: [legacy] })]);
  const read = await call(adminHandler(query, () => true));
  assert.equal(read.body.schema, 2);
  assert.deepEqual(read.body.notices[0].audience, { type: 'everyone' });
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body: { ...read.body, schema: 1 } })).statusCode, 400);
});
test('admin username lists stay private while the public endpoint serves matching hashes', async () => {
  const body = { ...defaultConfig(), notices: [notice({ audience: { type: 'all', conditions: [
    { type: 'rule', field: 'username', operator: 'in', values: ['Alice', 'BOB'] },
    { type: 'rule', field: 'versionName', operator: 'in', values: ['0.1.20'] },
  ] } })] };
  const saved = await call(adminHandler(query, () => true), { method: 'PUT', body });
  assert.equal(saved.statusCode, 200);
  assert.deepEqual(saved.body.notices[0].audience.conditions[0].values, ['alice', 'bob']);
  const published = await call(publicHandler(query));
  assert.equal(published.body.schema, 2);
  assert.equal(published.body.notices[0].audience.conditions[0].field, 'usernameHash');
  assert.ok(published.body.notices[0].audience.conditions[0].values.every(value => /^[a-f0-9]{64}$/.test(value)));
  assert.ok(!JSON.stringify(published.body).includes('alice'));
  assert.ok(!JSON.stringify(published.body).includes('bob'));
});
test('only an authenticated same-origin admin can publish', async () => {
  const body = defaultConfig();
  assert.equal((await call(adminHandler(query, () => false), { method: 'PUT', body })).statusCode, 401);
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body, headers: { origin: 'https://evil.test' } })).statusCode, 403);
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body, headers: { origin: '' } })).statusCode, 403);
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body, headers: { 'content-type': 'text/plain' } })).statusCode, 415);
  assert.equal((await call(publicHandler(query), { method: 'PUT', body })).statusCode, 405);
});
test('publish, read, revise, disable and reset overrides round-trip through Postgres', async () => {
  const handler = adminHandler(query, () => true);
  const config = { ...defaultConfig(), limits: { searchSpacingMs: 10000 }, notices: [notice({ mode: 'required', requiredVersionCode: 30,
    startsAt: '2026-11-16T20:00:00.000Z', endsAt: '2026-12-01T00:00:00.000Z', linkText: 'Details', linkUrl: 'https://example.org/update' })] };
  const saved = await call(handler, { method: 'PUT', body: config });
  assert.equal(saved.statusCode, 200); assert.equal(saved.body.revision, 1);
  assert.deepEqual((await call(publicHandler(query))).body, saved.body);
  const next = await call(handler, { method: 'PUT', body: { ...saved.body, limits: {}, notices: [notice({ enabled: false })] } });
  assert.equal(next.statusCode, 200); assert.equal(next.body.revision, 2);
  assert.deepEqual((await call(handler)).body, next.body);
});
test('concurrent and stale writes cannot overwrite an admin edit', async () => {
  const handler = adminHandler(query, () => true);
  const initial = await Promise.all([call(handler, { method: 'PUT', body: defaultConfig() }), call(handler, { method: 'PUT', body: defaultConfig() })]);
  assert.deepEqual(initial.map(r => r.statusCode).sort(), [200, 409]);
  const config = initial.find(r => r.statusCode === 200).body;
  const edits = await Promise.all([call(handler, { method: 'PUT', body: config }), call(handler, { method: 'PUT', body: config })]);
  assert.deepEqual(edits.map(r => r.statusCode).sort(), [200, 409]);
  assert.equal((await call(handler, { method: 'PUT', body: defaultConfig() })).statusCode, 409);
});
test('all API overrides reject invalid bounds and accept shipped defaults', () => {
  for (const [key, , fallback, min, max] of limitFields) {
    assert.equal(validateConfig({ ...defaultConfig(), limits: { [key]: fallback } }).limits[key], fallback);
    for (const bad of [min - 1, max + 1, 1.5, '3000', null]) {
      assert.throws(() => validateConfig({ ...defaultConfig(), limits: { [key]: bad } }));
    }
  }
});
test('upstream policy thresholds round-trip through the protected editor and public endpoint', async () => {
  const limits = Object.fromEntries(limitFields.map(([key, , fallback]) => [key, fallback]));
  Object.assign(limits, { readSpacingMs: 2000, readConcurrency: 1, messagePollIntervalMs: 180000,
    memberLookupSpacingMs: 5000, debridRefreshIntervalMs: 60000 });
  const saved = await call(adminHandler(query, () => true), { method: 'PUT', body: { ...defaultConfig(), limits } });
  assert.equal(saved.statusCode, 200);
  assert.deepEqual((await call(publicHandler(query))).body.limits, limits);
});
test('notices reject unsafe links, broken targeting, schedules, and missing update targets', () => {
  for (const bad of [
    { mode: 'required' }, { mode: 'required', requiredVersionCode: 1 },
    { linkUrl: 'javascript:alert(1)', linkText: 'Read' }, { linkUrl: 'http://example.org', linkText: 'Read' },
    { linkUrl: 'https://user:password@example.org', linkText: 'Read' }, { linkUrl: 'https://example.org' },
    { maxVersionCode: 0 }, { minVersionCode: 20, maxVersionCode: 10 },
    { startsAt: '2026-11-20T00:00:00.000Z', endsAt: '2026-11-19T00:00:00.000Z' },
    { startsAt: 'invalid' }, { message: '' }, { frequency: 'sometimes' }, { unexpected: 'field' },
  ]) assert.throws(() => validateConfig({ ...defaultConfig(), notices: [notice(bad)] }));
  assert.throws(() => validateConfig({ ...defaultConfig(), notices: [notice(), notice()] }));
});
test('database and malformed request errors never expose private details', async () => {
  const failing = () => { throw new Error('private connection secret'); };
  const publicResult = await call(publicHandler(failing));
  assert.equal(publicResult.statusCode, 503); assert.ok(!JSON.stringify(publicResult.body).includes('secret'));
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body: '{' })).statusCode, 400);
  assert.equal((await call(adminHandler(query, () => true), { method: 'PUT', body: 'x'.repeat(65537) })).statusCode, 413);
});
