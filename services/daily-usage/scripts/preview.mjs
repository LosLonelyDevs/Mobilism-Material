// Local preview uses an in-memory database and synthetic credentials only.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { resolve, extname, sep } from 'node:path';
import { PGlite } from '@electric-sql/pglite';
import { createHandler as sessionHandler } from '../api/admin-session.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { createHandler as configHandler } from '../api/app-config.js';
import { createHandler as controlHandler } from '../api/control-config.js';
import { createHandler as releasesHandler } from '../api/releases.js';
import { encodeBase32, makeToken, SESSION_COOKIE, SESSION_SECONDS } from '../lib/admin-auth.js';
import { sessionUser } from '../lib/access.js';
import { userStore, publicUser } from '../lib/user-store.js';
import { createHandler as usersHandler } from '../api/users.js';
import { createHandler as accountHandler } from '../api/account.js';

const env = { ADMIN_USERNAME: 'preview', ADMIN_PASSWORD: randomBytes(32).toString('base64url'),
  ADMIN_TOTP_SECRET: encodeBase32(randomBytes(20)), ADMIN_SESSION_SECRET: randomBytes(48).toString('base64url') };
if (process.argv.includes('--test-auth')) {
  if (!process.env.PREVIEW_TEST_PASSWORD || !process.env.PREVIEW_TEST_TOTP_SECRET)
    throw new Error('Set PREVIEW_TEST_PASSWORD and PREVIEW_TEST_TOTP_SECRET for the local auth test.');
  env.ADMIN_PASSWORD = process.env.PREVIEW_TEST_PASSWORD;
  env.ADMIN_TOTP_SECRET = process.env.PREVIEW_TEST_TOTP_SECRET;
}
const db = new PGlite();
await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
const query = async (sql, values) => (await db.query(sql, values)).rows;
for (let days = 0; days < 90; days++) {
  const day = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10);
  await query(`INSERT INTO daily_usage VALUES ($1, '0.1.18', 19, 36, 'Google', 'Pixel 9 Pro', $2)`, [day, 12 + days % 13]);
  await query(`INSERT INTO daily_usage VALUES ($1, '0.1.17', 18, 35, 'Samsung', 'Galaxy S25 Ultra', $2)`, [day, 5 + days % 7]);
}
const session = sessionHandler({ database: query, env });
const dashboardPreview = process.argv.includes('--dashboard');
const access = req => dashboardPreview || sessionUser(req, { database: query, env });
const stats = statsHandler(query, () => '', () => new Date(), access);
const config = configHandler(query);
const control = controlHandler(query, access);
const futureFollowing = process.argv.includes('--following-controls');
const releases = releasesHandler(futureFollowing ? async () => ({
  versions: [{ version: '0.1.24', versionCode: 25, prerelease: false }], comparisonsAvailable: true,
  unavailable: [], checkedAt: new Date().toISOString(),
}) : undefined, access);
const users = usersHandler({ database: query, env }), account = accountHandler({ database: query, env });
await userStore(query, env).ensureOwner();
const previewOwner = await userStore(query, env).get('owner');
const previewUser = publicUser(previewOwner);
const previewToken = dashboardPreview ? makeToken(SESSION_COOKIE, SESSION_SECONDS, env, Date.now()) : null;
if (previewToken) await userStore(query, env).issueSession(previewToken.split('.')[1], previewOwner, Date.now() + SESSION_SECONDS * 1000);
const staticRoot = fileURLToPath(new URL('../dist/', import.meta.url));
const port = Number(process.env.PREVIEW_PORT || 43921);
http.createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (dashboardPreview && path !== '/api/admin-session') req.headers.cookie = `${SESSION_COOKIE}=${previewToken}`;
    if (dashboardPreview && path === '/api/admin-session' && req.method === 'GET') {
      res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ authenticated: true, ...previewUser })); return;
    }
    const handler = { '/api/admin-session': session, '/api/stats': stats, '/api/app-config': config, '/api/control-config': control, '/api/releases': releases,
      '/api/users': users, '/api/account': account }[path];
    if (handler) {
      const chunks = []; let length = 0;
      for await (const chunk of req) { length += chunk.length; if (length > 65536) { res.writeHead(413); res.end(); return; } chunks.push(chunk); }
      req.body = Buffer.concat(chunks);
      await handler(req, res);
      return;
    }
    const route = ['/', '/index.html', '/app-usage', '/app-usage/', '/app-controls', '/app-controls/', '/users', '/users/', '/account', '/account/'].includes(path);
    const file = resolve(staticRoot, route ? 'index.html' : `.${path}`);
    if (!file.startsWith(resolve(staticRoot) + sep) || req.method !== 'GET') { res.writeHead(404); res.end(); return; }
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', extname(file) === '.js' ? 'text/javascript' : extname(file) === '.css' ? 'text/css' : 'text/html');
    res.end(await readFile(file));
  } catch (error) { res.writeHead(error.code === 'ENOENT' ? 404 : 500); res.end('Preview unavailable'); }
}).listen(port, '127.0.0.1', () => {
  console.log(`Synthetic dashboard preview: http://127.0.0.1:${port}`);
  if (!dashboardPreview && !process.argv.includes('--test-auth')) {
    console.log('Temporary local username:', env.ADMIN_USERNAME);
    console.log('Temporary local password:', env.ADMIN_PASSWORD);
    console.log('Temporary local authenticator setup key:', env.ADMIN_TOTP_SECRET);
  }
});
