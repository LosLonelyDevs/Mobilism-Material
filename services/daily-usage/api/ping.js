import { query, recordReport } from '../lib/database.js';
import { authorizeAppWrite, enrollmentEnabled } from '../lib/app-writes.js';
import { readReport, validateReport, respond } from '../lib/protocol.js';
import { acceptObservations } from './latest-apps.js';

export function createHandler(database = query, now = () => new Date(), env = process.env) {
  return async (req, res) => {
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST');
      return respond(res, 405, { error: 'Use POST' });
    }
    if (enrollmentEnabled(env)) res.setHeader('X-App-Attestation', '1');
    let report, allowed, renew, unavailable;
    try { ({ body: report, allowed, renew, unavailable } = await authorizeAppWrite(await readReport(req), req, 'usage', database, env)); }
    catch { return respond(res, 400, { error: 'Invalid report' }); }
    if (unavailable) return respond(res, 503, { error: 'Please retry later' });
    if (renew) res.setHeader('X-App-Attestation-Renew', '1');
    if (!allowed) return respond(res, 428, { error: 'Verified install required' });
    let observations;
    if (report && Object.hasOwn(report, 'report') && Object.hasOwn(report, 'observations') && Object.keys(report).length === 2) {
      observations = report.observations;
      report = report.report;
    }
    if (!validateReport(report, now())) return respond(res, 400, { error: 'Invalid report' });
    try {
      const inserted = await recordReport(database, report);
      await acceptObservations(database, observations, Math.floor(now().getTime() / 1000), res);
      return respond(res, 200, { accepted: true, day: report.day, pingId: report.pingId, duplicate: !inserted });
    } catch {
      return respond(res, 503, { error: 'Please retry later' });
    }
  };
}

export default createHandler();
