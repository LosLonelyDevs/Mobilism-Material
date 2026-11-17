import type { ReactNode, TextareaHTMLAttributes } from 'react';
import { useId } from 'react';
import { motion } from './tokens.ts';

export interface AHFUTextAreaProps
  extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'style'> {
  label?: ReactNode;
  helperText?: ReactNode;
  errorText?: ReactNode;
  resize?: 'none' | 'vertical' | 'horizontal' | 'both';
  style?: React.CSSProperties;
}

export default function AHFUTextArea({
  label,
  helperText,
  errorText,
  resize = 'vertical',
  id,
  rows = 4,
  disabled,
  style: overrideStyle,
  ...rest
}: AHFUTextAreaProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const hasError = !!errorText;

  const textarea: React.CSSProperties = {
    width: '100%',
    padding: '8px 10px',
    fontFamily: 'inherit',
    fontSize: 14,
    lineHeight: '20px',
    color: 'var(--text-primary)',
    background: 'var(--surface-alt)',
    border: `1px solid ${hasError ? 'var(--danger)' : 'var(--border)'}`,
    borderRadius: 'var(--control-radius, 6px)',
    outline: 'none',
    resize,
    boxSizing: 'border-box',
    transition: `border-color ${motion.input.focus}ms ease, box-shadow ${motion.input.focus}ms ease`,
    opacity: disabled ? 0.5 : 1,
    ...overrideStyle,
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      {label && (
        <label
          htmlFor={inputId}
          style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-primary)', lineHeight: '20px' }}
        >
          {label}
        </label>
      )}
      <textarea
        id={inputId}
        rows={rows}
        disabled={disabled}
        style={textarea}
        onFocus={(e) => {
          e.currentTarget.style.borderColor = hasError ? 'var(--danger)' : 'var(--primary)';
          e.currentTarget.style.boxShadow = `0 0 0 2px ${hasError ? 'var(--danger)' : 'var(--primary)'}`;
        }}
        onBlur={(e) => {
          e.currentTarget.style.borderColor = hasError ? 'var(--danger)' : 'var(--border)';
          e.currentTarget.style.boxShadow = 'none';
        }}
        {...rest}
      />
      <span
        style={{
          fontSize: 11,
          lineHeight: 1.4,
          paddingTop: 2,
          display: 'block',
          minHeight: '1.4em',
          color: hasError ? 'var(--danger)' : 'var(--text-secondary)',
        }}
      >
        {(errorText || helperText) ?? '\u00A0'}
      </span>
    </div>
  );
}
