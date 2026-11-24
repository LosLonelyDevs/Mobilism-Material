import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { catalogCapacity, createHandler } from '../api/latest-apps.js';
import { latestFeedSize, visibleSnapshot } from '../lib/follow-lookup.js';
import { createHandler as followingHandler } from '../api/following-cache.js';
import { randomUUID } from 'node:crypto';

const now = 1900000000;
let db;
before(async () => {
  db = new PGlite();
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
});
beforeEach(async () => { await db.exec("UPDATE latest_apps_cache SET items = '[]', category_top = '[]', observed_at = 0; TRUNCATE daily_followed_apps, follow_lookup_receipts"); });
after(async () => { await db.close(); });
const query = async (sql, values) => (await db.query(sql, values)).rows;
const item = (id, name = 'App ' + id) => ({
  id, name, title: name + ' v1.1', version: '1.1', postedAt: now - 2000 + id, author: 'Uploader'
});
async function call(method, body, database = query, time = now, headers = {}, url = '/') {
  const res = { headers: {}, setHeader(key, value) { this.headers[key] = value; },
    end(value) { this.body = JSON.parse(value); } };
  await createHandler(database, () => time)({ method, headers: { 'content-type': 'application/json', ...headers }, body, url }, res);
  return res;
}
const post = (items, firstPage = false, adultFiltered = false) => call('POST', { firstPage, adultFiltered, items });
const day = new Date(now * 1000).toISOString().slice(0, 10);
const named = (apps, adultFiltered = false, database = query, time = now) =>
  call('POST', { apps, adultFiltered, day }, database, time);
const feed = (ids, baseline, at, filtered = false, items = ids.map(id => item(id)), arrival = at) =>
  call('POST', { ids, baseline, readAt: at, items, firstPage: true, adultFiltered: filtered }, query, arrival);

test('category metadata fills older rows, survives legacy uploads, and belongs to the release topic', async () => {
  const original = item(9, 'MyPGallery'), adult = { id: 12, name: 'Adult' };
  const upload = (release, at = now) => call('POST', { firstPage: true, adultFiltered: false,
    items: [release], readAt: at }, query, at);
  await upload(original);
  await Promise.all([upload({ ...original, category: adult }), upload(original)]);
  await upload({ ...original, category: { id: 12 } }, now + 1);
  assert.deepEqual((await call('GET')).body.items[0].category, adult);
  assert.deepEqual((await named([{ name: original.name, receipt: randomUUID() }])).body.items[0].category, adult);
  await upload({ ...original, category: { id: 21 } }, now + 3);
  await upload({ ...original, category: adult }, now + 2);
  assert.deepEqual((await call('GET')).body.items[0].category, { id: 21 });
  const next = { ...original, id: 10, postedAt: original.postedAt + 1 };
  await upload(next);
  await upload({ ...original, category: adult });
  assert.equal((await call('GET')).body.items[0].category, undefined);
  for (const category of [null, { id: -1 }, { id: 12, name: ' ' }, { id: 2, custom: 'private' }])
    assert.equal((await upload({ ...next, category })).statusCode, 400);
});

