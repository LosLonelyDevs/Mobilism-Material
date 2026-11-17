import { useId, useState, type ReactNode, type InputHTMLAttributes } from 'react';
import { typeRole, duration, easing, transition, disabled as disabledTokens } from './tokens.ts';
import { injectTouchTarget } from './utils/injectTouchTarget.ts';

injectTouchTarget();

export type AHFUSwitchSize = 'small' | 'medium';

export interface AHFUSwitchProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'style' | 'type' | 'size'> {
  label?: ReactNode;
  size?: AHFUSwitchSize;
  style?: React.CSSProperties;
}

// Material 3 switch geometry: a tall pill track with a thumb that is small when
// off, larger when on, and larger still while pressed (then springs back).
const sizeConfig: Record<AHFUSwitchSize, { w: number; h: number; off: number; on: number }> = {
  small:  { w: 40, h: 24, off: 12, on: 18 },
  medium: { w: 52, h: 32, off: 16, on: 24 },
};

export default function AHFUSwitch({
  label,
  size = 'medium',
  checked,
  disabled,
  id,
  style: overrideStyle,
  ...rest
}: AHFUSwitchProps) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const isChecked = !!checked;
  const s = sizeConfig[size];
  const [pressed, setPressed] = useState(false);

  // Pressed thumb grows ~4dp (M3), capped to fit inside the track.
  const base = isChecked ? s.on : s.off;
  const thumb = Math.min(s.h - 2, base + (pressed && !disabled ? 4 : 0));
  const top = (s.h - thumb) / 2;
  const left = isChecked ? s.w - thumb - (s.h - thumb) / 2 : (s.h - thumb) / 2;

  // Spatial properties spring (overshoot); the track colour glides.
  const thumbMotion = `left ${duration.normal}ms ${easing.overshoot}, top ${duration.normal}ms ${easing.overshoot}, width ${duration.normal}ms ${easing.overshoot}, height ${duration.normal}ms ${easing.overshoot}, background-color ${duration.quick}ms ${easing.standard}`;

  const track: React.CSSProperties = {
    width: s.w,
    height: s.h,
    borderRadius: 999,
    background: isChecked ? 'var(--primary)' : 'var(--md-sys-color-surface-container-highest, var(--surface-alt))',
    boxShadow: isChecked ? 'none' : 'inset 0 0 0 2px var(--border-strong)',
    position: 'relative',
    transition: transition(['background-color', 'box-shadow'], 'normal', 'standard'),
    flexShrink: 0,
    boxSizing: 'border-box',
  };

  const knob: React.CSSProperties = {
    position: 'absolute',
    top,
    left,
    width: thumb,
    height: thumb,
    borderRadius: '50%',
    background: isChecked ? 'var(--md-sys-color-on-primary, var(--primary-foreground))' : 'var(--border-strong)',
    display: 'grid',
    placeItems: 'center',
    transition: thumbMotion,
  };

  const checkSize = Math.round(thumb * 0.62);

  return (
    <label
      htmlFor={inputId}
      className="ahfu-touch-target"
      onPointerDown={() => setPressed(true)}
      onPointerUp={() => setPressed(false)}
      onPointerLeave={() => setPressed(false)}
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 10,
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? disabledTokens.content : 1,
        ...overrideStyle,
      }}
    >
      <input
        id={inputId}
        type="checkbox"
        role="switch"
        checked={isChecked}
        disabled={disabled}
        style={{ position: 'absolute', opacity: 0, width: 0, height: 0, pointerEvents: 'none' }}
        {...rest}
      />
      <span style={track} aria-hidden>
        <span style={knob}>
          <svg
            width={checkSize}
            height={checkSize}
            viewBox="0 0 24 24"
            fill="none"
            stroke="var(--primary)"
            strokeWidth={3}
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{
              opacity: isChecked ? 1 : 0,
              transform: isChecked ? 'scale(1)' : 'scale(0.4)',
              transition: `opacity ${duration.quick}ms ${easing.standard}, transform ${duration.normal}ms ${easing.overshoot}`,
            }}
          >
            <path d="M5 13l4 4L19 7" />
          </svg>
        </span>
      </span>
      {label && (
        <span style={{ ...typeRole.bodyLarge, color: 'var(--text-primary)' }}>{label}</span>
      )}
    </label>
  );
}
