import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { createHandler } from '../api/latest-apps.js';
import { createHandler as followingHandler } from '../api/following-cache.js';
import { randomUUID } from 'node:crypto';

const now = 1900000000;
let db;
before(async () => {
  db = new PGlite();
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
});
beforeEach(async () => { await db.exec("UPDATE latest_apps_cache SET items = '[]', observed_at = 0; TRUNCATE daily_followed_apps, follow_lookup_receipts"); });
after(async () => { await db.close(); });
const query = async (sql, values) => (await db.query(sql, values)).rows;
const item = (id, name = 'App ' + id) => ({
  id, name, title: name + ' v1.1', version: '1.1', postedAt: now - 2000 + id, author: 'Uploader'
});
async function call(method, body, database = query, time = now) {
  const res = { headers: {}, setHeader(key, value) { this.headers[key] = value; },
    end(value) { this.body = JSON.parse(value); } };
  await createHandler(database, () => time)({ method, headers: { 'content-type': 'application/json' }, body }, res);
  return res;
}
const post = (items, firstPage = false, adultFiltered = false) => call('POST', { firstPage, adultFiltered, items });
const day = new Date(now * 1000).toISOString().slice(0, 10);
const named = (apps, adultFiltered = false, database = query, time = now) =>
  call('POST', { apps, adultFiltered, day }, database, time);
const feed = (ids, baseline, at, filtered = false, items = ids.map(id => item(id)), arrival = at) =>
  call('POST', { ids, baseline, readAt: at, items, firstPage: true, adultFiltered: filtered }, query, arrival);

test('one lookup counts each requesting device once per app and only absent visible rows as misses', async () => {
  const apps = ['APP 1', 'App 2', 'Absent'].map(name => ({ name, receipt: randomUUID() }));
  await post([item(1)], true, true);
  await post([item(2)], true, false);
  for (let n = 0; n < 2; n++) {
    const reply = await named(apps, true);
    assert.equal(reply.statusCode, 200);
    assert.equal(reply.headers['Cache-Control'], 'no-store');
    assert.deepEqual(reply.body.items, [{ ...item(1), adultFiltered: true }]);
    assert.equal(reply.body.misses, 1);
  }
  await named(apps.map(app => ({ ...app, receipt: randomUUID() })));
  const counts = () => query('SELECT app_key, followers::int, misses::int FROM daily_followed_apps ORDER BY app_key');
  assert.deepEqual(await counts(), [
    { app_key: 'absent', followers: 2, misses: 3 }, { app_key: 'app 1', followers: 2, misses: 0 },
    { app_key: 'app 2', followers: 2, misses: 0 },
  ]);
  const before = await counts();
  assert.equal((await named(apps, false, query, now + 21601)).statusCode, 200);
  assert.deepEqual(await counts(), before);
  const noMetrics = async (sql, params) => {
    if (sql.includes('follow_lookup_receipts')) throw new Error('Statistics unavailable');
    return query(sql, params);
  };
  assert.equal((await named(apps, false, noMetrics)).body.items.length, 2);
  assert.equal((await named(apps, false, async () => { throw new Error(); })).statusCode, 503);
  assert.deepEqual(await counts(), before);
  const res = { setHeader() {}, end(value) { this.body = JSON.parse(value); } };
  await followingHandler(query, () => true,
    async () => ({ versions: [{ version: '0.1.24', versionCode: 25, prerelease: false }] }), () => new Date(now * 1000))(
    { method: 'GET', url: '/api/following-cache?from=' + day + '&to=' + day, headers: {} }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.cachedApps, 2);
  assert.deepEqual(res.body.lookups, { hits: '4', misses: '3', measuredMisses: '3' });
  assert.equal(res.body.followRankings.find(row => row.name === 'APP 1').cachedVersion, '1.1');
  assert.equal(res.body.followRankings.find(row => row.name === 'Absent').cachedVersion, null);
  assert.equal(res.body.followRankings.find(row => row.missRank === 1).name, 'Absent');
  assert.ok(res.body.followRankings.every(row => row.followers === '2'));
  assert.ok(res.body.followRankings.every(row => row.totalLookups === '4'));
  for (const invalid of [[], [...apps, { name: '  app 1  ', receipt: randomUUID() }], [{...apps[0], username: 'extra'}],
    [{ name: 'Test', receipt: 'invalid' }]]) assert.equal((await named(invalid)).statusCode, 400);
});