test('a cache read can carry an observed release without changing the Latest feed timestamp', async () => {
  const older = item(1, 'Camera'), newer = { ...item(2, 'Camera'), version: '2', title: 'Camera v2', category: { id: 7, name: 'Photography' } };
  await post([older], true);
  const apps = [{ name: 'Camera', receipt: randomUUID() }];
  const observations = { items: [newer], readAt: now - 600, adultFiltered: false };
  const request = extra => call('POST', { apps, adultFiltered: false, day, observations: extra });
  const reply = await request(observations);
  assert.equal(reply.headers['X-App-Observations-Accepted'], '1');
  assert.equal(reply.body.items[0].id, 2);
  assert.deepEqual(reply.body.items[0].category, newer.category);
  await request({ ...observations, items: [older] });
  for (const bad of [{ ...observations, items: [{ url: 'private' }] }, { ...observations, readAt: now - 21601 },
    { ...observations, items: Array(11).fill(newer) }]) {
    const ignored = await request(bad);
    assert.equal(ignored.statusCode, 200);
    assert.equal(ignored.headers['X-App-Observations-Accepted'], undefined);
    assert.equal(ignored.body.items[0].id, 2);
  }
  const failure = await call('POST', { apps, adultFiltered: false, day, observations }, async (sql, values) => {
    if (sql.includes('UPDATE latest_apps_cache SET items')) throw new Error('optional write unavailable');
    return query(sql, values);
  });
  assert.equal(failure.statusCode, 200);
  assert.equal(failure.body.items[0].id, 2);
  assert.equal((await call('GET')).body.observedAt, now);
  assert.equal((await named(apps, true)).body.items.length, 0);
});

test('Installed shares current versions without receiving device packages or counting lookups', async () => {
  const camera = { ...item(11, 'Camera'), packageName: 'example.camera', category: { id: 7 } };
  await post([camera], true, true);
  await post([item(11, 'Camera'), item(13, 'Another')]);
  const get = safe => call('GET', null, query, now, {}, '/?view=installed&safe=' + safe);
  assert.deepEqual((await get('1')).body, { observedAt: now, collection: 'installed',
    items: [{ id: 11, name: 'Camera', version: '1.1', packageName: 'example.camera', adultFiltered: true }] });
  assert.equal((await get('0')).body.items.length, 2);
  assert.equal((await get('x')).statusCode, 400);
  assert.equal((await post([{ ...camera, packageName: 'invalid package' }])).statusCode, 400);
  assert.equal((await query('SELECT * FROM daily_followed_apps')).length, 0);
});

test('Discover ranks unique cached apps by recent follow demand and only returns safe sightings when requested', async () => {
  const apps = Array.from({ length: 5 }, (_, index) => ({ ...item(index + 1), category: { id: 20, name: 'Utilities' } }));
  await post(apps.slice(0, 4), true, true);
  await post(apps.slice(4));
  for (const [index, followers] of [5, 1, 0, 2, 8].entries())
    await query('INSERT INTO daily_followed_apps(day, app_key, name, followers, misses, hits) VALUES ($1,$2,$3,$4,0,$5)',
      [day, apps[index].name.toLowerCase(), apps[index].name, followers, index === 2 ? 1000 : 1]);
  const discover = (safe, time = now) => call('GET', null, query, time, {}, '/?view=discover&safe=' + safe);
  assert.equal((await discover('1')).body.collection, 'discover');
  assert.deepEqual((await discover('1')).body.items.map(r => r.id), [1, 4, 2]);
  assert.deepEqual((await discover('0')).body.items.map(r => r.id), [5, 1, 4, 2]);
  assert.deepEqual((await discover('0', now + 2 * 86400)).body.items, []);
  assert.equal((await discover('unknown')).statusCode, 400);
  assert.equal(Number((await query('SELECT sum(hits) AS hits FROM daily_followed_apps'))[0].hits), 1004);
});

test('category Top backfills Home, prefers a newer known post, and stays outside Following', async () => {
  const category = { id: 1332, name: 'Music & Audio' };
  const chart = [1, 2, 3, 4].map(id => ({ ...item(id), key: 'app ' + id, lastSeen: now, adultFiltered: true,
    category, topicViews: 1000 - id, artworkId: 'cover' + id }));
  await query('UPDATE latest_apps_cache SET category_top = $1::jsonb WHERE singleton', [JSON.stringify(chart)]);
  await post([{ ...item(15, 'App 2'), version: '2' }], true, true);
  await post([item(17, 'App 3')], false, false);
  await post([{ ...item(18, 'App 4'), category: { id: 7 } }], false, true);
  const path = '/?view=discover&safe=1';
  const rows = (await call('GET', null, query, now, {}, path)).body.items;
  assert.deepEqual(rows.map(r => [r.id, r.artworkId]), [[1, 'cover1'], [15, 'cover2']]);
  assert.equal(rows[1].version, '2');
  assert.equal((await named([{ name: 'App 1', receipt: randomUUID() }])).body.items.length, 0);
  assert.deepEqual((await call('GET', null, query, now + 2 * 86400, {}, path)).body.items, []);
});

