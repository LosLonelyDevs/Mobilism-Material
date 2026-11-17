import { injectKeyframes } from './utils/injectKeyframes.ts';
import { useReducedMotion } from './hooks/useReducedMotion.ts';
import { radii, motion } from './tokens.ts';

/* -------------------------------------------------- */
/*  Shared SVG filters (injected once)                */
/* -------------------------------------------------- */

// Injected as a hidden SVG into the document body on first render.
let filtersInjected = false;
function injectOrgFilters() {
  if (filtersInjected || typeof document === 'undefined') return;
  filtersInjected = true;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '0');
  svg.setAttribute('height', '0');
  svg.setAttribute('aria-hidden', 'true');
  svg.style.position = 'absolute';
  svg.innerHTML = `
    <defs>
      <filter id="ahfu-sk-line" x="-20%" y="-150%" width="140%" height="400%" color-interpolation-filters="sRGB">
        <feTurbulence type="fractalNoise" baseFrequency="0.024 0.05" numOctaves="2" seed="3" result="n">
          <animate attributeName="baseFrequency" dur="6s" repeatCount="indefinite"
                   values="0.018 0.04;0.034 0.06;0.022 0.045;0.018 0.04"/>
        </feTurbulence>
        <feGaussianBlur in="SourceGraphic" stdDeviation="0.4" result="src"/>
        <feDisplacementMap in="src" in2="n" scale="10" result="d"/>
        <feGaussianBlur in="d" stdDeviation="0.5"/>
      </filter>
      <filter id="ahfu-sk-blob" x="-50%" y="-50%" width="200%" height="200%" color-interpolation-filters="sRGB">
        <feTurbulence type="fractalNoise" baseFrequency="0.022" numOctaves="2" seed="7" result="n">
          <animate attributeName="baseFrequency" dur="7s" repeatCount="indefinite"
                   values="0.018;0.030;0.020;0.018"/>
        </feTurbulence>
        <feGaussianBlur in="SourceGraphic" stdDeviation="0.5" result="src"/>
        <feDisplacementMap in="src" in2="n" scale="9" result="d"/>
        <feGaussianBlur in="d" stdDeviation="0.55"/>
      </filter>
      <filter id="ahfu-sk-rect" x="-30%" y="-30%" width="160%" height="160%" color-interpolation-filters="sRGB">
        <feTurbulence type="fractalNoise" baseFrequency="0.012" numOctaves="2" seed="1" result="n">
          <animate attributeName="baseFrequency" dur="9s" repeatCount="indefinite"
                   values="0.010;0.018;0.012;0.010"/>
        </feTurbulence>
        <feGaussianBlur in="SourceGraphic" stdDeviation="0.6" result="src"/>
        <feDisplacementMap in="src" in2="n" scale="14" result="d"/>
        <feGaussianBlur in="d" stdDeviation="0.65"/>
      </filter>
    </defs>
  `;
  document.body.appendChild(svg);
}

/* -------------------------------------------------- */
/*  Keyframes                                         */
/* -------------------------------------------------- */

injectKeyframes('ahfu-skeleton-shimmer', `
@keyframes ahfu-skeleton-shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
`);

injectKeyframes('ahfu-sk-blob-morph', `
@keyframes ahfu-sk-blob-morph {
  0%,100% { border-radius: 55% 45% 60% 40% / 50% 55% 45% 50%; }
  25%     { border-radius: 45% 55% 40% 60% / 55% 45% 55% 45%; }
  50%     { border-radius: 60% 40% 55% 45% / 40% 60% 50% 50%; }
  75%     { border-radius: 50% 50% 45% 55% / 60% 40% 60% 40%; }
}
`);

injectKeyframes('ahfu-sk-blob-breathe', `
@keyframes ahfu-sk-blob-breathe {
  0%,100% { transform: scale(0.93) rotate(-2deg); }
  50%     { transform: scale(1.0)  rotate(2deg);  }
}
`);

injectKeyframes('ahfu-sk-rect-morph', `
@keyframes ahfu-sk-rect-morph {
  0%,100% { border-radius: 28% 32% 30% 34% / 34% 28% 32% 30%; }
  50%     { border-radius: 34% 28% 32% 30% / 28% 34% 30% 32%; }
}
`);

injectKeyframes('ahfu-sk-rect-breathe', `
@keyframes ahfu-sk-rect-breathe {
  0%,100% { transform: scale(0.97) rotate(-0.5deg); }
  50%     { transform: scale(1.0)  rotate(0.5deg);  }
}
`);