test('each ranking includes its own top 20 apps without truncating lookup totals', async () => {
  await query(`INSERT INTO daily_followed_apps (day, app_key, name, followers, misses)
    SELECT $1::date, 'app' || n, 'App ' || n, n, 42 - n FROM generate_series(1,41) AS n`, [day]);
  const res = { setHeader() {}, end(value) { this.body = JSON.parse(value); } };
  await followingHandler(query, () => true,
    async () => ({ versions: [{ version: '0.1.24', versionCode: 25, prerelease: false }] }), () => new Date(now * 1000))(
    { method: 'GET', url: '/api/following-cache', headers: {} }, res);
  assert.equal(res.statusCode, 200);
  const rows = res.body.followRankings;
  assert.equal(rows.length, 40);
  assert.equal(rows.find(row => row.followRank === 20).name, 'App 22');
  assert.equal(rows.find(row => row.missRank === 20).name, 'App 20');
  assert.ok(!rows.some(row => row.name === 'App 21'));
  assert.equal(res.body.lookups.misses, '861');
});

test('a search contribution fills a miss and dates include the current partial UTC day', async () => {
  const apps = [{ name: 'Facebook', receipt: randomUUID() }];
  await post([item(1)], true);
  assert.equal((await named(apps)).body.misses, 1);
  const upload = data => call('POST', { items: [data], ids: [data.id], readAt: now, firstPage: false, adultFiltered: true });
  await upload(item(2, 'Facebook'));
  await upload({ ...item(3, 'Facebook'), title: 'Facebook v2.0', version: '2.0' });
  await upload(item(2, 'Facebook'));
  const next = await named(apps, true);
  assert.equal(next.body.misses, 0);
  assert.deepEqual(next.body.items.map(item => item.version), ['2.0']);
  const yesterday = new Date((now - 86400) * 1000).toISOString().slice(0, 10);
  await query('INSERT INTO daily_followed_apps(day, app_key, name, followers, misses) VALUES ($1,$2,$2,1,5)', [yesterday, 'older']);
  const report = async (from, to, at = now) => {
    const res = { setHeader() {}, end(value) { this.body = JSON.parse(value); } };
    await followingHandler(query, () => true, async () => ({ versions: [{ version: '0.1.25', versionCode: 26, prerelease: false }] }),
      () => new Date(at * 1000))({ method: 'GET', url: '/api/following-cache?from=' + from + '&to=' + to }, res);
    assert.equal(res.statusCode, 200);
    return res.body;
  };
  assert.deepEqual((await report(day, day)).lookups, { hits: '1', misses: '1', measuredMisses: '1' });
  const range = await report(yesterday, day);
  assert.deepEqual(range.lookups, { hits: '1', misses: '6', measuredMisses: '1' });
  assert.equal(range.cachedApps, 2);
  assert.equal(range.followRankings.find(r => r.name === 'Facebook').cachedVersion, '2.0');
  assert.equal(range.followRankings.find(r => r.name === 'Facebook').totalLookups, '2');
  assert.equal(range.followRankings.find(r => r.name === 'older').totalLookups, '5');
  await query("UPDATE latest_apps_cache SET items = jsonb_set(items, '{0,hiddenAt}', to_jsonb($1::bigint))", [now]);
  assert.equal((await report(yesterday, yesterday)).cachedApps, 1);
  assert.equal((await report(yesterday, day, now + 86400)).cachedApps, 0);
});

test('two comparable gaps ten minutes apart hide a topic until a newer sighting', async () => {
  const original = [105, 104, 103, 102, 101], gap = [105, 104, 102, 101];
  const visible = async at => (await call('GET', null, query, at)).body.items.some(app => app.id === 103);
  assert.equal((await feed(original, [], now)).statusCode, 200);
  await feed(gap, original, now + 10, true);
  await feed(gap, original, now + 630);
  await feed(gap, original, now + 1229);
  assert.equal(await visible(now + 1229), true);
  await feed(gap, original, now + 1230);
  assert.equal(await visible(now + 1230), false);
  await feed(original, [], now + 1210, false, [], now + 1240);
  assert.equal(await visible(now + 1240), false);
  await feed(original, [], now + 1250, false, []);
  assert.equal(await visible(now + 1250), true);
  await feed(gap, original, now + 1260);
  await feed(original, [], now + 1300, false, []);
  await feed(gap, original, now + 1860);
  assert.equal(await visible(now + 1860), true);
});

