import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';
import { createPortal } from 'react-dom';
import AHFUButton from './AHFUButton.tsx';
import AHFUDropdownSelector from './AHFUDropdownSelector.tsx';
import AHFUTextInput from './AHFUTextInput.tsx';
import AHFUIconButton from './AHFUIconButton.tsx';
import AHFUIcon from './AHFUIcon.tsx';
import { useFloatingPosition } from './hooks/useFloatingPosition.ts';
import { usePopupMotion } from './hooks/usePopupMotion.ts';
import { zIndex } from './tokens.ts';
import './responsive.css';

export interface AHFUDateTimePickerProps {
  label: string;
  mode?: 'date' | 'time' | 'datetime-local';
  /** Local ISO date/time string. No implicit UTC or timezone conversion. */
  value?: string;
  defaultValue?: string;
  onChange?: (value: string) => void;
  min?: string; max?: string; step?: number;
  name?: string; id?: string; required?: boolean; disabled?: boolean; readOnly?: boolean;
  clearable?: boolean; helperText?: string; errorText?: string; style?: CSSProperties;
}
const pad = (value: number) => String(value).padStart(2, '0');
const dateString = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
const localDate = (value: string) => new Date(`${value}T12:00:00`);
const timeSeconds = (value: string) => value.split(':').reduce((sum, part, index) => sum + Number(part) * [3600, 60, 1][index], 0);
const timeString = (value: number, seconds: boolean) => {
  const remainder = Number((value % 60).toFixed(3));
  const fraction = String(remainder).split('.')[1];
  return `${pad(Math.floor(value / 3600))}:${pad(Math.floor(value % 3600 / 60))}${seconds ? `:${pad(Math.floor(remainder))}${fraction ? `.${fraction}` : ''}` : ''}`;
};
const months = Array.from({ length: 12 }, (_, month) => ({ value: String(month), label: new Date(2026, month, 1).toLocaleString('en', { month: 'long' }) }));

