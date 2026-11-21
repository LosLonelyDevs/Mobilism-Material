import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createHandler as loginHandler } from '../api/admin-session.js';
import { createHandler as usersHandler } from '../api/users.js';
import { createHandler as accountHandler } from '../api/account.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { createHandler as controlsHandler } from '../api/control-config.js';
import { createHandler as followingHandler } from '../api/following-cache.js';
import { createHandler as releasesHandler } from '../api/releases.js';
import { encodeBase32, totp } from '../lib/admin-auth.js';
import { sessionUser } from '../lib/access.js';
import { generateCredentials, openSecret, userStore, verifyPassword } from '../lib/user-store.js';
import { canAccess, navigation, PAGES } from '../public/access.js';

const env = { ADMIN_USERNAME: 'test-owner', ADMIN_PASSWORD: randomBytes(32).toString('base64url'),
  ADMIN_TOTP_SECRET: encodeBase32(randomBytes(20)), ADMIN_SESSION_SECRET: randomBytes(48).toString('base64url'), VERCEL: '1' };
let db, time, login, users, account, ownerCookie;
const query = async (sql, values) => (await db.query(sql, values)).rows;
const cookies = response => [response.headers['Set-Cookie']].flat().filter(Boolean).map(value => value.split(';')[0]).join('; ');
const owner = { username: env.ADMIN_USERNAME, password: env.ADMIN_PASSWORD, totpSecret: env.ADMIN_TOTP_SECRET };
const access = req => sessionUser(req, { database: query, env, now: time });
async function call(handler, { body, method = 'POST', cookie = ownerCookie, origin = 'https://usage.example', type = 'application/json', ip = '192.0.2.1' } = {}) {
  const response = { headers: {}, setHeader(key, value) { this.headers[key] = value; }, end(value) { this.body = JSON.parse(value); } };
  await handler({ method, body, url: '/api/test', headers: { host: 'usage.example', origin, cookie,
    'x-forwarded-proto': 'https', 'x-vercel-forwarded-for': ip, 'content-type': type } }, response);
  return response;
}
async function signIn(credentials, ip = '192.0.2.1') {
  const first = await call(login, { cookie: '', ip, body: { step: 'password', username: credentials.username, password: credentials.password } });
  assert.equal(first.statusCode, 200);
  const second = await call(login, { cookie: cookies(first), ip, body: { step: 'otp', code: totp(credentials.totpSecret, time / 30000) } });
  assert.equal(second.statusCode, 200);
  return { cookie: cookies(second), user: second.body };
}
async function createUser(pages = [], isAdmin = false, username = 'new-user') {
  const credentials = { username, ...generateCredentials() };
  const result = await call(users, { body: { ...credentials, pages, isAdmin } });
  assert.equal(result.statusCode, 201);
  return { ...credentials, ...result.body.user };
}
before(async () => { db = new PGlite(); await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8')); });
beforeEach(async () => {
  await db.exec('TRUNCATE dashboard_users, dashboard_sessions, admin_login_limits, admin_login_challenges, admin_totp_counters, app_control CASCADE');
  time = Date.UTC(2026, 10, 16, 12);
  const dependencies = { database: query, env, now: () => time };
  login = loginHandler(dependencies); users = usersHandler(dependencies); account = accountHandler(dependencies);
  ownerCookie = (await signIn(owner)).cookie;
});
after(async () => { await db.close(); });

test('owner is bootstrapped once, protected from deletion and access changes, and never leaks credentials', async () => {
  const list = await call(users, { method: 'GET' });
  assert.equal(list.body.users[0].isOwner, true);
  assert.deepEqual(list.body.users[0].pages, PAGES.map(page => page.id));
  assert.equal((await call(users, { method: 'DELETE', body: { id: 'owner', version: 1 } })).statusCode, 403);
  assert.equal((await call(users, { method: 'PUT', body: { id: 'owner', version: 1, isAdmin: false, pages: [] } })).statusCode, 403);
  const serialized = JSON.stringify(list);
  for (const secret of [env.ADMIN_PASSWORD, env.ADMIN_TOTP_SECRET, 'password_hash', 'totp_secret']) assert.equal(serialized.includes(secret), false);
  await userStore(query, env).ensureOwner();
  assert.equal((await query('SELECT id FROM dashboard_users')).length, 1);
});

test('new users get random 20-character passwords, encrypted TOTP keys, and independent sign-ins', async () => {
  const first = await call(users, { body: { action: 'generate' } });
  const second = await call(users, { body: { action: 'generate' } });
  assert.equal(first.body.password.length, 20);
  assert.match(first.body.totpSecret, /^[A-Z2-7]{32}$/);
  assert.notEqual(first.body.password, second.body.password);
  assert.notEqual(first.body.totpSecret, second.body.totpSecret);
  const user = await createUser(['app-usage']);
  const stored = await userStore(query, env).get(user.id);
  assert.notEqual(stored.password_hash, user.password); assert.notEqual(stored.totp_secret, user.totpSecret);
  assert.equal(await verifyPassword(user.password, stored.password_hash), true);
  assert.equal(openSecret(stored.totp_secret, env), user.totpSecret);
  const signedIn = await signIn(user);
  assert.equal(signedIn.user.authenticated, true); assert.equal(signedIn.user.isAdmin, false);
  assert.deepEqual(signedIn.user.pages, ['app-usage']);
  assert.equal((await call(users, { body: { ...user, username: user.username.toUpperCase() } })).statusCode, 409);
});

test('page grants control navigation and API access, including direct endpoint requests', async () => {
  const user = await createUser(['app-usage']);
  const signedIn = await signIn(user), cookie = signedIn.cookie;
  assert.deepEqual(navigation(signedIn.user).map(item => item.id), ['app-usage', 'account']);
  assert.equal(canAccess(signedIn.user, 'overview'), false);
  assert.equal(canAccess(signedIn.user, 'users'), false);
  assert.equal(canAccess(signedIn.user, 'app-controls'), false);
  const stats = statsHandler(query, () => '', () => new Date(time), access, async () => ({ versions: [] }));
  const controls = controlsHandler(query, access), releases = releasesHandler(async () => ({ releases: [] }), access);
  assert.equal((await call(stats, { method: 'GET', cookie })).statusCode, 200);
  assert.equal((await call(controls, { method: 'GET', cookie })).statusCode, 403);
  assert.equal((await call(controls, { method: 'PUT', cookie, body: {} })).statusCode, 403);
  assert.equal((await call(releases, { method: 'GET', cookie })).statusCode, 403);
  for (const method of ['GET', 'POST', 'PUT', 'DELETE']) assert.equal((await call(users, { method, cookie, body: { action: 'generate' } })).statusCode, 403);
  assert.equal((await call(stats, { method: 'GET', cookie: '' })).statusCode, 401);
});

test('app-controls grant permits saving fields without admin status or app-usage access', async () => {
  const user = await createUser(['app-controls']), { cookie } = await signIn(user);
  const controls = controlsHandler(query, access);
  const config = await call(controls, { method: 'GET', cookie });
  assert.equal(config.statusCode, 200);
  const saved = await call(controls, { method: 'PUT', cookie, body: { ...config.body, limits: { searchSpacingMs: 10000 } } });
  assert.equal(saved.statusCode, 200);
  assert.equal((await call(controls, { method: 'GET', cookie })).body.limits.searchSpacingMs, 10000);
  assert.equal((await call(statsHandler(query, () => '', () => new Date(time), access), { method: 'GET', cookie })).statusCode, 403);
});

test('admin status grants Following cache and user management; owner remains protected', async () => {
  const user = await createUser([], true), { cookie, user: auth } = await signIn(user);
  assert.deepEqual(navigation(auth).map(item => item.id), ['following-cache', 'users', 'account']);
  assert.equal((await call(users, { method: 'GET', cookie })).statusCode, 200);
  assert.equal((await call(users, { method: 'DELETE', cookie, body: { id: 'owner', version: 1 } })).statusCode, 403);
  assert.equal((await call(users, { method: 'DELETE', cookie, body: { id: user.id, version: 1 } })).statusCode, 403);
  assert.equal((await call(users, { method: 'PUT', body: { id: user.id, version: 1, pages: [], isAdmin: false } })).statusCode, 200);
  assert.equal((await call(users, { method: 'GET', cookie })).statusCode, 401);
  time += 30000;
  const demoted = await signIn(user);
  assert.equal((await call(users, { method: 'GET', cookie: demoted.cookie })).statusCode, 403);
  assert.equal(canAccess(demoted.user, 'following-cache'), false);
});

test('Following cache has its own grant and owns the pull switch across concurrent config edits', async () => {
  const following = followingHandler(query, access, async () => ({ versions: [{ version: '0.1.24', prerelease: false, versionCode: 25 }] }));
  const controls = controlsHandler(query, access);
  const general = await signIn(await createUser(['app-usage', 'app-controls']));
  const editor = await signIn(await createUser(['following-cache'], false, 'cache-editor'));
  const read = (handler, cookie) => call(handler, { method: 'GET', cookie });
  const put = (handler, cookie, body) => call(handler, { method: 'PUT', cookie, body });
  const toggle = { revision: 0, enabled: true };
  assert.equal((await read(following, general.cookie)).statusCode, 403);
  assert.equal((await put(following, general.cookie, toggle)).statusCode, 403);
  assert.equal((await read(controls, editor.cookie)).statusCode, 403);
  const initial = (await read(controls, general.cookie)).body;
  assert.deepEqual((await read(following, editor.cookie)).body.pullToRefresh, { revision: 0, enabled: false });
  assert.equal((await call(following, { method: 'PUT', origin: 'https://elsewhere.example', cookie: editor.cookie, body: toggle })).statusCode, 403);
  assert.equal((await put(following, editor.cookie, { ...toggle, limits: {} })).statusCode, 400);
  assert.deepEqual((await put(following, editor.cookie, toggle)).body, { revision: 1, enabled: true });
  assert.equal((await put(following, editor.cookie, toggle)).statusCode, 409);
  assert.equal((await put(controls, general.cookie, initial)).statusCode, 409);
  const current = (await read(controls, general.cookie)).body;
  assert.equal('followPullToRefreshEnabled' in current.limits, false);
  assert.equal((await put(controls, general.cookie, { ...current, limits: { followPullToRefreshEnabled: false } })).statusCode, 403);
  assert.equal((await put(controls, general.cookie, { ...current, limits: {} })).statusCode, 200);
  assert.equal((await read(following, editor.cookie)).body.pullToRefresh.enabled, true);
});

test('access changes revoke sessions and pending challenges; stale edits and deleted users cannot restore access', async () => {
  const user = await createUser(['app-usage']), { cookie } = await signIn(user);
  const challenge = cookies(await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } }));
  const body = { id: user.id, version: 1, isAdmin: false, pages: [] };
  assert.equal((await call(users, { method: 'PUT', body })).statusCode, 200);
  assert.equal((await call(users, { method: 'PUT', body })).statusCode, 409);
  assert.equal(await access({ headers: { cookie } }), null);
  const replayChallenge = cookies(await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } }));
  assert.equal((await call(login, { cookie: replayChallenge, body: { step: 'otp', code: totp(user.totpSecret, time / 30000) } })).statusCode, 401);
  time += 30000;
  assert.equal((await call(login, { cookie: challenge, body: { step: 'otp', code: totp(user.totpSecret, time / 30000) } })).statusCode, 401);
  const next = await signIn(user);
  assert.deepEqual(navigation(next.user).map(item => item.id), ['account']);
  assert.equal((await call(users, { method: 'DELETE', body: { id: user.id, version: 2 } })).statusCode, 200);
  assert.equal(await access({ headers: { cookie: next.cookie } }), null);
  assert.equal((await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } })).statusCode, 401);
});

