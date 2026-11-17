import { useState, type ReactNode, type ButtonHTMLAttributes } from 'react';
import StateLayer from './StateLayer.tsx';
import { shape, typeRole, duration, STATE_DURATION, easing, disabled as disabledTokens } from './tokens.ts';
import { injectTouchTarget } from './utils/injectTouchTarget.ts';
import AHFUSpinner, { type AHFUSpinnerProps } from './AHFUSpinner.tsx';

injectTouchTarget();

export type AHFUButtonVariant = 'primary' | 'success' | 'danger' | 'warning' | 'filled';
export type AHFUButtonSize = 'sm' | 'md' | 'lg';

export interface AHFUButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'style'> {
  children: ReactNode;
  variant?: AHFUButtonVariant;
  size?: AHFUButtonSize;
  /** Replaces label with spinner. Dimensions stay fixed. */
  loading?: boolean;
  /** Shows spinner to the left of the label while keeping the label visible. Disables clicks. */
  loadingLabel?: boolean;
  /** Spinner animation variant. Default: 'donut'. */
  spinnerVariant?: AHFUSpinnerProps['variant'];
  style?: React.CSSProperties;
}

// Material 3 colour treatment per variant. `filled` is the high-emphasis filled
// button (primary); the rest are tonal buttons in their semantic role.
type VariantStyle = { bg: string; fg: string; elevated?: boolean };
const variantStyles: Record<AHFUButtonVariant, VariantStyle> = {
  filled:  { bg: 'var(--primary)', fg: 'var(--primary-foreground)', elevated: true },
  primary: { bg: 'var(--primary-bg)', fg: 'var(--md-sys-color-on-primary-container, var(--primary))' },
  success: { bg: 'var(--md-sys-color-success-container, var(--success))', fg: 'var(--md-sys-color-on-success-container, var(--success-foreground))' },
  warning: { bg: 'var(--md-sys-color-warning-container, var(--warning))', fg: 'var(--md-sys-color-on-warning-container, var(--warning-foreground))' },
  danger:  { bg: 'var(--md-sys-color-error-container, var(--danger))', fg: 'var(--md-sys-color-on-error-container, var(--danger-foreground))' },
};

// Compact rounded rectangles, consistent across every button size.
const sizeStyles: Record<AHFUButtonSize, React.CSSProperties> = {
  sm: { height: 32, padding: '0 16px', ...typeRole.labelLarge, fontSize: 13 },
  md: { height: 40, padding: '0 24px', ...typeRole.labelLarge },
  lg: { height: 48, padding: '0 28px', ...typeRole.labelLarge, fontSize: 15 },
};

const spinnerDim: Record<AHFUButtonSize, number> = { sm: 12, md: 16, lg: 18 };

const baseStyle: React.CSSProperties = {
  fontFamily: 'inherit',
  cursor: 'pointer',
  border: 'none',
  whiteSpace: 'nowrap',
};

// Spatial (corner/scale) springs with overshoot; effects (shadow/colour) glide.
// Spatial press-morph stays on the slow floor; hover elevation + colour use the fast clock.
const MOTION = `border-radius ${duration.moderate}ms ${easing.overshoot}, transform ${duration.moderate}ms ${easing.overshoot}, box-shadow ${STATE_DURATION}ms ${easing.standard}, background-color ${STATE_DURATION}ms ${easing.standard}`;

export default function AHFUButton({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  loadingLabel = false,
  spinnerVariant = 'donut',
  style: overrideStyle,
  disabled,
  ...rest
}: AHFUButtonProps) {
  const v = variantStyles[variant];
  const isDisabled = disabled || loading || loadingLabel;
  const dim = spinnerDim[size];
  const [pressed, setPressed] = useState(false);
  const [hovered, setHovered] = useState(false);

  // Expressive press: the corner softens inward and the surface squashes,
  // settling back with a spring. Hover lifts the surface a level of elevation.
  const elevation = isDisabled
    ? 'none'
    : v.elevated
      ? (hovered ? 'var(--elevation-2)' : 'var(--elevation-1)')
      : (hovered ? 'var(--elevation-1)' : 'none');

  const buttonStyle: React.CSSProperties = {
    ...baseStyle,
    ...sizeStyles[size],
    color: v.fg,
    background: v.bg,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    position: 'relative',
    borderRadius: pressed ? shape.xs : shape.sm,
    transform: pressed ? 'scale(0.96)' : 'scale(1)',
    boxShadow: elevation,
    transition: MOTION,
    ...(isDisabled ? { pointerEvents: 'none' } : {}),
    ...(disabled ? { opacity: disabledTokens.content, boxShadow: 'none' } : {}),
    ...overrideStyle,
  };

  return (
    <StateLayer
      as="button"
      disabled={isDisabled}
      color={v.fg}
      aria-busy={(loading || loadingLabel) || undefined}
      style={buttonStyle}
      className="ahfu-touch-target"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => { setHovered(false); setPressed(false); }}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      {...rest}
    >
      {loadingLabel && (
        <span style={{
          width: dim, height: dim, flexShrink: 0,
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          marginRight: 8,
        }}>
          <AHFUSpinner size="button" variant={spinnerVariant} dim={dim} color="currentColor" />
        </span>
      )}
      <span style={{ visibility: loading ? 'hidden' : 'visible', display: 'inline-flex', alignItems: 'center' }}>
        {children}
      </span>
      <span style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', visibility: loading ? 'visible' : 'hidden' }}>
        <AHFUSpinner size="button" variant={spinnerVariant} dim={dim} color="currentColor" />
      </span>
    </StateLayer>
  );
}
