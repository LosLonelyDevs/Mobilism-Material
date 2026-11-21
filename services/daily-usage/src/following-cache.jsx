import { useEffect, useRef, useState } from 'react';
import { api, today } from './api';
import { percentage } from '../public/metrics';
import { Button, Notice, Switch } from './ui';
import { mountUsageCalendar } from './usage-calendar';
import calendarMarkup from './report-calendar.html?raw';

const calendarHtml = { __html: calendarMarkup };
const number = value => BigInt(value).toLocaleString();

function Rankings({ rows, title, rank, count, label }) {
  const sorted = rows.filter(row => row[rank] <= 10 && BigInt(row[count]) > 0n)
    .sort((a, b) => a[rank] - b[rank]);
  return <section className="admin-panel"><h2>{title}</h2>
    {sorted.length ? <table className="admin-traffic-table"><thead><tr><th>App</th><th>{label}</th></tr></thead>
      <tbody>{sorted.map(row => <tr key={row[rank]}><td>{row.name}</td><td>{number(row[count])}</td></tr>)}</tbody></table>
      : <p className="muted">No apps reported for these dates.</p>}
  </section>;
}

export default function FollowingCache() {
  const calendar = useRef(null);
  const [range, setRange] = useState(() => ({ from: today(), to: today() }));
  const [report, setReport] = useState(null), [error, setError] = useState('');
  const [saving, setSaving] = useState(false), [status, setStatus] = useState('');
  useEffect(() => {
    const picker = mountUsageCalendar(calendar.current, today, setRange);
    picker.setRange(today(), today());
  }, []);
  useEffect(() => {
    const controller = new AbortController();
    setReport(null); setError(''); setStatus('');
    api('/api/following-cache?' + new URLSearchParams(range), { signal: controller.signal })
      .then(setReport).catch(err => { if (!controller.signal.aborted) setError(err.message); });
    return () => controller.abort();
  }, [range]);

  async function changeSetting(event) {
    const enabled = event.target.checked;
    setSaving(true); setStatus('');
    try {
      const setting = await api('/api/following-cache', { method: 'PUT', body: { revision: report.pullToRefresh.revision, enabled } });
      setReport(current => ({ ...current, pullToRefresh: setting }));
      setStatus('Saved.');
    } catch (err) { setStatus(err.message); }
    finally { setSaving(false); }
  }
  return <div className="admin-dashboard usage-page following-report">
    <header className="admin-heading"><div><h1>Following cache</h1><p>Update checks for followed apps.</p></div>
      <div inert={saving} ref={calendar} dangerouslySetInnerHTML={calendarHtml} />
    </header>
    <Button disabled={saving} onClick={() => setRange({ ...range })}>Refresh report</Button>
    {error ? <Notice variant="danger">{error}</Notice> : !report ? <p role="status">Loading report…</p> :
      !report.available ? <p>Available with stable v0.1.24 or later.</p> : <>
        <section className="admin-panel"><h2>Cache hit rate</h2>
          <p>Reported by clients for the selected UTC dates. Reports can arrive later.</p>
          <table className="admin-traffic-table"><thead><tr><th>Check</th><th>Hits</th><th>Misses</th><th>Hit rate</th></tr></thead><tbody>
            {[['FollowingBackground', 'Background'], ['FollowingManual', 'Manual']].map(([key, label]) => {
              const row = report.cacheUsage.find(item => item.cache === key);
              const hits = BigInt(row?.hits ?? 0), misses = BigInt(row?.misses ?? 0), total = hits + misses;
              return <tr key={key}><td>{label}</td><td>{number(hits)}</td><td>{number(misses)}</td>
                <td>{total ? percentage(hits, total) + '%' : 'No reports'}</td></tr>;
            })}
          </tbody></table>
        </section>
        <Rankings rows={report.followRankings} title="Most followed apps" rank="followRank" count="followers" label="Reporting device-days" />
        <Rankings rows={report.followRankings} title="Most missed apps" rank="missRank" count="misses" label="Missing lookups" />
        <section className="admin-panel"><h2>Following controls</h2>
          <Switch label="Allow pull-to-refresh" disabled={saving} checked={report.pullToRefresh.enabled} onChange={changeSetting} />
          <p>Allow users to pull down on Following to check for updates.</p>
          <span role="status">{saving ? 'Saving…' : status}</span>
        </section>
      </>}
  </div>;
}
