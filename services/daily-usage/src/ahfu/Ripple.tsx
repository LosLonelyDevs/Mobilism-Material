import { useEffect, useRef, useState } from 'react';
import { MIN_DURATION } from './tokens.ts';
import { injectKeyframes } from './utils/injectKeyframes.ts';
import { useReducedMotion } from './hooks/useReducedMotion.ts';

// Gentle, evenly-paced expansion — no sudden jump at the start.
const RIPPLE_EASE = 'cubic-bezier(0.4, 0, 0.2, 1)';

// Material 3 touch ripple: an expanding, fading circle that originates at the
// pointer-down point. Drop <Ripple /> inside any `position: relative` host; it
// attaches to its parent element and clips itself to the host's border radius.
// Scale grows steadily while opacity is held, then fades near the end — so the
// ripple stays visible across its whole travel instead of vanishing instantly.
injectKeyframes(
  'ahfu-ripple',
  `@keyframes ahfu-ripple-expand{0%{transform:scale(0);opacity:0.34}35%{opacity:0.30}100%{transform:scale(1);opacity:0}}`,
);

interface RippleInstance {
  id: number;
  x: number;
  y: number;
  size: number;
}

export interface RippleProps {
  /** Ripple colour. Defaults to the host's text colour. */
  color?: string;
  /** Ripple lifetime in ms. */
  durationMs?: number;
}

export default function Ripple({ color = 'currentColor', durationMs = 900 }: RippleProps) {
  const rippleMs = Math.max(durationMs, MIN_DURATION);
  const anchorRef = useRef<HTMLSpanElement>(null);
  const [ripples, setRipples] = useState<RippleInstance[]>([]);
  const reduced = useReducedMotion();

  useEffect(() => {
    const host = anchorRef.current?.parentElement;
    if (!host || reduced) return;
    let counter = 0;
    const onDown = (e: PointerEvent) => {
      const rect = host.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      // Radius = farthest corner, so the ripple always reaches the edges.
      const radius = Math.hypot(Math.max(x, rect.width - x), Math.max(y, rect.height - y));
      setRipples((prev) => [...prev, { id: ++counter, x, y, size: radius * 2 }]);
    };
    host.addEventListener('pointerdown', onDown);
    return () => host.removeEventListener('pointerdown', onDown);
  }, [reduced]);

  const remove = (id: number) => setRipples((prev) => prev.filter((r) => r.id !== id));

  return (
    <span
      ref={anchorRef}
      aria-hidden="true"
      // translateZ(0) promotes this to its own layer so the rounded overflow
      // clip is applied to the (transformed) ripple children — otherwise Chrome
      // clips them to the square border-box and the corners poke out.
      style={{ position: 'absolute', inset: 0, overflow: 'hidden', borderRadius: 'inherit', pointerEvents: 'none', transform: 'translateZ(0)' }}
    >
      {ripples.map((r) => (
        <span
          key={r.id}
          onAnimationEnd={() => remove(r.id)}
          style={{
            position: 'absolute',
            left: r.x - r.size / 2,
            top: r.y - r.size / 2,
            width: r.size,
            height: r.size,
            borderRadius: '50%',
            backgroundColor: color,
            pointerEvents: 'none',
            transform: 'scale(0)', // base state before the animation's first frame paints
            animation: `ahfu-ripple-expand ${rippleMs}ms ${RIPPLE_EASE} forwards`,
          }}
        />
      ))}
    </span>
  );
}
