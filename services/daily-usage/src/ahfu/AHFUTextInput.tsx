import { useState, useCallback, useId, useEffect, useMemo, type InputHTMLAttributes } from 'react';
import { useFuzzySearchList } from '@nozbe/microfuzz/react';
import { shape, typeRole, STATE_DURATION, easing, stateTransition } from './tokens.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUTextInputStatus = 'default' | 'success' | 'error' | 'warning';

export interface AHFUTextInputProps<T = string>
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'style' | 'size' | 'list'> {
  label?: string;
  helperText?: string;
  errorText?: string;
  successText?: string;
  status?: AHFUTextInputStatus;
  size?: 'xsmall' | 'small' | 'medium' | 'large';
  fullWidth?: boolean;
  style?: React.CSSProperties;
  /** Enable fuzzy search mode. Requires `list` and `onResults`. */
  fuzzy?: boolean;
  /** Items to search over when `fuzzy` is true. */
  list?: T[];
  /** Extract searchable string(s) from each item. Defaults to identity for string lists. */
  getText?: (item: T) => string[];
  /** Called with filtered+ranked results whenever query changes. */
  onResults?: (results: T[]) => void;
}

/* -------------------------------------------------- */
/*  Status colors                                     */
/* -------------------------------------------------- */

const statusColors: Record<AHFUTextInputStatus, { indicator: string; text: string }> = {
  default: { indicator: 'var(--border-strong)', text: 'var(--text-secondary)' },
  success: { indicator: 'var(--success)', text: 'var(--success)' },
  error:   { indicator: 'var(--danger)',  text: 'var(--danger)' },
  warning: { indicator: 'var(--warning)', text: 'var(--warning)' },
};

/* -------------------------------------------------- */
/*  Size config                                       */
/* -------------------------------------------------- */

const sizes = {
  xsmall: { height: 32, fontSize: 12, labelSize: 11 },
  small:  { height: 40, fontSize: 13, labelSize: 12 },
  medium: { height: 48, fontSize: 14, labelSize: 13 },
  large:  { height: 56, fontSize: 15, labelSize: 14 },
} as const;

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUTextInput<T = string>({
  label,
  helperText,
  errorText,
  successText,
  status = 'default',
  size = 'medium',
  fullWidth = false,
  disabled,
  style: overrideStyle,
  onFocus: onFocusProp,
  onBlur: onBlurProp,
  fuzzy = false,
  list,
  getText,
  onResults,
  onChange: onChangeProp,
  value: valueProp,
  ...rest
}: AHFUTextInputProps<T>) {
  const [focused, setFocused] = useState(false);
  const [internalQuery, setInternalQuery] = useState('');
  const id = useId();
  const inputId = rest.id ?? id;

  const query = fuzzy ? (valueProp !== undefined ? String(valueProp) : internalQuery) : '';

  const stableGetText = useMemo(() => getText ?? ((item: T) => [String(item)]), [getText]);

  const fuzzyResults = useFuzzySearchList({
    list: (fuzzy ? list ?? [] : []) as T[],
    queryText: query,
    getText: stableGetText,
    mapResultItem: ({ item }) => item,
  });

  useEffect(() => {
    if (fuzzy) onResults?.(fuzzyResults as T[]);
  }, [fuzzy, fuzzyResults, onResults]);

  const s = sizes[size];
  const sc = statusColors[status];
  // Material 3 filled field: a resting hairline indicator, plus a 2dp active
  // indicator that springs out from the centre on focus.
  const restingIndicator = status === 'default' ? 'var(--border-strong)' : sc.indicator;
  const activeIndicator = status === 'default' ? 'var(--primary)' : sc.indicator;
  const indicatorActive = focused || status !== 'default';

  const feedbackText = status === 'error' ? errorText : status === 'success' ? successText : helperText;
  const feedbackColor = status === 'error' || status === 'success' || status === 'warning' ? sc.text : 'var(--text-muted)';

  const handleFocus = useCallback(
    (e: React.FocusEvent<HTMLInputElement>) => {
      setFocused(true);
      onFocusProp?.(e);
    },
    [onFocusProp],
  );

  const handleBlur = useCallback(
    (e: React.FocusEvent<HTMLInputElement>) => {
      setFocused(false);
      onBlurProp?.(e);
    },
    [onBlurProp],
  );

  const handleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      if (fuzzy && valueProp === undefined) setInternalQuery(e.target.value);
      onChangeProp?.(e);
    },
    [fuzzy, valueProp, onChangeProp],
  );

  const wrapperStyle: React.CSSProperties = {
    display: 'inline-flex',
    flexDirection: 'column',
    gap: 6,
    width: fullWidth ? '100%' : undefined,
    opacity: disabled ? 0.4 : 1,
    ...overrideStyle,
  };

  const inputStyle: React.CSSProperties = {
    height: s.height,
    fontSize: s.fontSize,
    padding: '0 16px',
    fontFamily: 'inherit',
    fontWeight: 500,
    color: 'var(--text-primary)',
    backgroundColor: 'var(--md-sys-color-surface-container-highest, var(--surface-alt))',
    border: 'none',
    borderBottom: `1px solid ${restingIndicator}`,
    borderRadius: `var(--control-radius, ${shape.xs}px)`,
    outline: 'none',
    transition: stateTransition(['background-color', 'border-color']),
    width: '100%',
    cursor: disabled ? 'default' : undefined,
    boxSizing: 'border-box',
  };

  return (
    <div style={wrapperStyle}>
      {label && (
        <label
          htmlFor={inputId}
          style={{
            ...typeRole.labelMedium,
            fontSize: s.labelSize,
            color: focused && status === 'default' ? 'var(--primary)' : 'var(--text-secondary)',
            transition: stateTransition(['color']),
          }}
        >
          {label}
        </label>
      )}
      <div style={{ position: 'relative', width: fullWidth ? '100%' : undefined }}>
        <input
          id={inputId}
          disabled={disabled}
          onFocus={handleFocus}
          onBlur={handleBlur}
          onChange={handleChange}
          value={valueProp !== undefined ? valueProp : fuzzy ? internalQuery : undefined}
          style={inputStyle}
          {...rest}
        />
        {/* 2dp active indicator that springs out from the centre */}
        <span
          aria-hidden
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            bottom: 0,
            height: 2,
            background: activeIndicator,
            transformOrigin: 'center',
            transform: indicatorActive ? 'scaleX(1)' : 'scaleX(0)',
            transition: `transform ${STATE_DURATION}ms ${easing.decelerate}`,
            pointerEvents: 'none',
          }}
        />
      </div>
      {feedbackText && (
        <span style={{ ...typeRole.bodySmall, fontSize: 11, color: feedbackColor, lineHeight: 1.4, paddingLeft: 16, display: 'block' }}>
          {feedbackText}
        </span>
      )}
    </div>
  );
}
