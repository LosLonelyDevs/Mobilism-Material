import { query, recordReport } from '../lib/database.js';
import { readReport, validateReport, respond } from '../lib/protocol.js';

export function createHandler(database = query, now = () => new Date()) {
  return async (req, res) => {
    if (req.method !== 'POST') {
      res.setHeader('Allow', 'POST');
      return respond(res, 405, { error: 'Use POST' });
    }
    let report;
    try { report = await readReport(req); }
    catch { return respond(res, 400, { error: 'Invalid report' }); }
    if (!validateReport(report, now())) return respond(res, 400, { error: 'Invalid report' });
    try {
      const inserted = await recordReport(database, report);
      return respond(res, 200, { accepted: true, day: report.day, pingId: report.pingId, duplicate: !inserted });
    } catch {
      return respond(res, 503, { error: 'Please retry later' });
    }
  };
}

export default createHandler();
