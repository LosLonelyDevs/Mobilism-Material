export function requestBody(req, res, respond) {
  if (!/^application\/json(?:\s*;|$)/i.test(req.headers?.['content-type'] || '')) {
    respond(res, 415, { error: 'Use JSON' }); return null;
  }
  try {
    const raw = Buffer.isBuffer(req.body) ? req.body.toString('utf8') : typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
    if (!raw || Buffer.byteLength(raw) > 4096) { respond(res, 413, { error: 'Request too large' }); return null; }
    const body = JSON.parse(raw);
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error();
    return body;
  } catch { respond(res, 400, { error: 'Invalid request' }); return null; }
}
