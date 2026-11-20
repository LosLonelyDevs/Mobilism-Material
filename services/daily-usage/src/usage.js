import { groupUsage, percentage, releaseAdoption } from '../public/metrics.js';
import { cacheLabels, groupRequestTraffic, requestTrafficMinimumVersion } from '../public/request-traffic.js';
import { mountUsageCalendar, offsetDay } from './usage-calendar';
import './usage.css';
export function mountUsage(root, onExpired) {
const $ = id => id === 'dashboard' ? root : root.querySelector('#' + id);
const number = value => new Intl.NumberFormat().format(BigInt(value));
const dateLabel = day => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }).format(new Date(day + 'T00:00:00Z'));
const today = () => new Date().toISOString().slice(0, 10);
let data = null, controller, selectedDay, rangeView = false;
let deviceGroup = 'models', showAllDevices = false;
const shareLabel = value => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value) + '%';
function text(tag, className, value) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = value;
  return node;
}
function notice(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
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
  $('chart').replaceChildren(...data.daily.map(day => {
    const column = text('div', 'admin-chart-column', '');
    const track = text('div', 'admin-chart-track', '');
    const bar = text('span', 'admin-chart-bar', '');
    bar.style.height = `${peak ? Math.max(BigInt(day.devices) > 0n ? 3 : 0, Number(BigInt(day.devices) * 10000n / peak) / 100) : 0}%`;
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
  $('detail-unit').textContent = rangeView ? 'Reports' : 'Clients';
  for (const node of root.querySelectorAll('.share-basis')) node.textContent = rangeView ? 'Share of reports' : 'Share of reporting clients';
  renderShares('version-shares', versions);
  renderShares('android-shares', groupUsage(rows, 'android'));
  const devices = deviceGroup === 'models' ? models : groupUsage(rows, 'brands');
  renderShares('device-shares', showAllDevices ? devices : devices.slice(0, 8));
  $('show-devices').hidden = devices.length <= 8;
  $('show-devices').textContent = showAllDevices ? 'Show fewer' : `Show all ${devices.length} ${deviceGroup === 'models' ? 'models' : 'manufacturers'}`;
  $('show-devices').setAttribute('aria-expanded', String(showAllDevices));
  $('devices').replaceChildren(...rows.map(row => {
    const item = text('div', 'admin-device-row', '');
    const name = text('div', 'admin-device-name', '');
    name.append(text('strong', '', row.model), text('span', 'admin-device-meta', `${row.manufacturer} · Android API ${row.androidApi}`));
    item.append(name, text('span', 'admin-version', `v${row.appVersion} · ${row.versionCode}`), text('strong', 'admin-device-count', `${number(row.devices)} · ${shareLabel(percentage(row.devices, total))}`));
    return item;
  }));
  $('empty').hidden = rows.length > 0;
  $('request-traffic').hidden = !data.trafficAvailable;
  $('cache-usage').hidden = !data.trafficAvailable;
  $('follow-rankings').hidden = !data.trafficAvailable;
  if (data.trafficAvailable) {
    const rankings = (data.followRankings || []).filter(entry => rangeView || entry.day === selectedDay);
    $('follow-unit').textContent = rangeView ? 'Device-days' : 'Reporting devices';
    for (const [body, empty, rank, count] of [
      ['most-followed', 'follow-empty', 'followRank', 'followers'],
      ['most-missed', 'miss-empty', 'missRank', 'misses'],
    ]) {
      const top = rankings.filter(entry => entry[rank] <= 10 && BigInt(entry[count]) > 0n).sort((a, b) => a[rank] - b[rank]);
      $(body).replaceChildren(...top.map(entry => {
        const row = document.createElement('tr'), label = text('th', '', entry.name);
        label.scope = 'row';
        row.append(label, text('td', '', number(entry[count])));
        return row;
      }));
      $(empty).hidden = top.length > 0;
    }
    const caches = (data.cacheUsage || []).filter(entry => rangeView || entry.day === selectedDay);
    $('cache-rows').replaceChildren(...caches.map(entry => {
      const row = document.createElement('tr');
      const label = text('th', '', cacheLabels[entry.cache]); label.scope = 'row';
      const hits = BigInt(entry.hits), misses = BigInt(entry.misses);
      row.append(label, text('td', '', number(hits)), text('td', '', number(misses)),
        text('td', '', shareLabel(percentage(hits, hits + misses))));
      return row;
    }));
    $('cache-empty').hidden = caches.length > 0;
    const groups = groupRequestTraffic(data.requestTraffic, rangeView ? undefined : selectedDay);
    const requests = groups.reduce((sum, group) => sum + group.requests, 0n);
    $('traffic-version').textContent = `Requires v${requestTrafficMinimumVersion} or later`;
    $('traffic-summary').textContent = `${number(requests)} requests · ${periodLabel} UTC`;
    $('traffic-rows').replaceChildren(...groups.map(group => {
      const row = document.createElement('tr');
      const label = text('th', '', `${group.method} ${group.endpoint}`); label.scope = 'row';
      row.append(label, text('td', '', number(group.requests)));
      group.counts.forEach(count => row.append(text('td', '', number(count))));
      row.append(text('td', '', `${number(group.requests ? group.durationMs / group.requests : 0n)} ms`));
      return row;
    }));
    $('traffic-empty').hidden = groups.length > 0;
  }
}
function selectDay() {
  if (!data) return;
  const selected = Number($('chart-slider').value), point = data.daily[selected];
  $('chart-total').textContent = number(point.devices); $('chart-day').textContent = dateLabel(point.day);
  $('chart-slider').setAttribute('aria-valuetext', `${dateLabel(point.day)}, ${number(point.devices)} reporting clients`);
  [...$('chart').children].forEach((node, index) => node.classList.toggle('is-current', index === selected));
  selectedDay = point.day;
  if (!rangeView) renderSnapshot();
}
$('chart-slider').addEventListener('input', () => {
  selectDay();
  if (!rangeView) calendar.setRange(selectedDay, selectedDay);
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
