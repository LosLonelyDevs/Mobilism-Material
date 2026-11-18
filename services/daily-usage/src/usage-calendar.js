const DAY = 86400000;
const iso = date => date.toISOString().slice(0, 10);
export const offsetDay = (day, days) => iso(new Date(Date.parse(day) + days * DAY));
const monthOf = day => day.slice(0, 7) + '-01';
const format = options => new Intl.DateTimeFormat(undefined, { ...options, timeZone: 'UTC' });
const shortDate = format({ year: 'numeric', month: 'short', day: 'numeric' });
const monthName = format({ year: 'numeric', month: 'long' });
function label({ from, to }) {
  return shortDate.formatRange(new Date(from), new Date(to));
}
function offsetMonth(day, direction) {
  const date = new Date(monthOf(day));
  date.setUTCMonth(date.getUTCMonth() + direction);
  return iso(date);
}

export function mountUsageCalendar(root, today, onApply) {
  const get = id => root.querySelector('#' + id);
  const dialog = get('date-calendar'), trigger = get('choose-dates'), grid = get('calendar-days');
  let applied = { from: today(), to: today() };
  let draft, month, cursor, awaitingEnd, min, max;
  function setRange(from, to) {
    applied = { from, to };
    trigger.textContent = label(applied);
  }
  setRange(applied.from, applied.to);

  function draw() {
    get('calendar-month').textContent = monthName.format(new Date(month));
    get('prev-month').disabled = month <= monthOf(min);
    get('next-month').disabled = month >= monthOf(max);
    const offset = (new Date(month).getUTCDay() + 6) % 7;
    const buttons = [];
    const nextMonth = offsetMonth(month, 1);
    for (let day = month; day < nextMonth; day = offsetDay(day, 1)) {
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.day = day;
      button.textContent = String(Number(day.slice(-2)));
      button.setAttribute('aria-label', shortDate.format(new Date(day)));
      const selected = day >= draft.from && day <= draft.to;
      button.setAttribute('aria-pressed', String(selected));
      if (day === draft.from || day === draft.to) button.classList.add('endpoint');
      if (day === max) button.setAttribute('aria-current', 'date');
      if (day === month) button.style.gridColumnStart = String(offset + 1);
      button.disabled = day < min || day > max;
      button.tabIndex = day === cursor ? 0 : -1;
      buttons.push(button);
    }
    grid.replaceChildren(...buttons);
    get('calendar-selection').textContent = awaitingEnd ? label(draft) + ' · Pick an end date or apply.' : label(draft);
  }
  function focusCursor() { grid.querySelector('[tabindex="0"]')?.focus(); }
  function move(day, focus = true) {
    cursor = day < min ? min : day > max ? max : day;
    month = monthOf(cursor);
    draw();
    if (focus) focusCursor();
  }
  function changeMonth(direction, focus = false) {
    const first = offsetMonth(cursor, direction);
    const last = offsetDay(offsetMonth(first, 1), -1);
    move(first.slice(0, 8) + (cursor.slice(-2) < last.slice(-2) ? cursor.slice(-2) : last.slice(-2)), focus);
  }
  trigger.onclick = () => {
    max = today(); min = offsetDay(max, -364);
    draft = { from: applied.from < min ? min : applied.from, to: applied.to < min ? min : applied.to };
    awaitingEnd = false;
    move(draft.to, false);
    dialog.showModal();
    focusCursor();
  };
  grid.onclick = event => {
    const button = event.target.closest('button[data-day]');
    if (!button || button.disabled) return;
    const day = button.dataset.day;
    if (awaitingEnd) draft = day < draft.from ? { from: day, to: draft.from } : { from: draft.from, to: day };
    else draft = { from: day, to: day };
    awaitingEnd = !awaitingEnd;
    move(day);
  };
  grid.onkeydown = event => {
    const offsets = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 };
    if (Object.hasOwn(offsets, event.key)) move(offsetDay(cursor, offsets[event.key]));
    else if (event.key === 'PageUp' || event.key === 'PageDown') changeMonth(event.key === 'PageUp' ? -1 : 1, true);
    else if (event.key === 'Home' || event.key === 'End') {
      const weekday = (new Date(cursor).getUTCDay() + 6) % 7;
      move(offsetDay(cursor, event.key === 'Home' ? -weekday : 6 - weekday));
    } else return;
    event.preventDefault();
  };
  get('prev-month').onclick = () => changeMonth(-1);
  get('next-month').onclick = () => changeMonth(1);
  get('cancel-dates').onclick = () => dialog.close();
  get('apply-dates').onclick = () => {
    setRange(draft.from, draft.to);
    dialog.close();
    onApply(applied);
  };
  return { setRange };
}
