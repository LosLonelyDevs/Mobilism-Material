import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createHandler as pingHandler } from '../api/ping.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { createHandler as cleanupHandler } from '../api/cleanup.js';
import { recordReport } from '../lib/database.js';
import { DAY, utcDay, validateReport } from '../lib/protocol.js';

let db;
const query = async (sql, values) => (await db.query(sql, values)).rows;
const today = () => utcDay();
const dateAgo = days => utcDay(new Date(Date.now() - days * DAY));
const token = 'test-only-token-with-at-least-32-characters';
const report = (values = {}) => ({ schema: 1, day: today(), pingId: randomUUID(), appVersion: '0.1.18',
  versionCode: 19, androidApi: 36, manufacturer: 'Google', model: 'Pixel 9', ...values });

async function call(handler, { method = 'POST', body, headers = {}, url = '/api/ping' } = {}) {
  const res = {
    headers: {}, statusCode: 0,
    setHeader(key, value) { this.headers[key] = value; },
    end(body) { this.body = JSON.parse(body); },
  };
  await handler({ method, body, headers: { 'content-type': 'application/json', ...headers }, url }, res);
  return res;
}

before(async () => {
  db = new PGlite();
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
});
beforeEach(async () => { await db.exec('TRUNCATE usage_receipts, daily_usage'); });
after(async () => { await db.close(); });

test('concurrent duplicate requests count one device and acknowledge every retry', async () => {
  const body = report();
  const responses = await Promise.all(Array.from({ length: 20 }, () => call(pingHandler(query), { body })));
  assert.equal(responses.filter(res => !res.body.duplicate).length, 1);
  assert.ok(responses.every(res => res.statusCode === 200 && res.body.accepted && res.body.pingId === body.pingId));
  assert.equal((await query('SELECT devices::text FROM daily_usage'))[0].devices, '1');
  assert.equal((await query('SELECT count(*)::int AS count FROM usage_receipts'))[0].count, 1);
});

test('different daily IDs count separately, with no cross-day identifier', async () => {
  await recordReport(query, report());
  await recordReport(query, report());
  await recordReport(query, report({ day: dateAgo(1) }));
  assert.deepEqual((await query('SELECT devices::text FROM daily_usage ORDER BY day')).map(row => row.devices), ['1', '2']);
});

test('replayed ID with changed app or device metadata cannot add another count', async () => {
  const body = report();
  await recordReport(query, body);
  assert.equal(await recordReport(query, { ...body, appVersion: '9.9.9', model: 'Different' }), false);
  const rows = await query('SELECT app_version, model, devices::text FROM daily_usage');
  assert.deepEqual(rows, [{ app_version: '0.1.18', model: 'Pixel 9', devices: '1' }]);
});

test('failed aggregation rolls back the receipt so a retry can succeed', async () => {
  const body = report();
  await assert.rejects(recordReport(query, { ...body, model: 'x'.repeat(121) }));
  assert.equal((await query('SELECT count(*)::int AS count FROM usage_receipts'))[0].count, 0);
  assert.equal(await recordReport(query, body), true);
});

test('invalid, oversized, future, expired, and unexpected identity fields never reach storage', async () => {
  const noDatabase = () => { throw new Error('Should not query'); };
  const invalid = [null, [], {}, report({ schema: 2 }), report({ pingId: 'permanent-device-id' }),
    report({ day: '2026-02-30' }), report({ day: dateAgo(-1) }), report({ day: dateAgo(8) }),
    report({ androidApi: 25 }), report({ versionCode: 1.5 }), report({ model: 'x'.repeat(121) }),
    report({ manufacturer: 'test\nheader' }), report({ accountId: '123' })];
  for (const body of invalid) {
    assert.equal((await call(pingHandler(noDatabase), { body })).statusCode, 400);
  }
  assert.equal((await call(pingHandler(noDatabase), { body: 'x'.repeat(2049) })).statusCode, 400);
  assert.equal((await call(pingHandler(noDatabase), { body: '{' })).statusCode, 400);
  assert.equal((await call(pingHandler(noDatabase), { body: report(), headers: { 'content-type': 'text/plain' } })).statusCode, 400);
  assert.equal((await call(pingHandler(noDatabase), { method: 'GET' })).statusCode, 405);
  assert.equal(validateReport(report({ day: dateAgo(7) })), true);
});

test('database failures return retryable responses without revealing credentials', async () => {
  const result = await call(pingHandler(() => { throw new Error('postgres://private-credential'); }), { body: report() });
  assert.equal(result.statusCode, 503);
  assert.deepEqual(result.body, { error: 'Please retry later' });
});

test('statistics require a separate strong token and include daily totals and dimensions', async () => {
  await recordReport(query, report());
  await recordReport(query, report({ model: 'Other phone', appVersion: '0.1.17', versionCode: 18 }));
  const handler = statsHandler(query, () => token);
  assert.equal((await call(handler, { method: 'GET' })).statusCode, 401);
  assert.equal((await call(statsHandler(query, () => ''), { method: 'GET', headers: { authorization: 'Bearer ' } })).statusCode, 401);
  const result = await call(handler, { method: 'GET', url: `/api/stats?from=${dateAgo(1)}&to=${today()}`,
    headers: { authorization: `Bearer ${token}` } });
  assert.equal(result.statusCode, 200);
  assert.equal(result.headers['Cache-Control'], 'no-store');
  assert.deepEqual(result.body.daily, [{ day: dateAgo(1), devices: '0' }, { day: today(), devices: '2' }]);
  assert.equal(result.body.breakdown.length, 2);
  assert.ok(result.body.breakdown.every(row => !('pingId' in row)));
  assert.equal((await call(handler, { method: 'GET', url: '/api/stats?from=bad',
    headers: { authorization: `Bearer ${token}` } })).statusCode, 400);
});

test('retention keeps all still-retryable IDs and prevents counting expired replays', async () => {
  const expired = report({ day: dateAgo(8) });
  await query('INSERT INTO usage_receipts (day, ping_id) VALUES ($1, $2)', [expired.day, expired.pingId]);
  const boundary = report({ day: dateAgo(7) });
  await recordReport(query, boundary);
  await query(`INSERT INTO daily_usage VALUES ($1, 'old', 1, 26, 'Old', 'Phone', 1)`, [dateAgo(365)]);
  const handler = cleanupHandler(query, () => token);
  assert.equal((await call(handler, { method: 'GET' })).statusCode, 401);
  const result = await call(handler, { method: 'GET', headers: { authorization: `Bearer ${token}` } });
  assert.deepEqual(result.body, { receipts: 1, aggregates: 1 });
  assert.equal(await recordReport(query, boundary), false);
  assert.equal((await call(pingHandler(query), { body: expired })).statusCode, 400);
  assert.equal((await query('SELECT devices::text FROM daily_usage'))[0].devices, '1');
});