injectKeyframes('ahfu-sk-line-undulate', `
@keyframes ahfu-sk-line-undulate {
  0%,100% { transform: scaleY(1)    translateY(0);    }
  50%     { transform: scaleY(1.07) translateY(-1px); }
}
`);

injectKeyframes('ahfu-sk-pill-pulse', `
@keyframes ahfu-sk-pill-pulse {
  0%,100% { transform: scaleX(1)    scaleY(1);   }
  50%     { transform: scaleX(1.04) scaleY(0.9); }
}
`);

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUSkeletonVariant = 'text' | 'circular' | 'rectangular' | 'rounded' | 'pill';

export interface AHFUSkeletonProps {
  variant?: AHFUSkeletonVariant;
  width?: number | string;
  height?: number | string;
  lines?: number;
  animationDelay?: string;
  style?: React.CSSProperties;
  className?: string;
}

/* -------------------------------------------------- */
/*  Shimmer background (subtle, layered over organic) */
/* -------------------------------------------------- */

function shimmerBg(reducedMotion: boolean): React.CSSProperties {
  if (reducedMotion) return { background: 'color-mix(in srgb, var(--border) 55%, var(--surface-alt))' };
  return {
    background: `linear-gradient(
      90deg,
      color-mix(in srgb, var(--border) 55%, var(--surface-alt)) 0%,
      color-mix(in srgb, var(--border) 25%, var(--surface-alt)) 50%,
      color-mix(in srgb, var(--border) 55%, var(--surface-alt)) 100%
    )`,
    backgroundSize: '200% 100%',
    animation: `ahfu-skeleton-shimmer ${motion.skeleton.shimmer}ms ease-in-out infinite`,
  };
}

/* -------------------------------------------------- */
/*  Primitive bones                                   */
/* -------------------------------------------------- */

interface BoneProps {
  width: number | string;
  height: number | string;
  reducedMotion: boolean;
  animationDelay?: string;
  style?: React.CSSProperties;
}

// Wavy text line — pill shape + turbulence edge + undulate
export function SkLine({ width, height = 16, reducedMotion, animationDelay, style }: BoneProps) {
  injectOrgFilters();
  const dur = reducedMotion ? 'none' : `ahfu-sk-line-undulate 3.6s ease-in-out infinite`;
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: 999,
        filter: reducedMotion ? undefined : 'url(#ahfu-sk-line)',
        animation: reducedMotion ? 'none' : dur,
        animationDelay,
        transformOrigin: 'left center',
        ...shimmerBg(reducedMotion),
        ...style,
      }}
    />
  );
}

// Amoeboid blob — for avatars / circular elements
export function SkBlob({ width = 40, height = 40, reducedMotion, animationDelay, style }: BoneProps) {
  injectOrgFilters();
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: '55% 45% 60% 40% / 50% 55% 45% 50%',
        filter: reducedMotion ? undefined : 'url(#ahfu-sk-blob)',
        animation: reducedMotion ? 'none' : [
          `ahfu-sk-blob-morph   6s   ease-in-out infinite`,
          `ahfu-sk-blob-breathe 3.4s ease-in-out infinite`,
          `ahfu-skeleton-shimmer ${motion.skeleton.shimmer}ms ease-in-out infinite`,
        ].join(', '),
        animationDelay,
        backgroundSize: '200% 100%',
        ...shimmerBg(reducedMotion),
        ...style,
      }}
    />
  );
}

// Amoeboid rectangle — for images, cards, thumbnails
export function SkRect({ width = '100%', height = 120, reducedMotion, animationDelay, style }: BoneProps) {
  injectOrgFilters();
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: '28px 36px 30px 34px / 32px 28px 36px 30px',
        filter: reducedMotion ? undefined : 'url(#ahfu-sk-rect)',
        animation: reducedMotion ? 'none' : [
          `ahfu-sk-rect-morph   8s ease-in-out infinite`,
          `ahfu-sk-rect-breathe 5s ease-in-out infinite`,
          `ahfu-skeleton-shimmer ${motion.skeleton.shimmer}ms ease-in-out infinite`,
        ].join(', '),
        animationDelay,
        backgroundSize: '200% 100%',
        ...shimmerBg(reducedMotion),
        ...style,
      }}
    />
  );
}

