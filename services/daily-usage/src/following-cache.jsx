import { useEffect, useRef, useState } from 'react';
import { api, today } from './api';
import { validStorage } from '../public/config-schema.js';
import { followingRankingLimit, percentage } from '../public/metrics';
import { Button, CollapsibleCard, InputField, Notice, Switch } from './ui';
import { mountUsageCalendar } from './usage-calendar';
import calendarMarkup from './report-calendar.html?raw';

const calendarHtml = { __html: calendarMarkup };
const number = value => BigInt(value).toLocaleString();
const rate = (count, total) => total ? percentage(count, total) + '%' : '—';
const requestTime = new Intl.DateTimeFormat(undefined, {
  timeZone: 'UTC', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
});

function CacheCard({ title, subtitle, defaultOpen = false, children }) {
  return <section aria-label={title}>
    <CollapsibleCard title={title} subtitle={subtitle} defaultOpen={defaultOpen} style={{ backgroundColor: 'var(--admin-surface)', borderRadius: 28, marginBottom: 16 }}>
      {children}
    </CollapsibleCard>
  </section>;
}

function Rankings({ rows, title, rank, count, displayCount = count, label }) {
  const sorted = rows.filter(row => row[rank] <= followingRankingLimit && BigInt(row[count]) > 0n)
    .sort((a, b) => a[rank] - b[rank]);
  return <CacheCard title={title} defaultOpen>
    {sorted.length ? <table className="admin-traffic-table cache-rankings"><thead><tr><th>App</th><th>Cached version</th><th>Client version</th><th>{label}</th></tr></thead>
      <tbody>{sorted.map(row => <tr key={row[rank]}><td>{row.name}</td><td>{row.cachedVersion ?? 'Not cached'}</td>
        <td className="client-version">{row.clientVersion ?? 'Unknown'}</td>
        <td>{BigInt(row[displayCount]) > 0n ? number(row[displayCount]) : '—'}</td></tr>)}</tbody></table>
      : <p className="muted">No apps reported for these dates.</p>}
  </CacheCard>;
}

function RecentApps({ apps }) {
  return <CacheCard title="Most recently requested apps">
    {apps.length ? <table className="admin-traffic-table cache-rankings recent-apps">
      <thead><tr><th>App</th><th>Cached version</th><th>Client version</th><th>Requested (UTC)</th></tr></thead>
      <tbody>{apps.map(app => <tr key={app.name}><td>{app.name}</td><td>{app.cachedVersion ?? 'Not cached'}</td>
        <td className="client-version">{app.clientVersion ?? 'Unknown'}</td>
        <td>{requestTime.format(app.requestedAt * 1000)}</td></tr>)}</tbody>
    </table> : <p className="muted">No timestamped requests for these dates.</p>}
  </CacheCard>;
}

function CacheSearch({ apps }) {
  const [query, setQuery] = useState('');
  const normalize = text => text.normalize('NFKC').toLowerCase().trim().replace(/\s+/g, ' ');
  const term = normalize(query);
  const words = term.split(' ');
  const matches = term ? apps.map(app => ({ ...app, key: normalize(app.name) }))
    .filter(app => words.every(word => app.key.includes(word)))
    .sort((a, b) => Number(b.key === term) - Number(a.key === term) ||
      Number(b.key.startsWith(term)) - Number(a.key.startsWith(term)) || a.key.localeCompare(b.key))
    .slice(0, 5) : [];
  return <CacheCard title="Search cache">
    <div className="cache-search-body">
      <InputField label="App name" type="search" value={query} maxLength={200} autoComplete="off" onChange={e => setQuery(e.target.value)} />
      {term && (matches.length ? <table className="admin-traffic-table cache-rankings">
        <thead><tr><th>App</th><th>Cached version</th><th>Client version</th></tr></thead>
        <tbody>{matches.map(app => <tr key={app.key}><td>{app.name}</td><td>{app.version}</td>
          <td className="client-version">{app.clientVersion ?? 'Unknown'}</td></tr>)}</tbody>
      </table> : <p role="status" className="muted">No matching apps in the cache.</p>)}
    </div>
  </CacheCard>;
}

function LookupSummary({ report }) {
  const hits = BigInt(report.lookups.hits), misses = BigInt(report.lookups.misses);
  const measured = BigInt(report.lookups.measuredMisses), total = hits + measured;
  const partial = report.to === report.asOf.slice(0, 10);
  const time = new Date(report.asOf).toLocaleTimeString(undefined, { timeZone: 'UTC', hour: '2-digit', minute: '2-digit' });
  return <section className="admin-panel"><h2>Cache lookups</h2>
    <p className="lookup-period">{partial ? `Includes today through ${time} UTC.` : 'Selected UTC dates.'}</p>
    <div className="destination-metrics">
      {[[number(report.cachedApps), 'Cached apps'], [number(hits), 'Hits'], [number(misses), 'Misses'],
        [rate(hits, total), 'Hit rate'], [rate(measured, total), 'Miss rate']].map(([value, label]) =>
        <div key={label}><span className="metric-value">{value}</span><span className="muted">{label}</span></div>)}
    </div>
  </section>;
}

