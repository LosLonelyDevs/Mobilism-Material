import { useId, useRef, useEffect, type CSSProperties } from 'react';
import { createPortal } from 'react-dom';
import AHFUButton from './AHFUButton.tsx';
import AHFUIcon from './AHFUIcon.tsx';
import { useFloatingPosition } from './hooks/useFloatingPosition.ts';
import { usePopupMotion } from './hooks/usePopupMotion.ts';
import { zIndex } from './tokens.ts';
import './responsive.css';

export interface AHFUDropdownOption<T extends string = string> { value: T; label: string; disabled?: boolean }
export interface AHFUDropdownSelectorProps<T extends string = string> {
  options: AHFUDropdownOption<T>[];
  value: T | null;
  onChange: (value: T) => void;
  placeholder?: string;
  label?: string;
  'aria-label'?: string;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: CSSProperties;
  /** Associates nested portaled menus with their parent popup. */
  popupGroup?: string;
}

export default function AHFUDropdownSelector<T extends string = string>({
  options, value, onChange, placeholder = 'Select…', label, 'aria-label': ariaLabel,
  disabled = false, fullWidth = false, style, popupGroup,
}: AHFUDropdownSelectorProps<T>) {
  const id = useId();
  const anchor = useRef<HTMLDivElement>(null);
  const popup = useRef<HTMLDivElement>(null);
  const { open, mounted, shown, show, hide } = usePopupMotion();
  const position = useFloatingPosition(mounted, anchor, popup, 280);
  const selected = options.find(option => option.value === value);
  const focusTrigger = () => anchor.current?.querySelector('button')?.focus();
  const focusOption = (last = false) => {
    const buttons = popup.current?.querySelectorAll<HTMLButtonElement>('[role="option"]:not(:disabled)');
    const current = popup.current?.querySelector<HTMLButtonElement>('[aria-selected="true"]:not(:disabled)');
    (current ?? (last ? buttons?.[buttons.length - 1] : buttons?.[0]))?.focus();
  };
  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!anchor.current?.contains(event.target as Node) && !popup.current?.contains(event.target as Node)) hide();
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open, hide]);
  useEffect(() => {
    if (open) {
      const selectedOption = popup.current?.querySelector<HTMLButtonElement>('[aria-selected="true"]:not(:disabled)');
      (selectedOption ?? popup.current?.querySelector<HTMLButtonElement>('[role="option"]:not(:disabled)'))?.focus({ preventScroll: true });
    }
  }, [open]);

  return <div data-ahfu-field className="ahfu-dropdown-field" style={{ width: fullWidth ? '100%' : undefined, ...style }}>
    {label && <label className="ahfu-field-label" htmlFor={id}>{label}</label>}
    <div ref={anchor}>
      <AHFUButton id={id} type="button" disabled={disabled} aria-label={ariaLabel ?? label}
        aria-haspopup="listbox" aria-expanded={open} aria-controls={mounted ? `${id}-options` : undefined}
        className="ahfu-dropdown-trigger" onClick={() => open ? hide() : show()}
        onKeyDown={event => {
          if (event.key === 'ArrowDown' || event.key === 'ArrowUp') { event.preventDefault(); if (open) focusOption(event.key === 'ArrowUp'); else show(); }
          if (event.key === 'Escape' && open) { event.stopPropagation(); hide(); }
        }}
        style={{ width: '100%', height: 48, padding: '0 12px', border: `1px solid ${open ? 'var(--primary)' : 'var(--border)'}`, background: 'var(--surface)', color: selected ? 'var(--text-primary)' : 'var(--text-muted)', boxShadow: 'none' }}>
        <span className="ahfu-dropdown-value">{selected?.label ?? placeholder}</span>
        <span className="ahfu-dropdown-chevron" data-open={open}><AHFUIcon size={18}>expand_more</AHFUIcon></span>
      </AHFUButton>
    </div>
    {mounted && createPortal(<div ref={popup} id={`${id}-options`} role="listbox" aria-label={ariaLabel ?? label ?? placeholder}
      aria-hidden={!open || undefined} inert={!open} data-ahfu-popup-group={popupGroup}
      className="ahfu-popup ahfu-dropdown-menu ahfu-motion-popup" data-shown={shown}
      style={{ ...position, position: 'fixed', zIndex: zIndex.modal + 2 }}
      onKeyDown={event => {
        const buttons = Array.from(popup.current?.querySelectorAll<HTMLButtonElement>('[role="option"]:not(:disabled)') ?? []);
        const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
        let next = index;
        if (event.key === 'ArrowDown') next = (index + 1) % buttons.length;
        else if (event.key === 'ArrowUp') next = (index - 1 + buttons.length) % buttons.length;
        else if (event.key === 'Home') next = 0;
        else if (event.key === 'End') next = buttons.length - 1;
        else if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); hide(); focusTrigger(); return; }
        else if (event.key === 'Tab') { hide(); focusTrigger(); return; }
        else return;
        event.preventDefault(); buttons[next]?.focus();
      }}>
      {options.map(option => <AHFUButton key={option.value} type="button" role="option" aria-selected={option.value === value}
        disabled={option.disabled} className="ahfu-dropdown-option" onClick={() => { onChange(option.value); hide(); focusTrigger(); }}
        style={{ width: '100%', minHeight: 44, height: 'auto', padding: '10px 12px', justifyContent: 'flex-start', textAlign: 'left', whiteSpace: 'normal', background: option.value === value ? 'var(--primary-bg)' : 'transparent', color: option.value === value ? 'var(--md-sys-color-on-primary-container, var(--text-primary))' : 'var(--text-primary)', boxShadow: 'none' }}>
        {option.label}
      </AHFUButton>)}
    </div>, document.body)}
  </div>;
}
