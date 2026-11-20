import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';
import { createHandler } from '../api/latest-apps.js';

const now = 1900000000;
let db;
before(async () => {
  db = new PGlite();
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
});
beforeEach(async () => { await db.exec("UPDATE latest_apps_cache SET items = '[]', observed_at = 0"); });
after(async () => { await db.close(); });
const query = async (sql, values) => (await db.query(sql, values)).rows;
const item = (id, name = 'App ' + id) => ({
  id, name, title: name + ' v1.1', version: '1.1', postedAt: now - 2000 + id, author: 'Uploader'
});
async function call(method, body, database = query) {
  const res = { headers: {}, setHeader(key, value) { this.headers[key] = value; },
    end(value) { this.body = JSON.parse(value); } };
  await createHandler(database, () => now)({ method, headers: { 'content-type': 'application/json' }, body }, res);
  return res;
}
const post = (items, firstPage = false, adultFiltered = false) => call('POST', { firstPage, adultFiltered, items });

test('concurrent overlapping pages keep the newest 200 unique apps in post order', async () => {
  const pages = Array.from({ length: 11 }, (_, page) => Array.from({ length: 20 }, (_, i) => item(page * 20 + i + 1)));
  const uploads = await Promise.all(pages.reverse().map((items, index) => post(items, index === 0)));
  assert.ok(uploads.every(res => res.statusCode === 200));
  assert.equal((await post([item(500, 'APP 220')])).statusCode, 200);
  assert.equal((await post([item(220), item(1)])).statusCode, 200);
  const result = await call('GET');
  assert.equal(result.body.items.length, 200);
  assert.deepEqual(result.body.items[0], { ...item(500, 'APP 220'), adultFiltered: false });
  assert.equal(result.body.items.at(-1).id, 21);
  assert.equal(result.body.observedAt, now);
  assert.match(result.headers['Vercel-CDN-Cache-Control'], /s-maxage/);
  await post([item(500, 'APP 220')], false, true);
  await post([item(500, 'APP 220')]);
  assert.equal((await call('GET')).body.items[0].adultFiltered, true);
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