export default function AHFUDateTimePicker({
  label, mode = 'datetime-local', value, defaultValue = '', onChange,
  min, max, step, name, id, required, disabled, readOnly, clearable = true,
  helperText, errorText, style,
}: AHFUDateTimePickerProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const [internalValue, setInternalValue] = useState(defaultValue);
  const current = value ?? internalValue;
  const [draftDate, setDraftDate] = useState(dateString(new Date()));
  const [draftTime, setDraftTime] = useState('09:00');
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [focusedDay, setFocusedDay] = useState('');
  const anchor = useRef<HTMLDivElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const validation = useRef<HTMLInputElement>(null);
  const pendingFocus = useRef<string | null>(null);
  const { open, mounted, shown, show, hide } = usePopupMotion();
  const position = useFloatingPosition(mounted, anchor, popup, 580, 360, true);
  const hasDate = mode !== 'time';
  const hasTime = mode !== 'date';
  const interval = step && step > 0 ? step * (mode === 'date' ? 86400 : 1) : mode === 'date' ? 86400 : 60;
  const scalar = (text: string) => mode === 'time' ? timeSeconds(text) : Date.parse(`${text}${mode === 'date' ? 'T00:00' : ''}Z`) / 1000;
  const lower = min ? scalar(min) : -Infinity;
  const upper = max ? scalar(max) : Infinity;
  const base = min ? lower : defaultValue ? scalar(defaultValue) : 0;
  const seconds = interval % 60 !== 0 || (min?.split(':')[2] !== undefined && Number(min.split(':')[2]) !== 0) || draftTime.split(':')[2] !== undefined;
  const change = (next: string) => { if (value === undefined) setInternalValue(next); onChange?.(next); };
  const restoreFocus = () => document.getElementById(inputId)?.focus();
  const close = () => { hide(); restoreFocus(); };
  const valid = (candidate: string) => {
    if (!candidate) return !required;
    const number = scalar(candidate);
    const inRange = mode === 'time' && lower > upper ? number >= lower || number <= upper : number >= lower && number <= upper;
    return Number.isFinite(number) && inRange && Math.abs((number - base) / interval - Math.round((number - base) / interval)) < .00001;
  };
  const dayAllowed = (date: string) => (!min || date >= min.slice(0, 10)) && (!max || date <= max.slice(0, 10)) && (mode !== 'date' || valid(date));
  const dayBase = mode === 'time' ? 0 : Date.parse(`${draftDate}T00:00Z`) / 1000;
  // Find a valid step within a given hour/minute. No large list of time ticks.
  const slot = (hour: number, minute?: number, second?: number) => {
    const start = dayBase + hour * 3600 + (minute ?? 0) * 60 + (second ?? 0);
    const end = start + (second !== undefined ? 1 : minute !== undefined ? 60 : 3600) - .001;
    const ranges = mode === 'time' && lower > upper ? [[lower, 86400 - .001], [0, upper]] : [[lower, upper]];
    for (const [lo, hi] of ranges) {
      const first = base + Math.ceil((Math.max(start, lo) - base - .000001) / interval) * interval;
      if (first <= Math.min(end, hi)) return Math.max(0, first - dayBase);
    }
    return null;
  };
  const hour = Number(draftTime.split(':')[0]);
  const minute = Number(draftTime.split(':')[1]);
  const second = Math.floor(Number(draftTime.split(':')[2] ?? 0));
  const candidate = mode === 'date' ? draftDate : mode === 'time' ? draftTime : `${draftDate}T${draftTime}`;
  const begin = () => {
    if (disabled || readOnly) return;
    let date = hasDate && current ? current.slice(0, 10) : dateString(new Date());
    if (hasDate && min && date < min.slice(0, 10)) date = min.slice(0, 10);
    if (hasDate && max && date > max.slice(0, 10)) date = max.slice(0, 10);
    setDraftDate(date); setFocusedDay(date);
    setDraftTime((mode === 'time' ? current : current.split('T')[1]) || (min?.includes(':') ? min.slice(mode === 'time' ? 0 : 11) : '09:00'));
    const parsed = localDate(date);
    setMonth(new Date(parsed.getFullYear(), parsed.getMonth(), 1));
    show();
  };
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      const target = event.target as HTMLElement;
      if (!anchor.current?.contains(target) && !popup.current?.contains(target) && target.closest('[data-ahfu-popup-group]')?.getAttribute('data-ahfu-popup-group') !== inputId) hide();
    };
    document.addEventListener('pointerdown', outside);
    (popup.current?.querySelector<HTMLElement>('[data-day][tabindex="0"]') ?? popup.current?.querySelector<HTMLElement>('button:not(:disabled)'))?.focus({ preventScroll: true });
    return () => document.removeEventListener('pointerdown', outside);
  }, [open, inputId, hide]);
  useLayoutEffect(() => {
    if (!pendingFocus.current) return;
    popup.current?.querySelector<HTMLElement>(`[data-day="${pendingFocus.current}"]`)?.focus({ preventScroll: true });
    pendingFocus.current = null;
  }, [focusedDay, month]);
  const moveMonth = (delta: number) => setMonth(new Date(month.getFullYear(), month.getMonth() + delta, 1));
  const onDayKey = (event: KeyboardEvent, date: string) => {
    const next = localDate(date);
    if (event.key === 'ArrowLeft') next.setDate(next.getDate() - 1);
    else if (event.key === 'ArrowRight') next.setDate(next.getDate() + 1);
    else if (event.key === 'ArrowUp') next.setDate(next.getDate() - 7);
    else if (event.key === 'ArrowDown') next.setDate(next.getDate() + 7);
    else if (event.key === 'Home') next.setDate(next.getDate() - next.getDay());
    else if (event.key === 'End') next.setDate(next.getDate() + 6 - next.getDay());
    else if (event.key === 'PageUp' || event.key === 'PageDown') { const day = next.getDate(); next.setDate(1); next.setMonth(next.getMonth() + (event.key === 'PageUp' ? -1 : 1) * (event.shiftKey ? 12 : 1)); next.setDate(Math.min(day, new Date(next.getFullYear(), next.getMonth() + 1, 0).getDate())); }
    else return;
    event.preventDefault();
    const key = dateString(next);
    if (!dayAllowed(key)) return;
    pendingFocus.current = key;
    setFocusedDay(key); setMonth(new Date(next.getFullYear(), next.getMonth(), 1));
  };
  const first = new Date(month.getFullYear(), month.getMonth(), 1 - month.getDay());
  const dates = Array.from({ length: 42 }, (_, offset) => { const date = new Date(first); date.setDate(first.getDate() + offset); return date; });
  const focusDate = dates.some(date => dateString(date) === focusedDay && dayAllowed(focusedDay)) ? focusedDay : dateString(dates.find(date => date.getMonth() === month.getMonth() && dayAllowed(dateString(date))) ?? month);
  const firstYear = min && hasDate ? Number(min.slice(0, 4)) : Math.min(1900, month.getFullYear() - 100);
  const lastYear = max && hasDate ? Number(max.slice(0, 4)) : Math.max(2100, month.getFullYear() + 100);
  const display = !current ? '' : mode === 'time' ? current : `${localDate(current.slice(0, 10)).toLocaleDateString('en', { month: 'short', day: 'numeric', year: 'numeric' })}${hasTime ? ` · ${current.split('T')[1] ?? ''}` : ''}`;
  const feedback = errorText ?? helperText;

  return <div data-ahfu-field className="ahfu-field ahfu-date-time-picker" style={style}>
    <label className="ahfu-field-label" htmlFor={inputId}>{label}{required && ' *'}</label>
    <div className="ahfu-control-row">
      <div ref={anchor} className="ahfu-picker-input">
        <AHFUTextInput id={inputId} fullWidth readOnly disabled={disabled} value={display}
          placeholder={mode === 'time' ? 'Choose a time' : 'Choose a date'} className="ahfu-picker-trigger"
          role="combobox" aria-haspopup="dialog" aria-expanded={open} aria-controls={mounted ? `${inputId}-popup` : undefined}
          aria-readonly={readOnly} aria-required={required} aria-invalid={!!errorText} aria-describedby={feedback ? `${inputId}-feedback` : undefined}
          onClick={begin} onKeyDown={event => {
            if (['Enter', ' ', 'ArrowDown'].includes(event.key)) { event.preventDefault(); begin(); }
            if (event.key === 'Escape') close();
          }} />
        <AHFUIconButton type="button" size="md" aria-label={`Open ${label}`} disabled={disabled || readOnly} tabIndex={-1} onClick={begin} style={{ position: 'absolute', right: 4, top: 4 }}><AHFUIcon size={20}>{mode === 'time' ? 'schedule' : 'calendar_month'}</AHFUIcon></AHFUIconButton>
      </div>
      {clearable && <AHFUButton type="button" size="sm" aria-label={`Clear ${label}`} disabled={disabled || readOnly || !current} onClick={() => change('')}>Clear</AHFUButton>}
    </div>
    <input ref={validation} className="ahfu-validation-input" tabIndex={-1} aria-hidden="true" type={mode} name={name} value={current} onChange={() => {}}
      min={min} max={max} step={step} required={required} disabled={disabled} readOnly={readOnly}
      onInvalid={event => { event.preventDefault(); restoreFocus(); }} />
    {feedback && <span id={`${inputId}-feedback`} className="ahfu-field-help" role={errorText ? 'alert' : undefined} style={errorText ? { color: 'var(--danger)' } : undefined}>{feedback}</span>}
    {mounted && createPortal(<div ref={popup} id={`${inputId}-popup`} role="dialog" aria-label={`Choose ${label}`}
      className="ahfu-popup ahfu-picker-popup ahfu-motion-popup" data-shown={shown} aria-hidden={!open || undefined} inert={!open}
      style={{ ...position, position: 'fixed', zIndex: zIndex.modal + 1 }}
      onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(); }
        if (event.key === 'Tab') {
          const targets = Array.from(popup.current?.querySelectorAll<HTMLElement>('button:not(:disabled):not([tabindex="-1"]), input:not(:disabled), [tabindex="0"]') ?? []);
          if (event.shiftKey && document.activeElement === targets[0] || !event.shiftKey && document.activeElement === targets[targets.length - 1]) { event.preventDefault(); close(); }
        }
      }}>
      <div className="ahfu-picker-title"><span>{label}</span><AHFUIconButton type="button" size="sm" aria-label={`Close ${label}`} onClick={close}><AHFUIcon size={20}>close</AHFUIcon></AHFUIconButton></div>
      {hasDate && <>
        <div className="ahfu-calendar-heading">
          <AHFUIconButton type="button" size="sm" aria-label="Previous month" disabled={!!min && new Date(month.getFullYear(), month.getMonth(), 0) < localDate(min.slice(0, 10))} onClick={() => moveMonth(-1)}><AHFUIcon size={20}>chevron_left</AHFUIcon></AHFUIconButton>
          <AHFUDropdownSelector aria-label="Month" value={String(month.getMonth())} popupGroup={inputId} options={months.map(option => ({ ...option, disabled: (Number(option.value) < Number(min?.slice(5, 7)) - 1 && month.getFullYear() === Number(min?.slice(0, 4))) || (Number(option.value) > Number(max?.slice(5, 7)) - 1 && month.getFullYear() === Number(max?.slice(0, 4))) }))} onChange={next => setMonth(new Date(month.getFullYear(), Number(next), 1))} />
          <AHFUDropdownSelector aria-label="Year" value={String(month.getFullYear())} popupGroup={inputId} options={Array.from({ length: Math.max(0, lastYear - firstYear + 1) }, (_, index) => ({ value: String(firstYear + index), label: String(firstYear + index) }))} onChange={next => {
            const year = Number(next); const lowerMonth = year === firstYear && min ? Number(min.slice(5, 7)) - 1 : 0; const upperMonth = year === lastYear && max ? Number(max.slice(5, 7)) - 1 : 11;
            setMonth(new Date(year, Math.max(lowerMonth, Math.min(upperMonth, month.getMonth())), 1));
          }} />
          <AHFUIconButton type="button" size="sm" aria-label="Next month" disabled={!!max && new Date(month.getFullYear(), month.getMonth() + 1, 1) > localDate(max.slice(0, 10))} onClick={() => moveMonth(1)}><AHFUIcon size={20}>chevron_right</AHFUIcon></AHFUIconButton>
        </div>
        <div className="ahfu-calendar" role="grid" aria-label={month.toLocaleDateString('en', { month: 'long', year: 'numeric' })}>
          <div role="row" className="ahfu-calendar-weekdays">{['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'].map(day => <span key={day} role="columnheader">{day}</span>)}</div>
          {Array.from({ length: 6 }, (_, week) => <div role="row" className="ahfu-calendar-week" key={week}>{dates.slice(week * 7, week * 7 + 7).map(date => {
            const key = dateString(date); const selected = key === draftDate;
            return <AHFUButton key={key} type="button" role="gridcell" data-day={key} aria-label={date.toLocaleDateString('en', { month: 'long', day: 'numeric', year: 'numeric' })}
              aria-selected={selected} aria-current={key === dateString(new Date()) ? 'date' : undefined} tabIndex={key === focusDate ? 0 : -1}
              className="ahfu-calendar-day" disabled={!dayAllowed(key)} onKeyDown={event => onDayKey(event, key)}
              onClick={() => { setDraftDate(key); setFocusedDay(key); if (mode === 'date') { change(key); close(); } }}
              style={{ height: 42, minHeight: 42, minWidth: 0, width: '100%', padding: 0, background: selected ? 'var(--primary)' : 'transparent', color: selected ? 'var(--primary-foreground)' : date.getMonth() === month.getMonth() ? 'var(--text-primary)' : 'var(--text-muted)', boxShadow: 'none' }}>{date.getDate()}</AHFUButton>;
          })}</div>)}
        </div>
      </>}
      {hasTime && <div className="ahfu-picker-time">
        <AHFUDropdownSelector label="Hour" value={pad(hour)} fullWidth popupGroup={inputId} options={Array.from({ length: 24 }, (_, n) => ({ value: pad(n), label: pad(n), disabled: slot(n) === null }))} onChange={next => setDraftTime(timeString(slot(Number(next), minute) ?? slot(Number(next)) ?? 0, seconds))} />
        <AHFUDropdownSelector label="Minute" value={pad(minute)} fullWidth popupGroup={inputId} options={Array.from({ length: 60 }, (_, n) => ({ value: pad(n), label: pad(n), disabled: slot(hour, n) === null }))} onChange={next => setDraftTime(timeString(slot(hour, Number(next)) ?? 0, seconds))} />
        {seconds && <AHFUDropdownSelector label="Second" value={pad(second)} fullWidth popupGroup={inputId} options={Array.from({ length: 60 }, (_, n) => ({ value: pad(n), label: pad(n), disabled: slot(hour, minute, n) === null }))} onChange={next => setDraftTime(timeString(slot(hour, minute, Number(next)) ?? 0, true))} />}
      </div>}
      {hasTime && !valid(candidate) && <p className="ahfu-field-help" role="status">Choose an available time within the allowed range.</p>}
      <div className="ahfu-picker-footer">
        <AHFUButton type="button" size="sm" onClick={close}>Cancel</AHFUButton>
        {hasTime && <AHFUButton type="button" size="sm" variant="filled" disabled={!valid(candidate)} onClick={() => { change(candidate); close(); }}>Apply</AHFUButton>}
      </div>
    </div>, document.body)}
  </div>;
}