test('password changes preserve TOTP, revoke all sessions, and ignore attempts to update another user', async () => {
  const user = await createUser(['app-usage']), first = await signIn(user);
  time += 30000;
  const second = await signIn(user);
  time += 30000;
  const newPassword = generateCredentials().password;
  const result = await call(account, { cookie: second.cookie, body: { action: 'password', id: 'owner', newPassword,
    currentPassword: user.password, currentCode: totp(user.totpSecret, time / 30000) } });
  assert.equal(result.statusCode, 200);
  assert.equal(await access({ headers: { cookie: first.cookie } }), null);
  assert.equal(await access({ headers: { cookie: second.cookie } }), null);
  const stored = await userStore(query, env).get(user.id);
  assert.equal(openSecret(stored.totp_secret, env), user.totpSecret);
  assert.equal(await verifyPassword(newPassword, stored.password_hash), true);
  assert.equal(await verifyPassword(env.ADMIN_PASSWORD, (await userStore(query, env).get('owner')).password_hash), true);
  assert.equal((await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } })).statusCode, 401);
  time += 30000;
  assert.equal((await signIn({ ...user, password: newPassword })).user.id, user.id);
});

test('authenticator changes require both old and new codes, preserve password, and revoke pending challenges', async () => {
  const user = await createUser(), signedIn = await signIn(user);
  const next = generateCredentials().totpSecret;
  const challenge = cookies(await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } }));
  time += 30000;
  const body = { action: 'totp', totpSecret: next, currentPassword: user.password, currentCode: totp(user.totpSecret, time / 30000) };
  assert.equal((await call(account, { cookie: signedIn.cookie, body: { ...body, newCode: 'bad' } })).statusCode, 400);
  assert.equal((await call(account, { cookie: signedIn.cookie, body: { ...body, newCode: totp(next, time / 30000) } })).statusCode, 200);
  const stored = await userStore(query, env).get(user.id);
  assert.equal(await verifyPassword(user.password, stored.password_hash), true);
  assert.equal(openSecret(stored.totp_secret, env), next);
  assert.equal((await call(login, { cookie: challenge, body: { step: 'otp', code: totp(user.totpSecret, time / 30000) } })).statusCode, 401);
  time += 30000;
  await signIn({ ...user, totpSecret: next });
});