function DeviceReports({ report }) {
  const rows = [];
  const live = report.todayLookups;
  if (live) {
    rows.push({ label: 'Today · all checks', ...live });
    for (const [label, hits, misses] of [
      ['Of which background', live.backgroundHits, live.backgroundMisses],
      ['Of which manual', live.manualHits, live.manualMisses],
    ]) {
      if (BigInt(hits) + BigInt(misses) > 0n) rows.push({ label, hits, misses });
    }
  }
  for (const row of report.cacheUsage) {
    const mode = row.cache === 'FollowingBackground' ? 'Background' : 'Manual';
    rows.push({ label: report.todayLookups ? `Earlier · ${mode}` : mode, ...row });
  }
  return <section className="admin-panel"><h2>Device reports</h2>
    {rows.length ? <div className="admin-traffic-scroll"><table className="admin-traffic-table"><thead><tr>
      <th>Check</th><th className="client-version">Client version</th><th>Hits</th><th>Misses</th><th>Hit rate</th><th>Miss rate</th>
    </tr></thead><tbody>{rows.map(row => {
      const hits = BigInt(row.hits), measured = BigInt(row.measuredMisses ?? row.misses), total = hits + measured;
      return <tr key={JSON.stringify([row.label, row.clientVersion])}><td>{row.label}</td><td className="client-version">{row.clientVersion ?? 'All'}</td><td>{number(hits)}</td><td>{number(row.misses)}</td>
        <td>{rate(hits, total)}</td><td>{rate(measured, total)}</td></tr>;
    })}</tbody></table></div> : <p>No phones have reported Following checks for the selected dates.</p>}
  </section>;
}

function StorageControls({ settings, saving, save }) {
  const [capacity, setCapacity] = useState(String(settings.capacity));
  const [hours, setHours] = useState(String(settings.retentionHours));
  const storage = { capacity: Number(capacity), retentionHours: Number(hours) };
  const dirty = storage.capacity !== settings.capacity || storage.retentionHours !== settings.retentionHours;
  return <div>
    <div className="form-grid">
      <InputField label="Cache capacity (apps)" help="500 to 2,000 apps. Default 1,000." type="number" min={500} max={2000} step={1}
        value={capacity} onChange={event => setCapacity(event.target.value)} />
      <InputField label="Retention (hours)" help="6 to 72 hours from the last sighting. Default 24." type="number" min={6} max={72} step={1}
        value={hours} onChange={event => setHours(event.target.value)} />
    </div>
    <p className="muted">The newest 500 apps keep their slots. Recent requests determine which other apps stay.</p>
    <Button disabled={saving || !dirty || !validStorage(storage)} onClick={() => save({ storage })}>Save cache policy</Button>
  </div>;
}

export default function FollowingCache({ title }) {
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
    setReport(current => current?.from === range.from && current?.to === range.to ? current : null);
    setError(''); setStatus('');
    api('/api/following-cache?' + new URLSearchParams(range), { signal: controller.signal })
      .then(setReport).catch(err => { if (!controller.signal.aborted) setError(err.message); });
    return () => controller.abort();
  }, [range]);

  async function changeSetting(change) {
    setSaving(true); setStatus('');
    try {
      const setting = await api('/api/following-cache', { method: 'PUT', body: { revision: report.pullToRefresh.revision, ...change } });
      setReport(current => ({ ...current, pullToRefresh: setting, storage: change.storage ?? current.storage }));
      setStatus('Saved.');
    } catch (err) { setStatus(err.message); }
    finally { setSaving(false); }
  }
  return <div className="admin-dashboard usage-page following-report">
    <header className="admin-heading"><div><h1>{title}</h1><p>Track update checks and cached app versions.</p></div>
      <div inert={saving} ref={calendar} dangerouslySetInnerHTML={calendarHtml} />
    </header>
    <Button disabled={saving} onClick={() => setRange({ ...range })}>Refresh report</Button>
    {error ? <Notice variant="danger">{error}</Notice> : !report ? <p role="status">Loading report…</p> :
      !report.available ? <p>Available with stable v0.1.24 or later.</p> : <>
        <LookupSummary report={report} />
        {report.canEditStorage && <CacheCard title="Cache storage" subtitle={`${number(report.storage.capacity)} apps · ${report.storage.retentionHours} hours`}>
          <StorageControls key={JSON.stringify(report.storage)} settings={report.storage} saving={saving} save={changeSetting} />
        </CacheCard>}
        <div role="status" aria-live="polite">{saving ? 'Saving…' : status}</div>
        <CacheSearch apps={report.catalog} />
        <RecentApps apps={report.recentApps} />
        <Rankings rows={report.followRankings} title="Most followed apps" rank="followRank" count="followers" displayCount="totalLookups" label="Total lookups" />
        <Rankings rows={report.followRankings} title="Most missed apps" rank="missRank" count="misses" label="Missing lookups" />
        <DeviceReports report={report} />
        <section className="admin-panel"><h2>Following controls</h2>
          <Switch label="Allow pull-to-refresh" disabled={saving} checked={report.pullToRefresh.enabled} onChange={event => changeSetting({ enabled: event.target.checked })} />
          <p>Allow users to pull down on Following to check for updates.</p>

        </section>
      </>}
  </div>;
}