test('ordinary view counts can rank a category and only public artwork IDs are shared', async () => {
  const release = { ...item(4), category: { id: 1332, name: 'Music & Audio' }, topicViews: 54, artworkId: 'ab_1' };
  await post([item(4)], true, true);
  await feed([4], undefined, now, true, [release]);
  await post([item(4)]);
  const rows = (await call('GET', null, query, now, {}, '/?view=discover&safe=1')).body.items;
  assert.equal(rows[0].artworkId, 'ab_1');
  assert.equal((await call('GET')).body.items[0].artworkId, undefined);
  assert.equal((await post([{ ...release, artworkId: 'https://private.invalid/image' }])).statusCode, 400);
  assert.equal((await post([{ ...release, topicViews: -1 }])).statusCode, 400);
});

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
  const category = { id: 7, name: 'Photography' };
  await post([{ ...item(1), category }], true, true);
  const res = { setHeader() {}, end(value) { this.body = JSON.parse(value); } };
  await followingHandler(query, () => true,
    async () => ({ versions: [{ version: '0.1.24', versionCode: 25, prerelease: false }] }), () => new Date(now * 1000))(
    { method: 'GET', url: '/api/following-cache?from=' + day + '&to=' + day, headers: {} }, res);
  assert.equal(res.statusCode, 200);
  assert.equal(res.body.cachedApps, 2);
  assert.deepEqual(res.body.lookups, { hits: '4', misses: '3', measuredMisses: '3' });
  assert.equal(res.body.followRankings.find(row => row.name === 'APP 1').cachedVersion, '1.1');
  assert.deepEqual(res.body.followRankings.find(row => row.name === 'APP 1').category, category);
  assert.deepEqual(res.body.recentApps.find(row => row.name === 'APP 1').category, category);
  assert.deepEqual(res.body.catalog.find(row => row.name === 'App 1').category, category);
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
  assert.deepEqual(res.body.recentApps, []);
  const batch = Array.from({ length: 17 }, (_, i) => ({ name: 'App ' + (i + 1), receipt: randomUUID() }));
  await named(batch);
  await named([batch[16]], false, query, now + 20);
  await named([batch[16]], false, query, now + 10);
  await followingHandler(query, () => true,
    async () => ({ versions: [{ version: '0.1.24', versionCode: 25, prerelease: false }] }), () => new Date((now + 20) * 1000))(
    { method: 'GET', url: '/api/following-cache', headers: {} }, res);
  assert.equal(res.body.recentApps.length, 15);
  assert.equal(new Set(res.body.recentApps.map(row => row.name)).size, 15);
  assert.deepEqual(res.body.recentApps[0], { name: 'App 17', requestedAt: now + 20, cachedVersion: null, clientVersion: null });
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
  assert.deepEqual(range.todayLookups, { hits: '1', misses: '1', measuredMisses: '1',
    backgroundHits: '0', backgroundMisses: '0', manualHits: '0', manualMisses: '0' });
  const names = { apps: [...apps, { name: 'Missing', receipt: randomUUID() }], adultFiltered: true, day };
  for (const mode of ['background', 'manual', 'unknown']) {
    assert.equal((await call('POST', names, query, now, { 'x-following-check': mode })).statusCode, 200);
  }
  assert.deepEqual((await report(yesterday, day)).todayLookups, { hits: '4', misses: '4', measuredMisses: '4',
    backgroundHits: '1', backgroundMisses: '1', manualHits: '1', manualMisses: '1' });
  assert.equal((await report(yesterday, yesterday)).todayLookups, null);
  assert.equal(range.cachedApps, 2);
  assert.equal(range.followRankings.find(r => r.name === 'Facebook').cachedVersion, '2.0');
  assert.deepEqual(range.catalog, [{ name: 'Facebook', version: '2.0', clientVersion: null }, { name: 'App 1', version: '1.1', clientVersion: null }]);
  const clientHeaders = version => ({ 'x-following-check': 'manual',
    'user-agent': `Example prefix MobilismMaterial/${version} (Android; build 27)` });
  await call('POST', names, query, now + 25, clientHeaders('0.1.26'));
  await call('POST', names, query, now + 10, clientHeaders('0.1.25'));
  const tracked = await report(yesterday, day, now + 25);
  assert.equal(tracked.followRankings.find(r => r.name === 'Facebook').clientVersion, '0.1.26');
  assert.equal(tracked.recentApps.find(r => r.name === 'Facebook').clientVersion, '0.1.26');
  assert.equal(tracked.catalog.find(r => r.name === 'Facebook').clientVersion, '0.1.26');
  for (const headers of [{}, { 'x-following-check': 'manual', 'user-agent': 'okhttp/4.12.0' },
    { 'user-agent': clientHeaders('0.1.26')['user-agent'] }]) {
    await call('POST', names, query, now + 30, headers);
    assert.equal((await report(day, day, now + 30)).recentApps[0].clientVersion, null);
  }
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