test('owner can rotate credentials independently without environment bootstrap overwriting them', async () => {
  time += 30000;
  const newPassword = generateCredentials().password;
  assert.equal((await call(account, { body: { action: 'password', currentPassword: owner.password,
    currentCode: totp(owner.totpSecret, time / 30000), newPassword } })).statusCode, 200);
  await userStore(query, env).ensureOwner();
  time += 30000;
  const signedIn = await signIn({ ...owner, password: newPassword });
  assert.equal(signedIn.user.isOwner, true); assert.equal(signedIn.user.isAdmin, true);
});

test('security updates reject CSRF, invalid bodies, reused codes, and rate-limit wrong reauthentication', async () => {
  const user = await createUser(), { cookie } = await signIn(user);
  const body = { action: 'password', currentPassword: user.password, currentCode: totp(user.totpSecret, time / 30000), newPassword: generateCredentials().password };
  assert.equal((await call(account, { cookie, body, origin: 'https://attacker.example' })).statusCode, 403);
  assert.equal((await call(users, { body: { action: 'generate' }, origin: 'https://attacker.example' })).statusCode, 403);
  assert.equal((await call(account, { cookie, body, type: 'text/plain' })).statusCode, 415);
  assert.equal((await call(account, { cookie, body: 'x'.repeat(5000) })).statusCode, 413);
  assert.equal((await call(account, { cookie, body })).statusCode, 400);
  time += 30000;
  for (let attempt = 0; attempt < 4; attempt++) {
    const result = await call(account, { cookie, body: { ...body, currentPassword: 'wrong', currentCode: totp(user.totpSecret, time / 30000) } });
    assert.equal(result.statusCode, attempt === 3 ? 429 : 400);
  }
  assert.equal((await call(login, { cookie: '', body: { step: 'password', username: user.username, password: user.password } })).statusCode, 429);
});

test('schema migration is idempotent and database failures fail closed', async () => {
  await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
  const broken = async () => { throw new Error(env.ADMIN_PASSWORD); };
  const result = await call(usersHandler({ database: broken, env, now: () => time }), { method: 'GET' });
  assert.equal(result.statusCode, 503); assert.equal(JSON.stringify(result).includes(env.ADMIN_PASSWORD), false);
  assert.equal((await call(accountHandler({ database: broken, env, now: () => time }), { body: {} })).statusCode, 503);
});
