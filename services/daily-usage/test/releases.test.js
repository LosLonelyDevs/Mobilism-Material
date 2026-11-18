import { test } from 'node:test';
import assert from 'node:assert/strict';
import { compareVersions, createReleaseCatalog, latestStableVersion, releaseVersion } from '../lib/releases.js';
import { createHandler } from '../api/releases.js';

const release = (version, prerelease = false) => ({ tag_name: `v${version}`, prerelease, draft: false });
test('latest stable uses the verified release catalog and handles incomplete results', () => {
  const versions = [{ version: '0.2.0', prerelease: true }, { version: '0.1.20', prerelease: false }];
  assert.equal(latestStableVersion({ versions, unavailable: ['0.1.9'] }), '0.1.20');
  assert.equal(latestStableVersion({ versions, unavailable: ['0.1.21'] }), null);
  assert.equal(latestStableVersion({ versions, stale: true }), null);
  assert.equal(latestStableVersion(null), null);
});
const source = (version, code) => `android {\n versionCode = ${code}\n versionName = "${version}"\n}`;
test('release versions sort numerically and use verified Android metadata', () => {
  assert.ok(compareVersions('0.1.10', '0.1.9') > 0);
  assert.ok(compareVersions('0.2.0', '0.1.99') > 0);
  assert.deepEqual(releaseVersion(release('0.1.21'), source('0.1.21', 22)), { version: '0.1.21', versionCode: 22, prerelease: false });
  assert.throws(() => releaseVersion(release('0.1.21'), source('0.1.20', 21)));
  assert.throws(() => releaseVersion(release('0.1.21'), source('0.1.21', 'project.versionCode')));
});
test('catalog excludes drafts, includes published prereleases, caches and deduplicates requests', async () => {
  let calls = 0;
  const catalog = createReleaseCatalog(async url => {
    calls++;
    if (url.includes('api.github.com')) return Response.json([release('0.1.9', true), release('0.1.10'), { ...release('0.1.11'), draft: true }]);
    const version = url.includes('/v0.1.10/') ? '0.1.10' : '0.1.9';
    return new Response(source(version, version === '0.1.10' ? 11 : 10));
  });
  const [a, b] = await Promise.all([catalog(), catalog()]);
  assert.deepEqual(a, b);
  assert.deepEqual(a.versions.map(row => row.version), ['0.1.10', '0.1.9']);
  assert.equal(a.comparisonsAvailable, true);
  await catalog();
  assert.equal(calls, 3);
});
test('catalog never guesses missing or out-of-order version codes', async () => {
  for (const missing of [true, false]) {
    const catalog = createReleaseCatalog(async url => url.includes('api.github.com')
      ? Response.json([release('0.1.20'), release('0.1.21')])
      : url.includes('/v0.1.21/') ? new Response(source('0.1.21', 22))
      : missing ? new Response('Unavailable', { status: 503 }) : new Response(source('0.1.20', 23)));
    const result = await catalog();
    assert.equal(result.comparisonsAvailable, false);
    assert.deepEqual(result.unavailable, missing ? ['0.1.20'] : []);
  }
});
test('refresh failure preserves the last verified catalog and marks it stale', async () => {
  let time = 0, offline = false;
  const catalog = createReleaseCatalog(async url => {
    if (offline) throw new Error('Offline');
    return url.includes('api.github.com') ? Response.json([release('0.1.21')]) : new Response(source('0.1.21', 22));
  }, () => time);
  const original = await catalog(); time = 700_000; offline = true;
  assert.deepEqual(await catalog(), { ...original, stale: true });
});
test('release endpoint requires an admin session and only accepts GET', async () => {
  let calls = 0;
  const catalog = async () => { calls++; return { versions: [] }; };
  const call = async (authorized, method = 'GET') => {
    const res = { setHeader() {}, end(body) { this.body = JSON.parse(body); } };
    await createHandler(catalog, () => authorized)({ method }, res);
    return res;
  };
  assert.equal((await call(false)).statusCode, 401);
  assert.equal((await call(true, 'POST')).statusCode, 405);
  assert.equal(calls, 0);
  assert.equal((await call(true)).statusCode, 200);
  assert.equal(calls, 1);
});
