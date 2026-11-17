import { type ReactNode, type ButtonHTMLAttributes } from 'react';
import StateLayer from './StateLayer.tsx';
import { shape, duration, easing, disabled as disabledTokens } from './tokens.ts';
import { injectTouchTarget } from './utils/injectTouchTarget.ts';

injectTouchTarget();

export type AHFUIconButtonVariant = 'standard' | 'filled' | 'tonal' | 'outlined';
export type AHFUIconButtonSize = 'sm' | 'md' | 'lg';

export interface AHFUIconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'style'> {
  /** The icon to render (e.g. <AHFUIcon>content_copy</AHFUIcon>). */
  children: ReactNode;
  variant?: AHFUIconButtonVariant;
  size?: AHFUIconButtonSize;
  /** Accessible label — required, since the button is icon-only. */
  'aria-label': string;
  style?: React.CSSProperties;
}

const sizePx: Record<AHFUIconButtonSize, number> = { sm: 32, md: 40, lg: 48 };

const variantStyles: Record<AHFUIconButtonVariant, { bg: string; fg: string; border?: string; elevated?: boolean }> = {
  standard: { bg: 'transparent', fg: 'var(--text-secondary)' },
  filled:   { bg: 'var(--primary)', fg: 'var(--primary-foreground)', elevated: true },
  tonal:    { bg: 'var(--primary-bg)', fg: 'var(--md-sys-color-on-primary-container, var(--primary))' },
  outlined: { bg: 'transparent', fg: 'var(--text-secondary)', border: '1px solid var(--border-strong)' },
};

export default function AHFUIconButton({
  children,
  variant = 'standard',
  size = 'md',
  disabled,
  style,
  ...rest
}: AHFUIconButtonProps) {
  const dim = sizePx[size];
  const v = variantStyles[variant];

  const btnStyle: React.CSSProperties = {
    width: dim,
    height: dim,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 0,
    flexShrink: 0,
    border: v.border ?? 'none',
    borderRadius: shape.pill,
    background: v.bg,
    color: v.fg,
    cursor: 'pointer',
    boxShadow: v.elevated && !disabled ? 'var(--elevation-1)' : 'none',
    transition: `background-color ${duration.normal}ms ${easing.standard}, box-shadow ${duration.normal}ms ${easing.standard}, transform ${duration.quick}ms ${easing.overshoot}`,
    ...(disabled ? { opacity: disabledTokens.content, pointerEvents: 'none', boxShadow: 'none' } : {}),
    ...style,
  };

  return (
    <StateLayer
      as="button"
      disabled={disabled}
      color={v.fg}
      className="ahfu-touch-target"
      style={btnStyle}
      {...rest}
    >
      {children}
    </StateLayer>
  );
}
