import { groupUsage, percentage, releaseAdoption, versionColor } from '../public/metrics.js';
import { compareVersions } from '../public/audience.js';
import { cacheLabels, groupRequestTraffic, requestTrafficMinimumVersion } from '../public/request-traffic.js';
import { mountUsageCalendar, offsetDay } from './usage-calendar';
import { mountUsageTable } from './usage-table.js';
import './usage.css';
export function mountUsage(root, onExpired) {
const $ = id => id === 'dashboard' ? root : root.querySelector('#' + id);
const number = value => new Intl.NumberFormat().format(BigInt(value));
const dateLabel = day => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(day + 'T00:00:00Z'));
const today = () => new Date().toISOString().slice(0, 10);
let data = null, controller, selectedDay, rangeView = false;
let deviceGroup = 'models', showAllDevices = false;
let chartVersions = [];
const shareLabel = value => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value) + '%';
function text(tag, className, value) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = value;
  return node;
}
function notice(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
const countColumn = (label, value) => ({ label, value, display: row => number(value(row)) });
function nameWithDetail(label, detail) {
  const cell = text('span', 'admin-device-name', '');
  cell.append(text('strong', '', label));
  if (detail) cell.append(text('span', 'admin-device-meta', detail));
  return cell;
}
const devicesTable = mountUsageTable($('device-shares'), [
  { label: 'Model', text: true, value: r => r.label, display: r => nameWithDetail(r.label, r.detail) },
  countColumn('Clients', r => r.devices),
  { label: 'Share', value: r => r.devices, display: r => shareLabel(r.share) },
], 1);
const detailsTable = mountUsageTable($('devices'), [
  { label: 'Device', text: true, value: r => r.model, display: r => nameWithDetail(r.model, `${r.manufacturer} · Android API ${r.androidApi}`) },
  { label: 'App version', value: r => r.versionCode, display: r => `v${r.appVersion} · ${r.versionCode}` },
  countColumn('Clients', r => BigInt(r.devices)),
  { label: 'Share', value: r => BigInt(r.devices), display: r => shareLabel(r.share) },
], 2);
const avgTime = r => r.requests ? r.durationMs / r.requests : 0n;
const trafficTable = mountUsageTable($('traffic-table'), [
  { label: 'Endpoint', text: true, value: r => `${r.method} ${r.endpoint}` },
  countColumn('Requests', r => r.requests),
  ...['Success', 'Redirects', 'Other 4xx', '429', '5xx', 'Network errors'].map((label, i) => countColumn(label, r => r.counts[i])),
  { label: 'Average time', value: avgTime, display: r => `${number(avgTime(r))} ms` },
], 1);
const cacheTable = mountUsageTable($('cache-table'), [
  { label: 'Cache', text: true, value: r => cacheLabels[r.cache] },
  countColumn('Hits', r => BigInt(r.hits)), countColumn('Misses', r => BigInt(r.misses)),
  { label: 'Hit rate', value: r => r.hitRate, display: r => shareLabel(r.hitRate) },
], 1);
const calendar = mountUsageCalendar(root, today, loadReport);
async function loadReport(selection) {
  controller?.abort();
  const request = new AbortController(); controller = request;
  $('loading').hidden = false; $('report').hidden = true; $('dashboard').setAttribute('aria-busy', 'true'); notice('dashboard-error');
  const to = selection.to, aggregate = selection.from !== to;
  const earliest = offsetDay(today(), -364);
  const trendStart = offsetDay(to, -13);
  const from = aggregate ? selection.from : trendStart < earliest ? earliest : trendStart;
  try {
    const response = await fetch(`/api/stats?from=${from}&to=${to}${aggregate ? '&group=period' : ''}`, { credentials: 'same-origin', cache: 'no-store', signal: request.signal });
    if (response.status === 401) { data = null; request.abort(); onExpired(); return; }
    if (!response.ok) throw new Error('Activity is unavailable. Try refreshing.');
    const report = await response.json();
    if (request.signal.aborted) return;
    data = report;
    rangeView = aggregate;
    renderReport(); $('report').hidden = false;
  } catch (error) { if (!request.signal.aborted) notice('dashboard-error', error.message); }
  finally { if (!request.signal.aborted) { $('loading').hidden = true; $('dashboard').setAttribute('aria-busy', 'false'); } }
}
function renderReport() {
  const peak = data.daily.reduce((max, day) => BigInt(day.devices) > max ? BigInt(day.devices) : max, 0n);
  $('peak').textContent = number(peak); $('zero-note').hidden = peak !== 0n;
  $('chart').style.gridTemplateColumns = `repeat(${data.daily.length}, minmax(0, 1fr))`;
  $('chart').style.setProperty('--bar-gap', `${18 / data.daily.length}%`);
  chartVersions = [...new Set(data.daily.flatMap(day => day.versions.map(v => v.appVersion)))]
    .sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
  $('chart').replaceChildren(...data.daily.map(day => {
    const column = text('div', 'admin-chart-column', '');
    const track = text('div', 'admin-chart-track', '');
    const bar = text('span', 'admin-chart-bar', '');
    bar.style.height = `${peak ? Number(day.devices) / Number(peak) * 100 : 0}%`;
    const counts = new Map(day.versions.map(v => [v.appVersion, v.devices]));
    for (const version of chartVersions) {
      if (!counts.has(version)) continue;
      const segment = document.createElement('span');
      segment.style.backgroundColor = versionColor(version);
      segment.style.flexGrow = String(Number(counts.get(version)) / Number(day.devices));
      segment.title = `${dateLabel(day.day)} · v${version} · ${number(counts.get(version))} clients`;
      bar.append(segment);
    }
    track.append(bar); column.append(track); return column;
  }));
  $('range-start').textContent = dateLabel(data.from); $('range-end').textContent = dateLabel(data.to);
  $('chart-slider').max = data.daily.length - 1; $('chart-slider').value = data.daily.length - 1;
  $('view-day').hidden = !rangeView;
  $('chart-slider').disabled = data.daily.length === 1;
  selectDay();
  if (rangeView) renderSnapshot();
}
function countLabel(count) {
  const unit = rangeView ? 'report' : 'client';
  return `${number(count)} ${unit}${count === 1n ? '' : 's'}`;
}
function renderShares(id, groups) {
  $(id).replaceChildren(...groups.map(group => {
    const item = text('div', 'admin-share', '');
    const heading = text('div', 'admin-share-heading', '');
    const label = text('div', 'admin-share-label', '');
    label.append(text('strong', '', group.label));
    if (group.detail) label.append(text('span', 'admin-device-meta', group.detail));
    const stats = text('div', 'admin-share-stats', '');
    stats.append(text('strong', '', shareLabel(group.share)), text('span', 'admin-device-meta', countLabel(group.devices)));
    heading.append(label, stats);
    const meter = text('div', 'admin-share-track', '');
    meter.setAttribute('aria-hidden', 'true');
    const fill = text('span', '', ''); fill.style.width = `${group.share}%`; meter.append(fill);
    item.append(heading, meter); return item;
  }));
  if (!groups.length) $(id).append(text('p', 'admin-zero-note', 'No reports for this period.'));
}
function renderSnapshot() {
  if (!data || !selectedDay) return;
  const from = rangeView ? data.from : selectedDay, to = rangeView ? data.to : selectedDay;
  const periodLabel = rangeView ? `${dateLabel(from)} to ${dateLabel(to)}` : dateLabel(selectedDay);
  const rows = data.breakdown.filter(row => rangeView || row.day === selectedDay)
    .sort((a, b) => BigInt(a.devices) === BigInt(b.devices) ? 0 : BigInt(a.devices) > BigInt(b.devices) ? -1 : 1);
  const total = rows.reduce((sum, row) => sum + BigInt(row.devices), 0n);
  const versions = groupUsage(rows, 'versions'), models = groupUsage(rows, 'models');
  $('total').textContent = number(total);
  $('total-label').textContent = rangeView ? 'Daily reports in range' : 'Reporting clients';
  if (rangeView) {
    const peak = data.daily.reduce((max, row) => BigInt(row.devices) > max ? BigInt(row.devices) : max, 0n);
    const bounds = peak === total ? number(total) : `${number(peak)} to ${number(total)}`;
    $('client-count-note').textContent = `${bounds} possible distinct ${total === 1n ? 'client' : 'clients'}. Anonymous IDs change daily.`;
  } else {
    $('client-count-note').textContent = 'Each installation reports its version on first activity that day.';
  }
  const adoption = releaseAdoption(versions, data.latestStable);
  for (const key of ['latest', 'older']) {
    $(key + '-share').textContent = adoption && total > 0n ? shareLabel(percentage(adoption[key], total)) : 'N/A';
    const version = key === 'latest' ? `on v${data.latestStable}` : `below v${data.latestStable}`;
    $(key + '-count').textContent = adoption ? `${countLabel(adoption[key])} ${version}` : 'Latest stable release unavailable.';
  }
  const other = adoption?.other ? ` · ${countLabel(adoption.other)} on newer or unrecognized versions` : '';
  $('snapshot-date').textContent = `${periodLabel} · UTC${other}`;
  $('device-date').textContent = periodLabel;
  const unit = rangeView ? 'Reports' : 'Clients';
  detailsTable.setLabel(2, unit);
  devicesTable.setLabel(1, unit);
  for (const node of root.querySelectorAll('.share-basis')) node.textContent = rangeView ? 'Share of reports' : 'Share of reporting clients';
  renderShares('version-shares', versions);
  renderShares('android-shares', groupUsage(rows, 'android'));
  const devices = deviceGroup === 'models' ? models : groupUsage(rows, 'brands');
  devicesTable.setLabel(0, deviceGroup === 'models' ? 'Model' : 'Manufacturer');
  devicesTable.setRows(devices, showAllDevices ? undefined : 8);
  $('device-shares-empty').hidden = devices.length > 0;
  $('show-devices').hidden = devices.length <= 8;
  $('show-devices').textContent = showAllDevices ? 'Show fewer' : `Show all ${devices.length} ${deviceGroup === 'models' ? 'models' : 'manufacturers'}`;
  $('show-devices').setAttribute('aria-expanded', String(showAllDevices));
  detailsTable.setRows(rows.map(row => ({ ...row, share: percentage(row.devices, total) })));
  $('empty').hidden = rows.length > 0;
  $('request-traffic').hidden = !data.trafficAvailable;
  $('cache-usage').hidden = !data.trafficAvailable;
  if (data.trafficAvailable) {
    const caches = (data.cacheUsage || []).filter(entry => rangeView || entry.day === selectedDay);
    cacheTable.setRows(caches.map(entry => ({ ...entry, hitRate: percentage(entry.hits, BigInt(entry.hits) + BigInt(entry.misses)) })));
    $('cache-empty').hidden = caches.length > 0;
    const groups = groupRequestTraffic(data.requestTraffic, rangeView ? undefined : selectedDay);
    const requests = groups.reduce((sum, group) => sum + group.requests, 0n);
    const capable = versions.reduce((sum, version) => {
      try { return compareVersions(version.key, requestTrafficMinimumVersion) >= 0 ? sum + version.devices : sum; }
      catch { return sum; }
    }, 0n);
    $('traffic-version').textContent = rangeView
      ? `${number(capable)} of ${countLabel(total)} came from v${requestTrafficMinimumVersion} or later.`
      : `${number(capable)} of ${countLabel(total)} reported v${requestTrafficMinimumVersion} or later.`;
    const pending = from === today();
    $('traffic-summary').textContent = pending ? 'Today’s request totals start arriving after midnight UTC.'
      : `${number(requests)} reported requests · ${periodLabel} UTC`;
    trafficTable.setRows(groups);
    $('traffic-empty').hidden = groups.length > 0 || pending;
  }
}
function selectDay() {
  if (!data) return;
  const selected = Number($('chart-slider').value), point = data.daily[selected];
  const unit = point.devices === '1' ? 'reporting client' : 'reporting clients';
  $('chart-total').textContent = number(point.devices);
  $('chart-unit').textContent = unit;
  $('chart-day').textContent = dateLabel(point.day) + (point.day === today() ? ' · Today so far (UTC)' : '');
  const counts = new Map(point.versions.map(v => [v.appVersion, v.devices]));
  $('chart-versions').replaceChildren(...chartVersions.map(version => {
    const item = document.createElement('li');
    const swatch = text('span', 'version-share-dot', '');
    swatch.style.backgroundColor = versionColor(version);
    item.append(swatch, `v${version} `, text('strong', '', number(counts.get(version) ?? 0)));
    return item;
  }));
  $('chart-slider').setAttribute('aria-valuetext', `${dateLabel(point.day)}, ${number(point.devices)} ${unit}`);
  [...$('chart').children].forEach((node, index) => node.classList.toggle('is-current', index === selected));
  selectedDay = point.day;
  if (!rangeView) renderSnapshot();
}
function exploreDay() {
  selectDay();
  if (!rangeView) calendar.setRange(selectedDay, selectedDay);
}
$('chart-slider').addEventListener('input', exploreDay);
$('chart').addEventListener('click', event => {
  const column = event.target.closest('.admin-chart-column');
  if (!column) return;
  $('chart-slider').value = [...$('chart').children].indexOf(column);
  exploreDay();
});
$('view-day').addEventListener('click', () => {
  calendar.setRange(selectedDay, selectedDay);
  loadReport({ from: selectedDay, to: selectedDay });
});
$('device-groups').addEventListener('click', event => {
  const button = event.target.closest('button[data-group]');
  if (!button || button.dataset.group === deviceGroup) return;
  deviceGroup = button.dataset.group; showAllDevices = false;
  for (const item of $('device-groups').children) item.setAttribute('aria-pressed', String(item === button));
  renderSnapshot();
});
$('show-devices').addEventListener('click', () => { showAllDevices = !showAllDevices; renderSnapshot(); });
const initialDay = today();
loadReport({ from: initialDay, to: initialDay });
return () => controller?.abort();
}
