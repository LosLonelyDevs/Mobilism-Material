import { timingSafeEqual } from 'node:crypto';

export const DAY = 86_400_000;
const keys = ['schema', 'day', 'pingId', 'appVersion', 'versionCode', 'androidApi', 'manufacturer', 'model'];
export function utcDay(now = new Date()) { return now.toISOString().slice(0, 10); }

export function validDay(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(parsed.getTime()) && utcDay(parsed) === value;
}

export function validateReport(report, now = new Date()) {
  if (!report || Array.isArray(report) || typeof report !== 'object' ||
      Object.keys(report).length !== keys.length || keys.some(key => !Object.hasOwn(report, key))) return false;
  const text = (value, max) => typeof value === 'string' && value.length > 0 && value.length <= max &&
    !/[\u0000-\u001f\u007f]/.test(value);
  return report.schema === 1 && validDay(report.day) && report.day <= utcDay(now) &&
    report.day >= utcDay(new Date(now.getTime() - 7 * DAY)) &&
    typeof report.pingId === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(report.pingId) &&
    text(report.appVersion, 50) && Number.isInteger(report.versionCode) && report.versionCode > 0 && report.versionCode <= 2147483647 &&
    Number.isInteger(report.androidApi) && report.androidApi >= 26 && report.androidApi <= 100 &&
    text(report.manufacturer, 80) && text(report.model, 120);
}

export async function readReport(req) {
  if (!/^application\/json(?:\s*;|$)/i.test(req.headers['content-type'] || '')) throw new Error('Expected JSON');
  if (Number(req.headers['content-length']) > 2048) throw new Error('Too large');
  let body = req.body;
  if (body === undefined) {
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
      size += Buffer.byteLength(chunk);
      if (size > 2048) throw new Error('Too large');
      chunks.push(Buffer.from(chunk));
    }
    body = Buffer.concat(chunks);
  }
  if (Buffer.isBuffer(body)) body = body.toString('utf8');
  if (typeof body !== 'string') body = JSON.stringify(body);
  if (Buffer.byteLength(body) > 2048) throw new Error('Too large');
  return JSON.parse(body);
}

export function authorized(req, token) {
  if (typeof token !== 'string' || token.length < 32) return false;
  const supplied = Buffer.from(req.headers.authorization || '');
  const expected = Buffer.from(`Bearer ${token}`);
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

export function respond(res, status, body) {
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Type', 'application/json');
  res.statusCode = status;
  res.end(JSON.stringify(body));
}
