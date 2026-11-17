import { useId, useMemo } from 'react';
import { spacing, fontSizes, motion, easing } from './tokens.ts';
import { injectKeyframes } from './utils/injectKeyframes.ts';
import { useReducedMotion } from './hooks/useReducedMotion.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUSpinnerSize    = 'small' | 'medium' | 'large' | 'button';
export type AHFUSpinnerVariant = 'blob' | 'donut' | 'squish' | 'orbit' | 'worm' | 'pill';

export interface AHFUSpinnerProps {
  size?:      AHFUSpinnerSize;
  dim?:       number;
  variant?:   AHFUSpinnerVariant | 'random';
  color?:     string;
  label?:     string;
  fullWidth?: boolean;
  style?:     React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Size config                                       */
/* -------------------------------------------------- */

const sizeMap: Record<AHFUSpinnerSize, number> = {
  button: 14,
  small:  20,
  medium: 32,
  large:  48,
};

const ALL_VARIANTS: AHFUSpinnerVariant[] = ['blob', 'donut', 'squish', 'orbit', 'worm', 'pill'];

/* -------------------------------------------------- */
/*  Keyframes                                         */
/* -------------------------------------------------- */

// blob — morphing blob + spin
injectKeyframes('ahfu-spinner-morph', `
@keyframes ahfu-spinner-morph {
  0%,100% { border-radius: 42% 58% 63% 37% / 41% 44% 56% 59%; }
  25%     { border-radius: 58% 42% 37% 63% / 56% 59% 41% 44%; }
  50%     { border-radius: 37% 63% 58% 42% / 63% 37% 56% 44%; }
  75%     { border-radius: 63% 37% 42% 58% / 44% 63% 37% 56%; }
}
`);

injectKeyframes('ahfu-spinner-spin', `
@keyframes ahfu-spinner-spin {
  to { transform: rotate(360deg); }
}
`);

// donut — morphing thick-border ring + counter-spin
injectKeyframes('ahfu-spinner-donut-morph', `
@keyframes ahfu-spinner-donut-morph {
  0%,100% { border-radius: 55% 45% 60% 40% / 50% 60% 40% 50%; }
  50%     { border-radius: 45% 55% 40% 60% / 60% 40% 60% 40%; }
}
`);

injectKeyframes('ahfu-spinner-counter-spin', `
@keyframes ahfu-spinner-counter-spin {
  to { transform: rotate(-360deg); }
}
`);

// squish — squash-and-bounce drop
injectKeyframes('ahfu-spinner-squish', `
@keyframes ahfu-spinner-squish {
  0%,100% { transform: scale(1,1)     translateY(0);    border-radius: 50%; }
  35%     { transform: scale(1.25,.75) translateY(8px);  border-radius: 60% 60% 50% 50% / 70% 70% 40% 40%; }
  65%     { transform: scale(.8,1.2)  translateY(-10px); border-radius: 50% 50% 45% 45% / 55% 55% 45% 45%; }
}
`);

// orbit — five-dot rotating orbit (SVG, no keyframe needed beyond spin)

// worm — squiggle dash travel
injectKeyframes('ahfu-spinner-worm', `
@keyframes ahfu-spinner-worm {
  0%   { stroke-dashoffset:  220; }
  50%  { stroke-dashoffset:    0; }
  100% { stroke-dashoffset: -220; }
}
`);

// pill — stretching + rotating pill
injectKeyframes('ahfu-spinner-pill', `
@keyframes ahfu-spinner-pill {
  0%,100% { transform: scale(1,1)   rotate(0deg);  border-radius: 999px; }
  25%     { transform: scale(.6,1.6) rotate(0deg);  border-radius: 50% 50% 60% 60% / 50% 50% 50% 50%; }
  50%     { transform: scale(1,1)   rotate(90deg); border-radius: 999px; }
  75%     { transform: scale(.6,1.6) rotate(90deg); border-radius: 50% 50% 60% 60% / 50% 50% 50% 50%; }
}
`);

/* -------------------------------------------------- */
/*  Variant renderers                                 */
/* -------------------------------------------------- */

interface RendererProps {
  dim: number;
  color: string;
  reducedMotion: boolean;
  filterId: string;
  fullWidth: boolean;
}

function BlobSpinner({ dim, color, reducedMotion }: RendererProps) {
  const morphDur = reducedMotion ? motion.spinner.rotation * 4 : motion.spinner.rotation * 1.7;
  const spinDur  = reducedMotion ? motion.spinner.rotation * 8 : motion.spinner.rotation * 2.8;
  return (
    <div style={{
      width: dim, height: dim,
      background: color,
      borderRadius: '42% 58% 63% 37% / 41% 44% 56% 59%',
      flexShrink: 0,
      animation: reducedMotion
        ? `ahfu-spinner-morph ${morphDur}ms ease-in-out infinite`
        : [
            `ahfu-spinner-morph ${morphDur}ms ease-in-out infinite`,
            `ahfu-spinner-spin  ${spinDur}ms ${easing.standard} infinite`,
          ].join(', '),
    }} />
  );
}

function DonutSpinner({ dim, color, reducedMotion }: RendererProps) {
  const stroke   = Math.max(3, Math.round(dim * 0.22));
  const morphDur = reducedMotion ? motion.spinner.rotation * 4 : motion.spinner.rotation * 1.25;
  const spinDur  = reducedMotion ? motion.spinner.rotation * 8 : motion.spinner.rotation * 1.875;
  return (
    <div style={{
      width: dim, height: dim, flexShrink: 0,
      border: `${stroke}px solid ${color}`,
      borderRadius: '55% 45% 60% 40% / 50% 60% 40% 50%',
      animation: reducedMotion
        ? `ahfu-spinner-donut-morph ${morphDur}ms ease-in-out infinite`
        : [
            `ahfu-spinner-donut-morph ${morphDur}ms ease-in-out infinite`,
            `ahfu-spinner-counter-spin ${spinDur}ms ${easing.standard} infinite`,
          ].join(', '),
    }} />
  );
}

function SquishSpinner({ dim, color, reducedMotion }: RendererProps) {
  const dur = reducedMotion ? motion.spinner.rotation * 3 : 1400;
  return (
    <div style={{
      width: dim, height: dim, flexShrink: 0,
      background: color,
      borderRadius: '50%',
      animation: `ahfu-spinner-squish ${dur}ms cubic-bezier(0.6,0,0.4,1) infinite`,
    }} />
  );
}

function OrbitSpinner({ dim, color, reducedMotion, filterId }: RendererProps) {
  const dur = reducedMotion ? motion.spinner.rotation * 5 : motion.spinner.rotation * 0.9375;
  // Five dots on a circle; sizes decrease to imply depth/trail
  const cx = dim / 2, cy = dim / 2;
  const r  = dim * 0.35;
  type Dot = { angle: number; r: number };
  const dots: Dot[] = [
    { angle: 270, r: dim * 0.1375 },
    { angle: 300, r: dim * 0.0875 },
    { angle: 323, r: dim * 0.0625 },
    { angle: 340, r: dim * 0.05   },
    { angle: 352, r: dim * 0.04   },
  ];
  return (
    <svg width={dim} height={dim} viewBox={`0 0 ${dim} ${dim}`} style={{ flexShrink: 0, overflow: 'visible' }}>
      <defs>
        <filter id={filterId}>
          <feGaussianBlur stdDeviation={dim * 0.04} result="blur" />
          <feColorMatrix in="blur" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 22 -11" />
        </filter>
      </defs>
      <g filter={`url(#${filterId})`}
         style={{
           transformOrigin: `${cx}px ${cy}px`,
           animation: `ahfu-spinner-spin ${dur}ms ${easing.standard} infinite`,
         }}>
        {dots.map(({ angle, r: dr }, i) => {
          const rad = (angle * Math.PI) / 180;
          return (
            <circle
              key={i}
              cx={cx + r * Math.cos(rad)}
              cy={cy + r * Math.sin(rad)}
              r={dr}
              fill={color}
            />
          );
        })}
        <circle cx={cx} cy={cy} r={dim * 0.09} fill={color} />
      </g>
    </svg>
  );
}

function buildWormPath(cycles: number, vbW: number, vbH: number): { d: string; dasharray: number } {
  const mid        = vbH / 2;
  const amp        = vbH * 0.38;
  const wavelength = vbW / cycles;
  const parts: string[] = [];

  parts.push(`M${(wavelength * 0.03).toFixed(2)},${mid.toFixed(2)}`);
  parts.push(`Q${(wavelength * 0.25).toFixed(2)},${(mid - amp).toFixed(2)} ${(wavelength * 0.5).toFixed(2)},${mid.toFixed(2)}`);
  for (let i = 1; i < cycles; i++) {
    parts.push(`T${(wavelength * (0.5 + i)).toFixed(2)},${mid.toFixed(2)}`);
  }

  const arcPerCycle = Math.PI * amp + wavelength * 0.8;
  return { d: parts.join(' '), dasharray: Math.round(arcPerCycle * cycles) };
}

function WormSpinner({ dim, color, reducedMotion, fullWidth }: RendererProps) {
  const dur = reducedMotion ? motion.spinner.rotation * 4 : 2200;
  const h   = Math.max(10, Math.round(dim * 0.4));
  const sw  = Math.max(2, Math.round(dim * 0.12));

  if (fullWidth) {
    // viewBox coordinate space: width=800 height=60 (same proportions as the original worm).
    // preserveAspectRatio="xMidYMid slice" fills the full rendered width while keeping
    // the coordinate aspect ratio intact — amplitude is preserved, path just extends.
    const vbW    = 800;
    const vbH    = 60;
    const cycles = 20; // fills 800 units at 40px/cycle, same density as original
    const { d, dasharray } = buildWormPath(cycles, vbW, vbH);
    const strokeW = sw * (160 / dim); // same weight as non-fullWidth

    return (
      <svg
        width="100%"
        height={h}
        viewBox={`0 0 ${vbW} ${vbH}`}
        preserveAspectRatio="xMidYMid slice"
        style={{ flexShrink: 0, display: 'block' }}
      >
        <path
          d={d}
          fill="none"
          stroke={color}
          strokeWidth={strokeW}
          strokeLinecap="round"
          strokeDasharray={dasharray}
          style={{ animation: `ahfu-spinner-worm ${dur}ms ease-in-out infinite` }}
        />
      </svg>
    );
  }

  return (
    <svg
      width={dim}
      height={h}
      viewBox="0 0 160 60"
      style={{ flexShrink: 0, display: 'block' }}
    >
      <path
        d="M5,30 Q25,5 45,30 T85,30 T125,30 T155,30"
        fill="none"
        stroke={color}
        strokeWidth={sw * (160 / dim)}
        strokeLinecap="round"
        strokeDasharray="220"
        style={{ animation: `ahfu-spinner-worm ${dur}ms ease-in-out infinite` }}
      />
    </svg>
  );
}

function PillSpinner({ dim, color, reducedMotion }: RendererProps) {
  const dur = reducedMotion ? motion.spinner.rotation * 3 : 1600;
  return (
    <div style={{
      width: dim * 2.7, height: dim * 0.94, flexShrink: 0,
      background: color,
      borderRadius: 999,
      animation: `ahfu-spinner-pill ${dur}ms cubic-bezier(0.5,0,0.5,1) infinite`,
    }} />
  );
}

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUSpinner({
  size      = 'medium',
  dim:      dimOverride,
  variant   = 'blob',
  color     = 'var(--primary)',
  label,
  fullWidth = false,
  style: overrideStyle,
}: AHFUSpinnerProps) {
  const reducedMotion = useReducedMotion();
  const uid           = useId();
  const filterId      = `ahfu-orbit-goo-${uid.replace(/:/g, '')}`;

  const resolvedVariant: AHFUSpinnerVariant = useMemo(() => {
    if (variant === 'random') {
      return ALL_VARIANTS[Math.floor(Math.random() * ALL_VARIANTS.length)];
    }
    return variant;
  }, [variant]);

  const dim = dimOverride ?? sizeMap[size];
  const rendererProps: RendererProps = { dim, color, reducedMotion, filterId, fullWidth };

  const inner = {
    blob:   <BlobSpinner   {...rendererProps} />,
    donut:  <DonutSpinner  {...rendererProps} />,
    squish: <SquishSpinner {...rendererProps} />,
    orbit:  <OrbitSpinner  {...rendererProps} />,
    worm:   <WormSpinner   {...rendererProps} />,
    pill:   <PillSpinner   {...rendererProps} />,
  }[resolvedVariant];

  return (
    <div
      role="status"
      aria-label={label ?? 'Loading'}
      style={{
        display: fullWidth ? 'flex' : 'inline-flex',
        flexDirection: 'column',
        alignItems: 'center',
        width: fullWidth ? '100%' : undefined,
        gap: spacing[3],
        ...overrideStyle,
      }}
    >
      {inner}
      {label && (
        <span style={{ fontSize: fontSizes.sm, color: 'var(--text-muted)', fontWeight: 500 }}>
          {label}
        </span>
      )}
    </div>
  );
}
