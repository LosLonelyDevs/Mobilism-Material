import { useState, useCallback, useRef, useId, type KeyboardEvent } from 'react';
import AHFUChip from './AHFUChip.tsx';
import { spacing, radii, fontSizes, fontWeights, transitions } from './tokens.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export interface AHFUMultiTextInputProps {
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  label?: string;
  helperText?: string;
  maxTags?: number;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUMultiTextInput({
  values,
  onChange,
  placeholder = 'Type and press Enter...',
  label,
  helperText,
  maxTags,
  disabled = false,
  fullWidth = false,
  style: overrideStyle,
}: AHFUMultiTextInputProps) {
  const [input, setInput] = useState('');
  const [focused, setFocused] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const inputId = useId();

  const addValue = useCallback(
    (raw: string) => {
      const trimmed = raw.trim();
      if (!trimmed) return;
      if (values.includes(trimmed)) { setInput(''); return; }
      if (maxTags && values.length >= maxTags) return;
      onChange([...values, trimmed]);
      setInput('');
    },
    [values, onChange, maxTags],
  );

  const removeValue = useCallback(
    (val: string) => {
      onChange(values.filter((v) => v !== val));
    },
    [values, onChange],
  );

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        addValue(input);
      } else if (e.key === 'Backspace' && !input && values.length > 0) {
        removeValue(values[values.length - 1]);
      }
    },
    [input, values, addValue, removeValue],
  );

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: spacing[2],
    padding: spacing[4],
    minHeight: 44,
    backgroundColor: 'var(--surface)',
    border: `1px solid ${focused ? 'var(--primary)' : 'var(--border)'}`,
    borderRadius: `var(--control-radius, ${radii.sm}px)`,
    cursor: disabled ? 'default' : 'text',
    transition: transitions.fast,
    boxShadow: focused ? '0 0 0 2px var(--primary-bg)' : 'none',
    opacity: disabled ? 0.4 : 1,
    width: fullWidth ? '100%' : undefined,
    boxSizing: 'border-box',
    overflowX: 'auto',
    overflowY: 'hidden',
  };

  const inputStyle: React.CSSProperties = {
    flex: 1,
    minWidth: 80,
    border: 'none',
    outline: 'none',
    backgroundColor: 'transparent',
    fontSize: fontSizes.base,
    fontFamily: 'inherit',
    fontWeight: fontWeights.normal,
    color: 'var(--text-primary)',
    padding: 0,
  };

  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', gap: spacing[1], width: fullWidth ? '100%' : undefined, ...overrideStyle }}>
      {label && (
        <label htmlFor={inputId} style={{ fontSize: fontSizes.md, fontWeight: fontWeights.medium, color: 'var(--text-primary)' }}>
          {label}
        </label>
      )}
      <div
        style={containerStyle}
        onClick={() => inputRef.current?.focus()}
      >
        {values.map((val) => (
          <AHFUChip
            key={val}
            label={val}
            size="small"
            color="primary"
            onDelete={disabled ? undefined : () => removeValue(val)}
          />
        ))}
        <input
          id={inputId}
          ref={inputRef}
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => setFocused(true)}
          onBlur={() => { setFocused(false); addValue(input); }}
          placeholder={values.length === 0 ? placeholder : ''}
          disabled={disabled}
          style={inputStyle}
        />
      </div>
      {helperText && (
        <span style={{ fontSize: fontSizes.xs, color: 'var(--text-muted)', paddingLeft: 1 }}>
          {helperText}
        </span>
      )}
    </div>
  );
}
