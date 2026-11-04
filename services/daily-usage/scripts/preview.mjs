// Local preview uses an in-memory database and synthetic credentials only.
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { randomBytes } from 'node:crypto';
import { PGlite } from '@electric-sql/pglite';
import { createHandler as sessionHandler } from '../api/admin-session.js';
import { createHandler as statsHandler } from '../api/stats.js';
import { encodeBase32, isAdminRequest } from '../lib/admin-auth.js';

const env = { ADMIN_USERNAME: 'preview', ADMIN_PASSWORD: randomBytes(32).toString('base64url'),
  ADMIN_TOTP_SECRET: encodeBase32(randomBytes(20)), ADMIN_SESSION_SECRET: randomBytes(48).toString('base64url') };
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
const stats = statsHandler(query, () => '', () => new Date(), req => dashboardPreview || isAdminRequest(req, env));
const files = { '/': 'index.html', '/index.html': 'index.html', '/dashboard.js': 'dashboard.js', '/metrics.js': 'metrics.js', '/dashboard.css': 'dashboard.css', '/site.css': 'site.css' };
http.createServer(async (req, res) => {
  try {
    const path = new URL(req.url, 'http://localhost').pathname;
    if (dashboardPreview && path === '/api/admin-session' && req.method === 'GET') {
      res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ isAdmin: true })); return;
    }
    if (path === '/api/admin-session' || path === '/api/stats') {
      const chunks = []; let length = 0;
      for await (const chunk of req) { length += chunk.length; if (length > 2048) { res.writeHead(413); res.end(); return; } chunks.push(chunk); }
      req.body = Buffer.concat(chunks);
      await (path === '/api/admin-session' ? session : stats)(req, res);
      return;
    }
    if (!files[path] || req.method !== 'GET') { res.writeHead(404); res.end(); return; }
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('Content-Type', path.endsWith('.js') ? 'text/javascript' : path.endsWith('.css') ? 'text/css' : 'text/html');
    res.end(await readFile(new URL(`../public/${files[path]}`, import.meta.url)));
  } catch { res.writeHead(500); res.end('Preview unavailable'); }
}).listen(43921, '127.0.0.1', () => {
  console.log('Synthetic dashboard preview: http://127.0.0.1:43921');
  if (!dashboardPreview) {
    console.log('Temporary local username:', env.ADMIN_USERNAME);
    console.log('Temporary local password:', env.ADMIN_PASSWORD);
    console.log('Temporary local authenticator setup key:', env.ADMIN_TOTP_SECRET);
  }
});