test('changing storage policy trims on the next upload and keeps the published snapshot bounded', async () => {
  const change = (capacity, retentionHours) => query(`INSERT INTO app_control (id, revision, config, updated_at)
    VALUES (1,1,$1::jsonb,CURRENT_TIMESTAMP) ON CONFLICT(id) DO UPDATE SET config=EXCLUDED.config`,
  [JSON.stringify({ followingStorage: { capacity, retentionHours } })]);
  const upload = async (id, at) => {
    const response = await call('POST', { firstPage: true, adultFiltered: false, items: [item(id)], readAt: at }, query, at);
    assert.equal(response.statusCode, 200);
  };
  try {
    await change(1200, 48);
    const apps = Array.from({ length: 1100 }, (_, i) => ({ ...item(i + 1), key: 'app ' + (i + 1), lastSeen: now }));
    await query('UPDATE latest_apps_cache SET items=$1::jsonb', [JSON.stringify(apps)]);
    await upload(1101, now);
    assert.equal((await visibleSnapshot(query, now)).items.length, 1101);
    assert.equal((await call('GET')).body.items.length, 500);
    const tomorrow = now + 86401;
    await upload(1102, tomorrow);
    assert.equal((await visibleSnapshot(query, tomorrow)).items.length, 1102);
    assert.deepEqual((await named([{ name: 'App 1101', receipt: randomUUID() }], false, query, tomorrow)).body.items, []);
    await change(501, 48);
    await upload(1103, tomorrow + 1);
    assert.equal((await visibleSnapshot(query, tomorrow + 1)).items.length, 501);
    await change(501, 6);
    assert.equal((await visibleSnapshot(query, tomorrow + 1)).items.length, 2);
    await upload(1104, tomorrow + 2);
    assert.equal((await query('SELECT jsonb_array_length(items) AS size FROM latest_apps_cache'))[0].size, 3);
  } finally { await db.exec('TRUNCATE app_control'); }
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
  assert.equal((await visibleSnapshot(query, now)).items.length, 520);
  assert.equal((await named([{ name: 'App 1', receipt: randomUUID() }])).body.items[0].id, 1);
  assert.equal(result.body.observedAt, now);
  assert.match(result.headers['Vercel-CDN-Cache-Control'], /s-maxage/);
  await post([item(800, 'APP 520')], false, true);
  await post([item(800, 'APP 520')]);
  assert.equal((await call('GET')).body.items[0].adultFiltered, true);
});

