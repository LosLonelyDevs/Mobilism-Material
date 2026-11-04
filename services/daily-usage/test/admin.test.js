import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createHandler } from '../api/admin-session.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { CHALLENGE_COOKIE, SESSION_COOKIE, encodeBase32, isAdminRequest, totp } from '../lib/admin-auth.js';

const env = { ADMIN_USERNAME: 'test-admin', ADMIN_PASSWORD: randomBytes(32).toString('base64url'),
  ADMIN_TOTP_SECRET: encodeBase32(randomBytes(20)), ADMIN_SESSION_SECRET: randomBytes(48).toString('base64url'), VERCEL: '1' };
let db, time, handler;
const query = async (sql, values) => (await db.query(sql, values)).rows;
const credentials = { step: 'password', username: env.ADMIN_USERNAME, password: env.ADMIN_PASSWORD };
const cookies = response => [response.headers['Set-Cookie']].flat().filter(Boolean).map(value => value.split(';')[0]).join('; ');
async function call(body, { cookie = '', method = 'POST', origin = 'https://usage.example', ip = '192.0.2.1', action = handler } = {}) {
  const response = { headers: {}, statusCode: 0, setHeader(key, value) { this.headers[key] = value; }, end(value) { this.body = JSON.parse(value); } };
  await action({ method, body, url: '/api/admin-session', headers: { host: 'usage.example', origin, cookie,
    'x-forwarded-proto': 'https', 'x-vercel-forwarded-for': ip, 'content-type': 'application/json' } }, response);
  return response;
}
before(async () => { db = new PGlite(); await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8')); });
beforeEach(async () => {
  await db.exec('TRUNCATE admin_login_limits, admin_login_challenges, admin_totp_counters');
  time = Date.UTC(2026, 10, 4, 12, 0, 0);
  handler = createHandler({ database: query, env, now: () => time });
});
after(async () => { await db.close(); });

test('TOTP matches all RFC 6238 SHA-1 vectors', () => {
  const rfcSecret = encodeBase32(Buffer.from('12345678901234567890'));
  const vectors = [[59, '94287082'], [1111111109, '07081804'], [1111111111, '14050471'],
    [1234567890, '89005924'], [2000000000, '69279037'], [20000000000, '65353130']];
  for (const [seconds, code] of vectors) assert.equal(totp(rfcSecret, Math.floor(seconds / 30), 8), code);
});

test('password alone grants no statistics access; only the second step creates a session', async () => {
  const first = await call(credentials);
  assert.deepEqual(first.body, { isAdmin: false, stage: 'otp' });
  assert.ok(first.headers['Set-Cookie'].startsWith(`${CHALLENGE_COOKIE}=`));
  assert.match(first.headers['Set-Cookie'], /HttpOnly; SameSite=Strict; Max-Age=300; Secure/);
  const challenge = cookies(first);
  assert.equal(isAdminRequest({ headers: { cookie: challenge } }, env, time), false);
  const stats = statsHandler(query, () => '', () => new Date(time), req => isAdminRequest(req, env, time));
  assert.equal((await call(null, { method: 'GET', cookie: challenge, action: stats })).statusCode, 401);
  assert.equal((await call(null, { method: 'GET', cookie: challenge })).body.stage, 'otp');
  const second = await call({ step: 'otp', code: totp(env.ADMIN_TOTP_SECRET, time / 30000) }, { cookie: challenge });
  assert.equal(second.statusCode, 200);
  assert.equal(second.body.isAdmin, true);
  assert.equal(isAdminRequest({ headers: { cookie: cookies(second) } }, env, time), true);
  assert.equal((await call(null, { method: 'GET', cookie: cookies(second), action: stats })).statusCode, 200);
});

test('wrong passwords never reveal the OTP page and lock out across instances and IPs', async () => {
  for (let attempt = 1; attempt <= 5; attempt++) {
    handler = createHandler({ database: query, env, now: () => time });
    const result = await call({ ...credentials, password: 'wrong' }, { ip: `192.0.2.${attempt}` });
    assert.equal(result.statusCode, attempt === 5 ? 429 : 401);
    assert.equal(result.body.stage, undefined);
    assert.equal(result.headers['Set-Cookie'], undefined);
  }
  const locked = await call(credentials, { ip: '192.0.2.99' });
  assert.equal(locked.statusCode, 429); assert.equal(locked.headers['Retry-After'], '900');
  time += 899000;
  assert.equal((await call(credentials)).statusCode, 429);
  time += 1001;
  assert.equal((await call(credentials)).body.stage, 'otp');
});

test('OTP cannot skip passwords, move between clients, or reuse an expired challenge', async () => {
  const body = { step: 'otp', code: totp(env.ADMIN_TOTP_SECRET, time / 30000) };
  assert.equal((await call(body)).statusCode, 401);
  const first = await call(credentials);
  assert.equal((await call(body, { cookie: cookies(first), ip: '192.0.2.2' })).statusCode, 401);
  time += 300001;
  assert.equal((await call(body, { cookie: cookies(first) })).body.stage, 'password');
});

test('successive wrong codes lock out, even after a correct password is submitted again', async () => {
  const correct = totp(env.ADMIN_TOTP_SECRET, time / 30000);
  const wrong = correct === '000000' ? '111111' : '000000';
  for (let attempt = 1; attempt <= 5; attempt++) {
    const first = await call(credentials);
    const result = await call({ step: 'otp', code: wrong }, { cookie: cookies(first) });
    assert.equal(result.statusCode, attempt === 5 ? 429 : 401);
  }
  assert.equal((await call(credentials)).statusCode, 429);
});

test('concurrent challenge reuse and same-code reuse each allow only one completed sign-in', async () => {
  const challenge = cookies(await call(credentials));
  const body = { step: 'otp', code: totp(env.ADMIN_TOTP_SECRET, time / 30000) };
  const results = await Promise.all([call(body, { cookie: challenge }), call(body, { cookie: challenge })]);
  assert.equal(results.filter(result => result.statusCode === 200).length, 1);
  const next = cookies(await call(credentials));
  assert.equal((await call(body, { cookie: next })).statusCode, 401);
  time += 30000;
  const fresh = cookies(await call(credentials));
  assert.equal((await call({ step: 'otp', code: totp(env.ADMIN_TOTP_SECRET, time / 30000) }, { cookie: fresh })).statusCode, 200);
});

test('session expiry, credential rotation, forged cookies, CSRF, and logout are enforced', async () => {
  assert.equal((await call(credentials, { origin: 'https://attacker.example' })).statusCode, 403);
  const first = cookies(await call(credentials));
  const result = await call({ step: 'otp', code: totp(env.ADMIN_TOTP_SECRET, time / 30000) }, { cookie: first });
  const request = { headers: { cookie: cookies(result) } };
  assert.equal(isAdminRequest(request, { ...env, ADMIN_PASSWORD: `${env.ADMIN_PASSWORD}rotated` }, time), false);
  assert.equal(isAdminRequest(request, env, time + 12 * 60 * 60 * 1000), false);
  assert.equal(isAdminRequest({ headers: { cookie: `${cookies(result)}x` } }, env, time), false);
  const logout = await call(null, { method: 'DELETE', cookie: cookies(result) });
  assert.ok(logout.headers['Set-Cookie'].some(value => value.startsWith(`${SESSION_COOKIE}=`) && value.includes('Max-Age=0')));
});

test('database failures fail closed and neither errors nor responses reveal secrets', async () => {
  const action = createHandler({ database: async () => { throw new Error(env.ADMIN_PASSWORD); }, env, now: () => time });
  const response = await call(credentials, { action });
  assert.equal(response.statusCode, 503);
  const serialized = JSON.stringify(response);
  for (const key of ['ADMIN_PASSWORD', 'ADMIN_TOTP_SECRET', 'ADMIN_SESSION_SECRET']) assert.equal(serialized.includes(env[key]), false);
});