// Pulsing pill — for buttons, chips, tags
export function SkPill({ width = 100, height = 36, reducedMotion, animationDelay, style }: BoneProps) {
  injectOrgFilters();
  return (
    <span
      aria-hidden="true"
      style={{
        display: 'block',
        width,
        height,
        borderRadius: 999,
        filter: reducedMotion ? undefined : 'url(#ahfu-sk-blob)',
        animation: reducedMotion ? 'none' : [
          `ahfu-sk-pill-pulse        2.6s ease-in-out infinite`,
          `ahfu-skeleton-shimmer ${motion.skeleton.shimmer}ms ease-in-out infinite`,
        ].join(', '),
        animationDelay,
        backgroundSize: '200% 100%',
        ...shimmerBg(reducedMotion),
        ...style,
      }}
    />
  );
}

/* -------------------------------------------------- */
/*  Public AHFUSkeleton component                     */
/* -------------------------------------------------- */

const STAGGER = ['0s', '-0.3s', '-0.9s', '-1.6s', '-2.4s'];

export default function AHFUSkeleton({
  variant  = 'text',
  width,
  height,
  lines    = 1,
  animationDelay,
  style: overrideStyle,
  className,
}: AHFUSkeletonProps) {
  const reducedMotion = useReducedMotion();

  const defaults: Record<AHFUSkeletonVariant, { w: number | string; h: number | string }> = {
    text:        { w: '100%', h: 16  },
    circular:    { w: 40,     h: 40  },
    rectangular: { w: '100%', h: 120 },
    rounded:     { w: '100%', h: 100 },
    pill:        { w: 100,    h: 36  },
  };

  const w = width  ?? defaults[variant].w;
  const h = height ?? defaults[variant].h;

  const sharedProps = { reducedMotion, animationDelay, style: overrideStyle };

  if (variant === 'text' && lines > 1) {
    return (
      <span
        role="status"
        aria-label="Loading"
        className={className}
        style={{ display: 'flex', flexDirection: 'column', gap: 10, width: w }}
      >
        {Array.from({ length: lines }).map((_, i) => (
          <SkLine
            key={i}
            width={i === lines - 1 ? '68%' : '100%'}
            height={h}
            reducedMotion={reducedMotion}
            animationDelay={STAGGER[i % STAGGER.length]}
          />
        ))}
      </span>
    );
  }

  const bone = (() => {
    switch (variant) {
      case 'circular':    return <SkBlob {...sharedProps} width={w} height={h} />;
      case 'rectangular': return <SkRect {...sharedProps} width={w} height={h} />;
      case 'rounded':     return <SkRect {...sharedProps} width={w} height={h} style={{ ...overrideStyle, borderRadius: radii.xl }} />;
      case 'pill':        return <SkPill {...sharedProps} width={w} height={h} />;
      default:            return <SkLine {...sharedProps} width={w} height={h} />;
    }
  })();

  return (
    <span role="status" aria-label="Loading" className={className}>
      {bone}
    </span>
  );
}

/* -------------------------------------------------- */
/*  Compound presets                                  */
/* -------------------------------------------------- */

export function AHFUSkeletonCard({ style }: { style?: React.CSSProperties }) {
  const reducedMotion = useReducedMotion();
  return (
    <div
      role="status"
      aria-label="Loading"
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
        padding: 16,
        border: '1px solid var(--border)',
        borderRadius: radii.xl,
        background: 'var(--surface)',
        ...style,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <SkBlob width={40} height={40} reducedMotion={reducedMotion} />
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
          <SkLine width="60%" height={14} reducedMotion={reducedMotion} animationDelay="-0.3s" />
          <SkLine width="40%" height={12} reducedMotion={reducedMotion} animationDelay="-0.9s" />
        </div>
      </div>
      <SkLine width="100%" height={12} reducedMotion={reducedMotion} animationDelay="-0.3s" />
      <SkLine width="90%"  height={12} reducedMotion={reducedMotion} animationDelay="-0.9s" />
      <SkLine width="70%"  height={12} reducedMotion={reducedMotion} animationDelay="-1.6s" />
    </div>
  );
}

export function AHFUSkeletonListItem({ style }: { style?: React.CSSProperties }) {
  const reducedMotion = useReducedMotion();
  return (
    <div
      role="status"
      aria-label="Loading"
      style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0', ...style }}
    >
      <SkBlob width={36} height={36} reducedMotion={reducedMotion} />
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <SkLine width="50%" height={13} reducedMotion={reducedMotion} animationDelay="-0.3s" />
        <SkLine width="75%" height={11} reducedMotion={reducedMotion} animationDelay="-0.9s" />
      </div>
    </div>
  );
}
