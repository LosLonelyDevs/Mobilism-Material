const $ = id => document.getElementById(id);
const number = value => new Intl.NumberFormat().format(BigInt(value));
const dateLabel = day => new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(`${day}T00:00:00Z`));
const today = () => new Date().toISOString().slice(0, 10);
let stage = 'password', busy = false, lockedUntil = 0, days = 14, data = null, controller;

function text(tag, className, value) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = value;
  return node;
}
function notice(id, message = '') { $(id).textContent = message; $(id).hidden = !message; }
function loginStage(next, focus = true) {
  stage = next === 'otp' ? 'otp' : 'password';
  $('login').hidden = false; $('dashboard').hidden = true; $('startup').hidden = true;
  $('password-fields').hidden = stage === 'otp'; $('otp-fields').hidden = stage !== 'otp';
  $('username').disabled = $('password').disabled = stage === 'otp'; $('code').disabled = stage !== 'otp';
  $('start-over').hidden = stage !== 'otp';
  $('login-title').textContent = stage === 'otp' ? 'Verify your sign-in' : 'Welcome back';
  $('login-subtitle').textContent = stage === 'otp' ? 'One more step to your dashboard.' : 'Sign in to view daily app activity.';
  $('login-step').textContent = stage === 'otp' ? 'Step 2 of 2' : 'Step 1 of 2';
  $('password').value = ''; $('code').value = '';
  updateLock();
  if (focus) $(stage === 'otp' ? 'code' : 'username').focus();
}
function updateLock() {
  const seconds = Math.max(0, Math.ceil((lockedUntil - Date.now()) / 1000));
  $('login-submit').disabled = busy || seconds > 0;
  $('login-submit').textContent = seconds ? `Try again in ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` :
    busy ? 'Checking…' : stage === 'otp' ? 'Verify & sign in' : 'Continue';
}
setInterval(updateLock, 1000);
async function session(method = 'GET', body) {
  const response = await fetch('/api/admin-session', { method, credentials: 'same-origin', cache: 'no-store',
    ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}) });
  const result = await response.json();
  if (result.retryAfter) lockedUntil = Date.now() + result.retryAfter * 1000;
  if (!response.ok) {
    if (result.stage) loginStage(result.stage);
    throw new Error(result.error || 'Sign-in unavailable. Try again shortly.');
  }
  return result;
}
async function openDashboard() {
  $('startup').hidden = $('login').hidden = true; $('dashboard').hidden = false;
  $('username').value = $('password').value = $('code').value = '';
  lockedUntil = 0; notice('login-error');
  await loadReport();
}
$('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (busy || lockedUntil > Date.now()) return;
  busy = true; updateLock(); notice('login-error');
  const body = stage === 'otp' ? { step: 'otp', code: $('code').value } : { step: 'password', username: $('username').value, password: $('password').value };
  try {
    const result = await session('POST', body);
    if (result.isAdmin) await openDashboard(); else loginStage(result.stage);
  } catch (error) { notice('login-error', error.message); $('password').value = ''; $('code').value = ''; }
  finally { busy = false; updateLock(); }
});
async function signOut() {
  try { await session('DELETE'); controller?.abort(); data = null; $('devices').replaceChildren(); $('report').hidden = true; notice('login-error'); loginStage('password'); }
  catch (error) { notice($('dashboard').hidden ? 'login-error' : 'dashboard-error', error.message); }
}
$('sign-out').addEventListener('click', signOut);
$('start-over').addEventListener('click', signOut);
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
    if (response.status === 401) { data = null; loginStage('password'); notice('login-error', 'Your session expired. Sign in again.'); return; }
    if (!response.ok) throw new Error('Activity is unavailable. Try refreshing.');
    const report = await response.json();
    if (request.signal.aborted) return;
    data = report; renderReport(); $('report').hidden = false;
  } catch (error) { if (!request.signal.aborted) notice('dashboard-error', error.message); }
  finally { if (!request.signal.aborted) { $('loading').hidden = true; $('refresh').disabled = false; $('dashboard').setAttribute('aria-busy', 'false'); } }
}
function renderReport() {
  const rows = data.breakdown.filter(row => row.day === data.to).sort((a, b) => BigInt(a.devices) === BigInt(b.devices) ? 0 : BigInt(a.devices) > BigInt(b.devices) ? -1 : 1);
  $('total').textContent = number(data.daily.at(-1)?.devices || 0);
  $('versions').textContent = number(new Set(rows.map(row => `${row.appVersion}:${row.versionCode}`)).size);
  $('models').textContent = number(new Set(rows.map(row => `${row.manufacturer}:${row.model}`)).size);
  $('device-date').textContent = dateLabel(data.to);
  $('devices').replaceChildren(...rows.map(row => {
    const item = text('div', 'admin-device-row', '');
    const name = text('div', 'admin-device-name', '');
    name.append(text('strong', '', row.model), text('span', 'admin-device-meta', `${row.manufacturer} · Android API ${row.androidApi}`));
    item.append(name, text('span', 'admin-version', `v${row.appVersion} · ${row.versionCode}`), text('strong', 'admin-device-count', number(row.devices)));
    return item;
  }));
  $('empty').hidden = rows.length > 0;
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
function selectDay() {
  if (!data) return;
  const selected = Number($('chart-slider').value), point = data.daily[selected];
  $('chart-total').textContent = number(point.devices); $('chart-day').textContent = dateLabel(point.day);
  $('chart-slider').setAttribute('aria-valuetext', `${dateLabel(point.day)}: ${number(point.devices)} active installs`);
  [...$('chart').children].forEach((node, index) => node.classList.toggle('is-current', index === selected));
}
$('chart-slider').addEventListener('input', selectDay);
session().then(result => result.isAdmin ? openDashboard() : loginStage(result.stage, false))
  .catch(error => { loginStage('password', false); notice('login-error', error.message); });
