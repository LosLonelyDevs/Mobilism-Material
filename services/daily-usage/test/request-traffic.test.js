import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { cleanReports, recordReport } from '../lib/database.js';
import { DAY, readReport, utcDay, validateReport } from '../lib/protocol.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { createHandler as followingHandler } from '../api/following-cache.js';
import { createHandler as pingHandler } from '../api/ping.js';
import { groupRequestTraffic, requestEndpoints, requestMethods, validateRequestTraffic } from '../public/request-traffic.js';

let db;
const query = async (sql, values) => (await db.query(sql, values)).rows;
const dateAgo = days => utcDay(new Date(Date.now() - days * DAY));
const metric = () => ({ endpoint: 'api/search', method: 'POST', counts: [12, 1, 2, 3, 4, 5], durationMs: 2700 });
const bucket = () => ({ day: dateAgo(1), id: randomUUID(), appVersion: '0.1.24', versionCode: 25, metrics: [metric()] });
const report = (traffic = [bucket()]) => ({ schema: 1, day: dateAgo(0), pingId: randomUUID(), appVersion: '0.1.24',
  versionCode: 25, androidApi: 36, manufacturer: 'Google', model: 'Pixel', requestTraffic: traffic });
before(async () => { db = new PGlite(); await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8')); });
beforeEach(async () => { await db.exec('TRUNCATE usage_receipts, daily_usage, request_receipts, daily_requests, daily_cache_usage'); });
after(async () => { await db.close(); });

test('device cache counts survive ingestion for all four cache groups', async () => {
  const names = ['ReleaseFeed', 'Inbox', 'Images', 'ReleaseComments'];
  const caches = Object.fromEntries(names.map(key => [key, { hits: 3, misses: 1 }]));
  const body = report([{ ...bucket(), metrics: [], caches }]);
  assert.equal(validateReport(body), true);
  await recordReport(query, body);
  const rows = await query('SELECT cache, hits, misses FROM daily_cache_usage ORDER BY cache');
  assert.deepEqual(rows.map(row => row.cache), names.sort());
  assert.ok(rows.every(row => Number(row.hits) === 3 && Number(row.misses) === 1));
});

test('cache-only days share a receipt across retries and report weighted totals', async () => {
  const b = { ...bucket(), metrics: [], caches: { FollowingBackground: { hits: 3, misses: 2 } } };
  assert.equal(validateReport(report([b])), true);
  for (const caches of [{}, { privateName: { hits: 1, misses: 0 } }, { FollowingBackground: { hits: -1, misses: 1 } },
    { FollowingManual: { hits: 0, misses: 0 } }, { FollowingManual: { hits: 1, misses: 0, query: 'private' } }]) {
    assert.equal(validateReport(report([{ ...b, caches }])), false);
  }
  const ingest = pingHandler(query);
  await Promise.all([report([b]), report([b])].map(async body => {
    const receipt = { setHeader() {}, end(text) { this.body = JSON.parse(text); } };
    await ingest({ method: 'POST', headers: { 'content-type': 'application/json' }, body }, receipt);
    assert.equal(receipt.statusCode, 200);
    assert.equal(receipt.body.accepted, true);
  }));
  await recordReport(query, report([{ ...b, id: randomUUID(), day: dateAgo(2), caches: { FollowingBackground: { hits: 0, misses: 5 } } }]));
  const handler = statsHandler(query, () => '', () => new Date(), () => true,
    async () => ({ versions: [{ version: '0.1.24', prerelease: false, versionCode: 25 }] }));
  const res = { setHeader() {}, end(body) { this.body = JSON.parse(body); } };
  await handler({ method: 'GET', url: `/api/stats?group=period&from=${dateAgo(2)}&to=${dateAgo(0)}`, headers: {} }, res);
  assert.deepEqual(res.body.cacheUsage, []);
  assert.equal('followRankings' in res.body, false);
  assert.deepEqual(res.body.requestTraffic, []);
  await followingHandler(query, () => true, async () => ({ versions: [{ version: '0.1.24', prerelease: false, versionCode: 25 }] }))(
    { method: 'GET', url: `/api/following-cache?from=${dateAgo(2)}&to=${dateAgo(0)}`, headers: {} }, res);
  assert.deepEqual(res.body.cacheUsage, [{ cache: 'FollowingBackground', hits: '3', misses: '7' }]);
});

test('old clients remain valid and traffic rejects private or unbounded input', async () => {
  const old = report(); delete old.requestTraffic;
  assert.equal(validateReport(old), true);
  assert.equal(validateReport(report()), true);
  const mutations = [
    b => { b.day = dateAgo(0); }, b => { b.day = dateAgo(8); }, b => { b.id = 'persistent-install-id'; },
    b => { b.url = 'https://private.test'; }, b => { b.metrics[0].endpoint = 'api/search?query=private'; },
    b => { b.metrics[0].counts[0] = -1; }, b => { b.metrics[0].counts[0] = 1000001; },
    b => { b.metrics[0].counts = [0, 0, 0, 0, 0, 0]; }, b => { b.metrics[0].durationMs = 27 * 300000 + 1; },
    b => { b.metrics.push(metric()); }, b => { b.metrics[0].username = 'private'; },
  ];
  for (const mutate of mutations) { const b = bucket(); mutate(b); assert.equal(validateRequestTraffic([b], dateAgo(0)), false); }
  const b = bucket(); assert.equal(validateRequestTraffic([b, b], dateAgo(0)), false);
  assert.equal(validateRequestTraffic([b, { ...b, id: b.id.toUpperCase() }], dateAgo(0)), false);
  assert.equal(validateRequestTraffic(Array.from({ length: 9 }, bucket), dateAgo(0)), false);
  await assert.rejects(readReport({ headers: {}, body: Buffer.alloc(262145, 32) }));
  const source = await readFile(new URL('../../../core/data/src/main/java/org/mobilism/material/data/RequestTraffic.kt', import.meta.url), 'utf8');
  for (const field of [...requestEndpoints, ...requestMethods]) assert.ok(source.includes(`"${field}"`), field);
});

test('traffic IDs deduplicate across concurrent retries and different daily reports', async () => {
  const b = bucket(), body = report([b]);
  await Promise.all(Array.from({ length: 8 }, () => recordReport(query, body)));
  await recordReport(query, report([b]));
  const rows = await query('SELECT successes::text, rate_limited::text, network_errors::text, duration_ms::text FROM daily_requests');
  assert.deepEqual(rows, [{ successes: '12', rate_limited: '3', network_errors: '5', duration_ms: '2700' }]);
  assert.equal((await query('SELECT devices::text FROM daily_usage'))[0].devices, '2');
  assert.equal((await query('SELECT count(*)::int AS n FROM request_receipts'))[0].n, 1);
  await recordReport(query, report([{ ...b, id: randomUUID() }]));
  assert.equal((await query('SELECT successes::text FROM daily_requests'))[0].successes, '24');
});

test('failed aggregation rolls back usage and traffic receipts together', async () => {
  const b = bucket(); b.metrics[0].durationMs = -1;
  await assert.rejects(recordReport(query, report([b])));
  assert.equal((await query('SELECT count(*)::int AS n FROM request_receipts'))[0].n, 0);
  assert.equal((await query('SELECT count(*)::int AS n FROM usage_receipts'))[0].n, 0);
});

test('traffic receipts cover seven-day payloads plus seven-day delivery retry', async () => {
  const b = { ...bucket(), day: dateAgo(14) };
  const body = { ...report([b]), day: dateAgo(7) };
  assert.equal(validateReport(body), true);
  await recordReport(query, body);
  await cleanReports(query);
  await recordReport(query, body);
  assert.equal((await query('SELECT successes::text FROM daily_requests'))[0].successes, '12');
  await query('INSERT INTO request_receipts VALUES ($1, $2)', [dateAgo(15), randomUUID()]);
  await cleanReports(query);
  assert.deepEqual(await query('SELECT day::text FROM request_receipts'), [{ day: dateAgo(14) }]);
  await recordReport(query, { ...report([{ ...b, day: dateAgo(15), id: randomUUID() }]), day: dateAgo(8) });
  assert.equal((await query('SELECT count(*)::int AS n FROM daily_requests'))[0].n, 1);
});

test('traffic stats stay hidden before the supported stable release and group by selected day', async () => {
  await recordReport(query, report());
  const run = async versions => {
    const handler = statsHandler(query, () => '', () => new Date(), () => true, async () => ({ versions, comparisonsAvailable: true }));
    const res = { setHeader() {}, end(body) { this.body = JSON.parse(body); } };
    await handler({ method: 'GET', url: `/api/stats?from=${dateAgo(2)}&to=${dateAgo(0)}`, headers: {} }, res);
    assert.equal(res.statusCode, 200);
    return res.body;
  };
  for (const version of [{ version: '0.1.23', versionCode: 24, prerelease: false }, { version: '0.1.24', versionCode: 25, prerelease: true }]) {
    const result = await run([version]); assert.equal(result.trafficAvailable, false); assert.deepEqual(result.requestTraffic, []);
  }
  const result = await run([{ version: '0.1.24', versionCode: 25, prerelease: false }]);
  assert.equal(result.trafficAvailable, true);
  assert.equal(result.requestTraffic.length, 1);
  const grouped = groupRequestTraffic([...result.requestTraffic, ...result.requestTraffic], dateAgo(1));
  assert.equal(grouped[0].requests, 54n);
  assert.equal(grouped[0].counts[3], 6n);
  assert.equal(grouped[0].durationMs, 5400n);
  assert.deepEqual(groupRequestTraffic(result.requestTraffic, dateAgo(0)), []);
});

test('period stats combine repeated dimensions and endpoints while retaining zero-filled daily totals', async () => {
  await recordReport(query, report([bucket(), { ...bucket(), day: dateAgo(2) }]));
  await recordReport(query, { ...report([]), day: dateAgo(2) });
  await recordReport(query, { ...report([]), day: dateAgo(3) });
  const versions = [{ version: '0.1.24', versionCode: 25, prerelease: false }];
  const handler = statsHandler(query, () => '', () => new Date(), () => true, async () => ({ versions }));
  const res = { setHeader() {}, end(body) { this.body = JSON.parse(body); } };
  await handler({ method: 'GET', url: `/api/stats?from=${dateAgo(2)}&to=${dateAgo(0)}&group=period`, headers: {} }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.latestStable, '0.1.24');
  assert.deepEqual(res.body.daily.map(row => row.devices), ['1', '0', '1']);
  assert.equal(res.body.breakdown.length, 1);
  assert.equal(res.body.breakdown[0].devices, '2');
  assert.deepEqual(res.body.requestTraffic, [{ endpoint: 'api/search', method: 'POST', counts: ['24', '2', '4', '6', '8', '10'], durationMs: '5400' }]);
});