test('a verified older release fills a named miss and displaces the least requested app', async () => {
  const catalog = Array.from({ length: catalogCapacity }, (_, i) => {
    const record = item(i + 1);
    return { ...record, key: record.name.toLowerCase(), postedAt: now - i - 1, lastSeen: now, adultFiltered: true };
  });
  await query('UPDATE latest_apps_cache SET items = $1, observed_at = $2', [JSON.stringify(catalog), now]);
  await query(`INSERT INTO daily_followed_apps(day, app_key, name, followers, misses, hits, last_requested_at)
    SELECT $1::date, 'app ' || id, 'App ' || id, 1, 0,
      CASE WHEN id = $3 THEN 1 WHEN id = $3::int - 1 THEN 0 ELSE 8 END, $4::bigint
    FROM generate_series($2::int, $3::int) id`, [day, latestFeedSize + 1, catalogCapacity, now - 60]);
  // Yesterday's demand still protects a release that has seen no hits today.
  const midnight = Date.parse(day + 'T00:00:00Z') / 1000;
  await query(`INSERT INTO daily_followed_apps(day, app_key, name, followers, misses, last_requested_at)
    VALUES ($1::date - 1, $2, $3, 1, 50, $4)`, [day, 'app ' + (catalogCapacity - 1),
    'App ' + (catalogCapacity - 1), midnight - 60]);

  const original = { ...item(catalogCapacity + 1, 'Longtime Reader'), postedAt: now - 60 * 86400 };
  const requested = [{ name: original.name, receipt: randomUUID() }];
  assert.equal((await named(requested)).body.misses, 1);
  assert.equal((await named(requested)).body.misses, 1);
  const upload = candidate => call('POST', { firstPage: false, adultFiltered: false, readAt: now,
    ids: [candidate.id], items: [candidate] });
  assert.equal((await upload(original)).statusCode, 200);
  const secondPhone = await named([{ name: original.name.toUpperCase(), receipt: randomUUID() }]);
  assert.deepEqual(secondPhone.body.items, [{ ...original, adultFiltered: false }]);
  assert.equal(secondPhone.body.misses, 0);
  assert.equal((await named(requested, true)).body.items.length, 0);
  const catalogAfter = await visibleSnapshot(query, now);
  assert.equal(catalogAfter.items.length, catalogCapacity);
  assert.ok(!catalogAfter.items.some(app => app.id === catalogCapacity));
  assert.ok(catalogAfter.items.some(app => app.id === catalogCapacity - 1));
  assert.deepEqual((await call('GET')).body.items.map(app => app.id), catalog.slice(0, latestFeedSize).map(app => app.id));

  const allRequested = catalog.slice(0, 512).map(app => ({ name: app.name, receipt: randomUUID() }));
  assert.equal((await named(allRequested)).body.items.length, latestFeedSize);

  await upload({ ...original, id: original.id + 1, postedAt: original.postedAt - 10, version: '0.9' });
  assert.equal((await named(requested)).body.items[0].id, original.id);
  await feed([1], [], now + 21601);
  assert.equal((await named(requested, false, query, now + 21601)).body.items.length, 0);
  assert.ok(!(await visibleSnapshot(query, now + 86400)).items.some(app => app.id === original.id));
});

test('migration widens an existing 500-app cache without losing its entries', async () => {
  await post([item(1)], true);
  await db.exec(`ALTER TABLE latest_apps_cache DROP CONSTRAINT latest_apps_cache_items_check,
    ADD CONSTRAINT latest_apps_cache_items_check CHECK (jsonb_array_length(items) <= 500)`);
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
  const { body } = await call('GET');
  assert.equal(body.items[0].id, 1);
  assert.equal(body.observedAt, now);
  const fill = count => query("UPDATE latest_apps_cache SET items = (SELECT jsonb_agg('{}'::jsonb) FROM generate_series(1, $1::int))", [count]);
  await fill(2000);
  await assert.rejects(fill(2001), /latest_apps_cache_items_check/);
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