test('a newly confirmed name can be served while the Latest feed is stale', async () => {
  const apps = ['Reader', 'Missing'].map(name => ({ name, receipt: randomUUID() }));
  await post([item(1)], true);
  const later = now + 21601;
  const reader = item(2, 'Reader');
  await call('POST', { firstPage: false, adultFiltered: true, items: [reader], readAt: later }, query, later);
  const result = await named(apps, true, query, later);
  assert.equal(result.body.observedAt, later);
  assert.deepEqual(result.body.items.map(item => item.name), ['Reader']);
  assert.equal(result.body.misses, 0);
  assert.equal((await call('GET', null, query, later)).body.observedAt, now);
  assert.deepEqual(await query('SELECT sum(hits)::int AS hits, sum(misses)::int AS misses FROM daily_followed_apps'), [{ hits: 1, misses: 0 }]);
  assert.deepEqual((await named(apps, true, query, later + 21601)).body.items, []);
});

test('paging past a topic cannot evict it, and other fresh topics do not extend its 24 hours', async () => {
  const original = [105, 104, 103, 102, 101], newer = [110, 109, 108, 107, 106];
  await feed(original, [], now);
  await feed(newer, original, now + 20);
  await feed(newer, original, now + 800);
  await feed(newer, [], now + 86395);
  const before = await call('GET', null, query, now + 86399);
  assert.equal(before.body.items.length, 10);
  assert.match(before.headers['Cache-Control'], /max-age=1$/);
  assert.equal((await call('GET', null, query, now + 86400)).body.items.length, 5);
});

test('concurrent overlapping pages keep the newest 500 unique apps in post order', async () => {
  const pages = Array.from({ length: 26 }, (_, page) => Array.from({ length: 20 }, (_, i) => item(page * 20 + i + 1)));
  const uploads = await Promise.all(pages.reverse().map((items, index) => post(items, index === 0)));
  assert.ok(uploads.every(res => res.statusCode === 200));
  assert.equal((await post([item(800, 'APP 520')])).statusCode, 200);
  assert.equal((await post([item(520), item(1)])).statusCode, 200);
  const result = await call('GET');
  assert.equal(result.body.items.length, 500);
  assert.deepEqual(result.body.items[0], { ...item(800, 'APP 520'), adultFiltered: false });
  assert.equal(result.body.items.at(-1).id, 21);
  assert.equal(result.body.observedAt, now);
  assert.match(result.headers['Vercel-CDN-Cache-Control'], /s-maxage/);
  await post([item(800, 'APP 520')], false, true);
  await post([item(800, 'APP 520')]);
  assert.equal((await call('GET')).body.items[0].adultFiltered, true);
});

test('migration widens an existing 200-app cache without losing its entries', async () => {
  await post([item(1)], true);
  await db.exec(`ALTER TABLE latest_apps_cache DROP CONSTRAINT latest_apps_cache_items_check,
    ADD CONSTRAINT latest_apps_cache_items_check CHECK (jsonb_array_length(items) <= 200)`);
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
  const { body } = await call('GET');
  assert.equal(body.items[0].id, 1);
  assert.equal(body.observedAt, now);
  const fill = count => query("UPDATE latest_apps_cache SET items = (SELECT jsonb_agg('{}'::jsonb) FROM generate_series(1, $1::int))", [count]);
  await fill(500);
  await assert.rejects(fill(501), /latest_apps_cache_items_check/);
});

test('later pages alone cannot claim freshness and invalid reports cannot alter the index', async () => {
  assert.equal((await post([item(1)])).statusCode, 200);
  const before = (await call('GET')).body;
  assert.equal(before.observedAt, 0);
  for (const invalid of [{ ...item(2), author: '' }, { ...item(2), url: 'https://example.org' },
    { ...item(2), postedAt: now + 86400 }, { ...item(2), id: -1 }]) {
    assert.equal((await post([invalid], true)).statusCode, 400);
  }
  assert.deepEqual((await call('GET')).body, before);
  assert.equal((await call('GET', null, async () => { throw new Error('offline'); })).statusCode, 503);
});
