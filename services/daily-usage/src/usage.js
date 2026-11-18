import { groupUsage, percentage } from '../public/metrics.js';
import { groupRequestTraffic, requestTrafficMinimumVersion } from '../public/request-traffic.js';
import './usage.css';
export function mountUsage(root, onExpired) {
const $ = id => id === 'dashboard' ? root : root.querySelector('#' + id);
const number = value => new Intl.NumberFormat().format(BigInt(value));
const dateLabel = day => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(day + 'T00:00:00Z'));
const today = () => new Date().toISOString().slice(0, 10);
let days = 14, data = null, controller, selectedDay;
let deviceGroup = 'models', showAllDevices = false;
const shareLabel = value => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value) + '%';
function text(tag, className, value) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = value;
  return node;
}
function notice(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
$('day').value = $('day').max = today();
$('filters').addEventListener('submit', event => { event.preventDefault(); loadReport(); });
$('day').addEventListener('change', () => { if ($('day').validity.valid) loadReport(); });
$('ranges').addEventListener('click', event => {
  const button = event.target.closest('button[data-days]');
  if (!button || Number(button.dataset.days) === days) return;
  days = Number(button.dataset.days);
  for (const item of $('ranges').children) item.setAttribute('aria-pressed', String(item === button));
  loadReport();
});
async function loadReport() {
  controller?.abort();
  const request = new AbortController(); controller = request;
  $('loading').hidden = false; $('report').hidden = true; $('refresh').disabled = true; $('dashboard').setAttribute('aria-busy', 'true'); notice('dashboard-error');
  const to = $('day').value || today();
  const from = new Date(new Date(`${to}T00:00:00Z`).getTime() - (days - 1) * 86400000).toISOString().slice(0, 10);
  try {
    const response = await fetch(`/api/stats?from=${from}&to=${to}`, { credentials: 'same-origin', cache: 'no-store', signal: request.signal });
    if (response.status === 401) { data = null; request.abort(); onExpired(); return; }
    if (!response.ok) throw new Error('Activity is unavailable. Try refreshing.');
    const report = await response.json();
    if (request.signal.aborted) return;
    data = report; renderReport(); $('report').hidden = false;
  } catch (error) { if (!request.signal.aborted) notice('dashboard-error', error.message); }
  finally { if (!request.signal.aborted) { $('loading').hidden = true; $('refresh').disabled = false; $('dashboard').setAttribute('aria-busy', 'false'); } }
}
function renderReport() {
  const peak = data.daily.reduce((max, day) => BigInt(day.devices) > max ? BigInt(day.devices) : max, 0n);
  $('peak').textContent = number(peak); $('zero-note').hidden = peak !== 0n;
  $('chart').style.gridTemplateColumns = `repeat(${data.daily.length}, minmax(0, 1fr))`;
  $('chart').replaceChildren(...data.daily.map(day => {
    const column = text('div', 'admin-chart-column', '');
    const track = text('div', 'admin-chart-track', '');
    const bar = text('span', 'admin-chart-bar', '');
    bar.style.height = `${peak ? Math.max(BigInt(day.devices) > 0n ? 3 : 0, Number(BigInt(day.devices) * 10000n / peak) / 100) : 0}%`;
    track.append(bar); column.append(track); return column;
  }));
  $('range-start').textContent = dateLabel(data.from); $('range-end').textContent = dateLabel(data.to);
  $('chart-slider').max = data.daily.length - 1; $('chart-slider').value = data.daily.length - 1;
  selectDay();
}
function renderShares(id, groups) {
  $(id).replaceChildren(...groups.map(group => {
    const item = text('div', 'admin-share', '');
    const heading = text('div', 'admin-share-heading', '');
    const label = text('div', 'admin-share-label', '');
    label.append(text('strong', '', group.label));
    if (group.detail) label.append(text('span', 'admin-device-meta', group.detail));
    const stats = text('div', 'admin-share-stats', '');
    stats.append(text('strong', '', shareLabel(group.share)), text('span', 'admin-device-meta', `${number(group.devices)} ${group.devices === 1n ? 'install' : 'installs'}`));
    heading.append(label, stats);
    const meter = text('div', 'admin-share-track', '');
    meter.setAttribute('aria-hidden', 'true');
    const fill = text('span', '', ''); fill.style.width = `${group.share}%`; meter.append(fill);
    item.append(heading, meter); return item;
  }));
  if (!groups.length) $(id).append(text('p', 'admin-zero-note', 'No reports for this date.'));
}
function renderSnapshot() {
  if (!data || !selectedDay) return;
  const rows = data.breakdown.filter(row => row.day === selectedDay).sort((a, b) => BigInt(a.devices) === BigInt(b.devices) ? 0 : BigInt(a.devices) > BigInt(b.devices) ? -1 : 1);
  const total = rows.reduce((sum, row) => sum + BigInt(row.devices), 0n);
  const versions = groupUsage(rows, 'versions'), models = groupUsage(rows, 'models');
  $('total').textContent = number(total);
  $('versions').textContent = number(versions.length);
  $('models').textContent = number(models.length);
  $('snapshot-date').textContent = `Snapshot for ${dateLabel(selectedDay)} · UTC`;
  $('device-date').textContent = dateLabel(selectedDay);
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
  if (data.trafficAvailable) {
    const groups = groupRequestTraffic(data.requestTraffic, selectedDay);
    const requests = groups.reduce((sum, group) => sum + group.requests, 0n);
    $('traffic-version').textContent = `Requires v${requestTrafficMinimumVersion} or later`;
    $('traffic-summary').textContent = `${number(requests)} requests · ${dateLabel(selectedDay)} UTC`;
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
  $('chart-slider').setAttribute('aria-valuetext', `${dateLabel(point.day)}: ${number(point.devices)} active installs`);
  [...$('chart').children].forEach((node, index) => node.classList.toggle('is-current', index === selected));
  selectedDay = point.day;
  renderSnapshot();
}
$('chart-slider').addEventListener('input', selectDay);
$('device-groups').addEventListener('click', event => {
  const button = event.target.closest('button[data-group]');
  if (!button || button.dataset.group === deviceGroup) return;
  deviceGroup = button.dataset.group; showAllDevices = false;
  for (const item of $('device-groups').children) item.setAttribute('aria-pressed', String(item === button));
  renderSnapshot();
});
$('show-devices').addEventListener('click', () => { showAllDevices = !showAllDevices; renderSnapshot(); });
loadReport();
return () => controller?.abort();
}
