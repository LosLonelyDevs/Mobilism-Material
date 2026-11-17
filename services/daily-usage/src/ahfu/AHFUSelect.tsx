import { useState, useRef, useCallback, useMemo, useEffect, useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { injectKeyframes } from './utils/injectKeyframes.ts';
import { motion } from './tokens.ts';
import { useFloatingPosition } from './hooks/useFloatingPosition.ts';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
export interface AHFUSelectOption {
  value: string;
  label: string;
  subtitle?: string | null;
  count?: number;
}

export interface AHFUSelectProps {
  options: AHFUSelectOption[];
  value?: string | string[];
  onChange?: (value: string | string[]) => void;
  multiple?: boolean;
  searchable?: boolean;
  placeholder?: string;
  label?: string;
  labelledBy?: string;
  'aria-label'?: string;
  disabled?: boolean;
  style?: React.CSSProperties;
}

// Dropdown menu open/close animation duration (ms) — both directions.
const MENU_ANIMATION_MS = 500;

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------
injectKeyframes('ahfu-select', `
.ahfu-select-option { background: transparent; transition: background ${motion.input.option}ms ease; }
.ahfu-select-option:hover { background: color-mix(in srgb, var(--text-muted) 10%, transparent); }
.ahfu-select-tag-x { opacity: 0.5; transition: opacity ${motion.input.option}ms ease; }
.ahfu-select-tag-x:hover { opacity: 1; }
`);

const S: Record<string, React.CSSProperties> = {
  wrapper: {
    position: 'relative',
    display: 'inline-block',
    width: '100%',
  },
  trigger: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    width: '100%',
    minHeight: 36,
    padding: '5px 10px',
    fontSize: 13,
    fontFamily: 'inherit',
    color: 'var(--text-primary)',
    backgroundColor: 'var(--surface)',
    border: '1.5px solid var(--border)',
    borderRadius: 'var(--control-radius, 6px)',
    cursor: 'pointer',
    boxSizing: 'border-box' as const,
    flexWrap: 'wrap' as const,
    transition: `border-color ${motion.input.focus}ms ease`,
  },
  triggerOpen: {
    borderColor: 'var(--primary)',
  },
  triggerDisabled: {
    opacity: 0.4,
    cursor: 'default',
  },
  placeholder: {
    color: 'var(--text-muted)',
    flex: 1,
  },
  singleValue: {
    flex: 1,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  arrow: {
    marginLeft: 'auto',
    fontSize: 10,
    color: 'var(--text-muted)',
    flexShrink: 0,
    transition: `transform ${motion.input.option}ms ease`,
  },
  arrowOpen: {
    transform: 'rotate(180deg)',
  },
  tag: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    padding: '2px 8px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: 'var(--primary-bg)',
    color: 'var(--primary)',
    border: '1px solid var(--primary-border)',
    borderRadius: 4,
  },
  tagRemove: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: 12,
    color: 'inherit',
    padding: 0,
    lineHeight: 1,
    fontFamily: 'inherit',
  },
  backdrop: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 99,
    background: 'transparent',
  },
  dropdown: {
    position: 'fixed',
    zIndex: 100,
    backgroundColor: 'var(--surface-alt)',
    border: '1.5px solid var(--border)',
    borderRadius: 8,
    boxShadow: 'var(--shadow)',
    maxHeight: 240,
    overflowY: 'auto',
    padding: '4px 0',
  },
  searchInput: {
    width: '100%',
    padding: '8px 12px',
    fontSize: 13,
    fontFamily: 'inherit',
    color: 'var(--text-primary)',
    backgroundColor: 'var(--surface)',
    border: 'none',
    borderBottom: '1px solid var(--border)',
    outline: 'none',
    boxSizing: 'border-box' as const,
  },
  option: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    width: '100%',
    padding: '8px 12px',
    fontSize: 13,
    fontFamily: 'inherit',
    color: 'var(--text-primary)',
    border: 'none',
    cursor: 'pointer',
    textAlign: 'left',
  },
  optionSelected: {
    color: 'var(--primary)',
    fontWeight: 700,
  },
  optionCount: {
    marginLeft: 'auto',
    fontSize: 11,
    color: 'var(--text-muted)',
  },
  checkbox: {
    width: 16,
    height: 16,
    cursor: 'pointer',
    accentColor: 'var(--primary)',
    flexShrink: 0,
    opacity: 1,
  },
  checkboxUnchecked: {
    width: 16,
    height: 16,
    cursor: 'pointer',
    accentColor: 'var(--primary)',
    flexShrink: 0,
    opacity: 0.4,
  },
  optionText: {
    display: 'flex',
    flexDirection: 'column',
    gap: 1,
  },
  optionSubtitle: {
    fontSize: 11,
    color: 'var(--text-muted)',
    fontWeight: 400,
  },
  noResults: {
    padding: '12px',
    fontSize: 13,
    color: 'var(--text-muted)',
    textAlign: 'center',
  },
  actions: {
    display: 'flex',
    gap: 8,
    padding: '6px 12px',
    borderBottom: '1px solid var(--border)',
  },
  actionBtn: {
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    color: 'var(--link)',
    fontSize: 12,
    fontFamily: 'inherit',
    padding: 0,
  },
};

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------
export default function AHFUSelect({
  options,
  value,
  onChange,
  multiple = false,
  searchable = false,
  placeholder = 'Select…',
  label,
  labelledBy,
  'aria-label': ariaLabel,
  disabled = false,
  style,
}: AHFUSelectProps) {
  const menuId = useId();
  const [open, setOpen] = useState(false);
  // Keep the dropdown mounted through its exit so open AND close both animate
  // (MENU_ANIMATION_MS each way): `menuMounted` keeps it in the DOM,
  // `menuShown` drives the opacity/transform transition.
  const [menuMounted, setMenuMounted] = useState(false);
  const [menuShown, setMenuShown] = useState(false);
  const menuRaf = useRef<number | undefined>(undefined);
  const menuTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [search, setSearch] = useState('');
  const searchRef = useRef<HTMLInputElement>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const popupRef = useRef<HTMLDivElement>(null);
  const dropdownPos = useFloatingPosition(menuMounted, triggerRef, popupRef, 240);

  const cancelMenuAnimation = useCallback(() => {
    if (menuRaf.current !== undefined) cancelAnimationFrame(menuRaf.current);
    if (menuTimer.current !== undefined) clearTimeout(menuTimer.current);
  }, []);

  const openMenu = useCallback(() => {
    cancelMenuAnimation();
    setOpen(true);
    setMenuMounted(true);
    // Double rAF: the first fires before the freshly-inserted menu is painted,
    // so flipping shown there coalesces both states into one style update and
    // the enter transition never runs. Wait one full frame.
    menuRaf.current = requestAnimationFrame(() => {
      menuRaf.current = requestAnimationFrame(() => setMenuShown(true));
    });
  }, [cancelMenuAnimation]);

  const closeMenu = useCallback(() => {
    cancelMenuAnimation();
    setOpen(false);
    setMenuShown(false);
    menuTimer.current = setTimeout(() => setMenuMounted(false), MENU_ANIMATION_MS);
  }, [cancelMenuAnimation]);

  // Cancel any in-flight open/close animation on unmount.
  useEffect(() => cancelMenuAnimation, [cancelMenuAnimation]);

  // Normalize value
  const selectedSet = useMemo(() => {
    if (!value) return new Set<string>();
    if (Array.isArray(value)) return new Set(value);
    return new Set([value]);
  }, [value]);

  // Filtered options
  const filteredOptions = useMemo(() => {
    if (!search) return options;
    const s = search.toLowerCase();
    return options.filter((o) => o.label.toLowerCase().includes(s));
  }, [options, search]);

  // Display label for single-select
  const displayLabel = useMemo(() => {
    if (multiple) return null;
    if (selectedSet.size === 0) return null;
    const val = Array.from(selectedSet)[0];
    return options.find((o) => o.value === val)?.label ?? val;
  }, [multiple, selectedSet, options]);

  const handleOpen = useCallback(() => {
    if (disabled) return;
    openMenu();
    setSearch('');
  }, [disabled, openMenu]);

  const handleClose = useCallback(() => {
    closeMenu();
    setSearch('');
  }, [closeMenu]);

  useEffect(() => {
    if (!open) return;
    const dismiss = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation();
        handleClose();
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('keydown', dismiss);
    return () => document.removeEventListener('keydown', dismiss);
  }, [open, handleClose]);

  const handleSelect = useCallback((optValue: string) => {
    if (multiple) {
      const next = new Set(selectedSet);
      if (next.has(optValue)) next.delete(optValue);
      else next.add(optValue);
      onChange?.(Array.from(next));
      setSearch('');
    } else {
      onChange?.(optValue);
      closeMenu();
      triggerRef.current?.focus();
    }
  }, [multiple, selectedSet, onChange, closeMenu]);

  const handleRemoveTag = useCallback((optValue: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const next = new Set(selectedSet);
    next.delete(optValue);
    onChange?.(Array.from(next));
  }, [selectedSet, onChange]);

  const handleSelectAll = useCallback(() => {
    onChange?.(options.map((o) => o.value));
  }, [options, onChange]);

  const handleClearAll = useCallback(() => {
    onChange?.(multiple ? [] : '');
  }, [multiple, onChange]);

  // Focus search once the menu is actually in the DOM.
  useEffect(() => {
    if (!open || !menuMounted) return;
    if (searchable) searchRef.current?.focus({ preventScroll: true });
    else (popupRef.current?.querySelector<HTMLButtonElement>('[role="option"][aria-selected="true"]') ?? popupRef.current?.querySelector<HTMLButtonElement>('[role="option"]'))?.focus({ preventScroll: true });
  }, [open, menuMounted, searchable]);

  // Tags for multi-select
  const tags = useMemo((): ReactNode => {
    if (!multiple || selectedSet.size === 0) return null;
    return Array.from(selectedSet).map((val) => {
      const label = options.find((o) => o.value === val)?.label ?? val;
      return (
        <span key={val} style={S.tag}>
          {label}
          <button type="button" aria-label={`Remove ${label}`} className="ahfu-select-tag-x" style={S.tagRemove} onClick={(e) => handleRemoveTag(val, e)}>&#10005;</button>
        </span>
      );
    });
  }, [multiple, selectedSet, options, handleRemoveTag]);

  return (
    <div data-ahfu-field style={{ ...S.wrapper, ...style }}>
      {label && <span className="ahfu-field-label" style={{ display: 'block', marginBottom: 6 }}>{label}</span>}
      {/* Trigger */}
      <div
        ref={triggerRef}
        role="combobox"
        aria-labelledby={labelledBy}
        aria-controls={menuId}
        aria-label={ariaLabel ?? label ?? placeholder}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-disabled={disabled}
        tabIndex={disabled ? -1 : 0}
        onKeyDown={(event) => {
          if (event.target !== event.currentTarget) return;
          if (['Enter', ' ', 'ArrowDown', 'ArrowUp'].includes(event.key)) { event.preventDefault(); handleOpen(); }
        }}
        style={{
          ...S.trigger,
          ...(open ? S.triggerOpen : {}),
          ...(disabled ? S.triggerDisabled : {}),
        }}
        onClick={handleOpen}
      >
        {multiple && tags}
        {!multiple && displayLabel && <span style={S.singleValue}>{displayLabel}</span>}
        {((multiple && selectedSet.size === 0) || (!multiple && !displayLabel)) && (
          <span style={S.placeholder}>{placeholder}</span>
        )}
        <span style={{ ...S.arrow, ...(open ? S.arrowOpen : {}) }}>&#9660;</span>
      </div>

      {/* Dropdown (portaled to body to escape overflow clipping). Stays
          mounted through its exit transition; the backdrop only blocks
          clicks while logically open. */}
      {menuMounted && createPortal(
        <>
          {open && <div style={S.backdrop} onClick={(e) => { e.stopPropagation(); handleClose(); }} />}
          <div
            ref={popupRef}
            id={menuId}
            role="listbox"
            aria-labelledby={labelledBy}
            aria-hidden={!open || undefined}
            inert={!open}
            aria-label={ariaLabel ?? label ?? placeholder}
            aria-multiselectable={multiple || undefined}
            onKeyDown={event => {
              if (event.key === 'Tab') { handleClose(); triggerRef.current?.focus(); return; }
              if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
              if (event.target === searchRef.current && ['Home', 'End'].includes(event.key)) return;
              const options = Array.from(popupRef.current?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? []);
              if (!options.length) return;
              event.preventDefault();
              const index = options.indexOf(document.activeElement as HTMLButtonElement);
              const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1 : event.key === 'ArrowDown' ? (index + 1) % options.length : (index - 1 + options.length) % options.length;
              options[next]?.focus();
            }}
            className="ahfu-scroll ahfu-popup"
            style={{
              ...S.dropdown,
              top: dropdownPos.top,
              left: dropdownPos.left,
              width: dropdownPos.width,
              maxHeight: dropdownPos.maxHeight,
              opacity: menuShown ? 1 : 0,
              // Unfurl from the trigger: the menu grows down out of its top
              // edge rather than sliding as a rigid block.
              transform: menuShown ? 'scaleY(1)' : 'scaleY(0.6)',
              transformOrigin: 'top center',
              pointerEvents: menuShown ? 'auto' : 'none',
              transition: `opacity ${MENU_ANIMATION_MS}ms cubic-bezier(0.4, 0, 0.2, 1), transform ${MENU_ANIMATION_MS}ms cubic-bezier(0.4, 0, 0.2, 1)`,
            }}
          >
            {/* Search */}
            {searchable && (
              <input
                ref={searchRef}
                type="text"
                aria-label="Search options"
                placeholder="Search…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                style={S.searchInput}
                onFocus={(e) => { e.currentTarget.style.boxShadow = '0 0 0 2px var(--primary)'; }}
                onBlur={(e) => { e.currentTarget.style.boxShadow = 'none'; }}
              />
            )}

            {/* Select All / Clear for multi */}
            {multiple && (
              <div style={S.actions}>
                <button type="button" style={S.actionBtn} onClick={handleSelectAll}>Select All</button>
                <button type="button" style={S.actionBtn} onClick={handleClearAll}>Clear</button>
              </div>
            )}

            {/* Options */}
            {filteredOptions.map((opt) => {
              const selected = selectedSet.has(opt.value);
              return (
                <button
                  type="button"
                  key={opt.value}
                  role="option"
                  aria-selected={selected}
                  className="ahfu-select-option"
                  style={{ ...S.option, ...(selected ? S.optionSelected : {}) }}
                  onClick={() => handleSelect(opt.value)}
                >
                  {multiple && (
                    <input type="checkbox" checked={selected} readOnly style={selected ? S.checkbox : S.checkboxUnchecked} />
                  )}
                  <span style={opt.subtitle ? S.optionText : undefined}>
                    <span>{opt.label}</span>
                    {opt.subtitle && <span style={S.optionSubtitle}>{opt.subtitle}</span>}
                  </span>
                  {opt.count != null && <span style={S.optionCount}>{opt.count}</span>}
                </button>
              );
            })}

            {filteredOptions.length === 0 && (
              <div style={S.noResults}>No results</div>
            )}
          </div>
        </>,
        document.body,
      )}
    </div>
  );
}
