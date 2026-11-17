import type { CSSProperties } from 'react';
import './responsive.css';

/* ================================================== */
/*  AHFU Design Tokens                                */
/*  Standardized spacing, sizing, and radii values.   */
/*  Import and use these instead of raw numbers to    */
/*  enforce visual consistency across components.     */
/* ================================================== */

/**
 * Allowed spacing values (px).
 * Use for margin, padding, gap.
 */
export const spacing = {
  0: 0,
  1: 2,
  2: 4,
  3: 8,
  4: 12,
  5: 16,
  6: 20,
  7: 24,
  8: 32,
  9: 40,
  10: 48,
} as const;

export type SpacingKey = keyof typeof spacing;
export type SpacingValue = (typeof spacing)[SpacingKey];

/**
 * Border radius scale (px).
 */
export const radii = {
  none: 0,
  xs: 4,
  sm: 6,
  md: 8,
  lg: 10,
  xl: 12,
  full: 999,
} as const;

export type RadiiKey = keyof typeof radii;

/**
 * Font size scale (px).
 */
export const fontSizes = {
  xs: 11,
  sm: 12,
  md: 13,
  base: 14,
  lg: 16,
  xl: 18,
  '2xl': 20,
  '3xl': 24,
  '4xl': 36,
  '5xl': 40,
} as const;

export type FontSizeKey = keyof typeof fontSizes;

/**
 * Font weight scale — mirrors CSS custom properties.
 */
export const fontWeights = {
  normal: 500,
  medium: 600,
  semibold: 650,
  bold: 700,
  extrabold: 800,
} as const;

export type FontWeightKey = keyof typeof fontWeights;

// Global motion floor: every built-in AHFU animation is at least MIN_DURATION
// ms, so nothing ever feels snappy. The whole motion system reads from this
// scale (directly or via transition()), so changing MIN_DURATION retunes it
// all at once. (slow/xslow sit above the floor for large/page-level motion.)
export const MIN_DURATION = 750;
// Hover/focus and other "effect" transitions (opacity, colour, hover elevation)
// run on a faster, INDEPENDENT clock so feedback feels instant even though
// spatial/expressive motion (ripple, shape-morph, slides) stays deliberate.
export const STATE_DURATION = 250;
export const TABLE_HOVER_DURATION = 50;
export const duration = {
  instant:  750,
  fast:     750,
  quick:    750,
  normal:   750,
  moderate: 750,
  relaxed:  750,
  slow:     800,
  xslow:    900,
} as const;
export type DurationKey = keyof typeof duration;

// Per-component motion. Interaction durations are floored to MIN_DURATION (750);
// genuine hover-intent delays (tooltip.delay) and continuous loops (shimmer,
// pulse, spinner) keep their own timings.
export const motion = {
  input: {
    focus: 750,
    option: 750,
  },
  sidebar: {
    filter: 750,
  },
  dropdown: {
    entrance: 750,
  },
  tooltip: {
    delay: 300,
    transition: 750,
  },
  modal: {
    enter: 750,
    exit: 750,
  },
  toast: {
    enter: 750,
    exit: 750,
  },
  tabs: {
    indicator: 750,
  },
  collapsible: {
    body: 750,
    header: 750,
  },
  codeBlock: {
    fade: 750,
  },
  skeleton: {
    shimmer: 2200,
  },
  newDot: {
    pulse: 3000,
  },
  spinner: {
    rotation: 3200,
    button: 800,
  },
} as const;

export const easing = {
  standard:   'cubic-bezier(0.2, 0, 0, 1)',
  decelerate: 'cubic-bezier(0.05, 0.7, 0.1, 1)',
  accelerate: 'cubic-bezier(0.3, 0, 0.8, 0.15)',
  overshoot:  'cubic-bezier(0.34, 1.56, 0.64, 1)',
  linear:     'linear',
} as const;
export type EasingKey = keyof typeof easing;

export function transition(
  properties: string | string[],
  dur: DurationKey,
  ease: EasingKey = 'standard',
): string {
  const props = Array.isArray(properties) ? properties : [properties];
  const d = `${duration[dur]}ms`;
  const e = easing[ease];
  return props.map((p) => `${p} ${d} ${e}`).join(', ');
}

/**
 * Fast transition for hover/focus/colour feedback — runs at STATE_DURATION
 * (250ms), independent of the 750ms spatial floor. Use for opacity, colour,
 * background, border, and hover-elevation changes.
 */
export function stateTransition(properties: string | string[], ease: EasingKey = 'standard'): string {
  const props = Array.isArray(properties) ? properties : [properties];
  const e = easing[ease];
  return props.map((p) => `${p} ${STATE_DURATION}ms ${e}`).join(', ');
}

/** @deprecated Use the transition() builder instead */
export const transitions = {
  fast:   transition(['color', 'background-color', 'border-color', 'opacity', 'box-shadow'], 'quick'),
  normal: transition(['color', 'background-color', 'border-color', 'opacity', 'box-shadow'], 'normal'),
  slow:   transition(['color', 'background-color', 'border-color', 'opacity', 'box-shadow'], 'relaxed'),
} as const;

export const stateLayer = {
  hover:   0.08,
  focus:   0.10,
  pressed: 0.10,
  dragged: 0.16,
} as const;

export const disabled = {
  content:   0.38,
  container: 0.12,
} as const;

const fw = (v: string): CSSProperties['fontWeight'] => v as CSSProperties['fontWeight'];

// Type roles read from configurable CSS variables (defaults in index.css :root,
// tuned live by the Font Samples typography panel via applyTypeConfig), so
// size / weight / line-height / tracking propagate to every component at once.
export const typeRole = {
  displayLarge:   { fontSize: 'calc(var(--ts-heading) * 2)',   fontWeight: fw('var(--fw-heading)'),     lineHeight: 1.1,  letterSpacing: 'var(--ts-tracking)' },
  displayMedium:  { fontSize: 'calc(var(--ts-heading) * 1.8)', fontWeight: fw('var(--fw-heading)'),     lineHeight: 1.15, letterSpacing: 'var(--ts-tracking)' },
  headingLarge:   { fontSize: 'var(--ts-heading)',             fontWeight: fw('var(--fw-heading)'),     lineHeight: 1.25, letterSpacing: 'var(--ts-tracking)' },
  headingMedium:  { fontSize: 'calc(var(--ts-heading) * 0.9)', fontWeight: fw('var(--fw-heading)'),     lineHeight: 1.3,  letterSpacing: 'var(--ts-tracking)' },
  headingSmall:   { fontSize: 'calc(var(--ts-heading) * 0.8)', fontWeight: fw('var(--fw-heading)'),     lineHeight: 1.35, letterSpacing: '0' },
  titleLarge:     { fontSize: 'var(--ts-title)',               fontWeight: fw('var(--fw-title, 600)'),  lineHeight: 1.4,  letterSpacing: '0' },
  titleMedium:    { fontSize: 'calc(var(--ts-title) * 0.875)', fontWeight: fw('var(--fw-title, 600)'),  lineHeight: 1.45, letterSpacing: '0.1px' },
  titleSmall:     { fontSize: 'calc(var(--ts-title) * 0.8)',   fontWeight: fw('var(--fw-title, 600)'),  lineHeight: 1.45, letterSpacing: '0.1px' },
  labelLarge:     { fontSize: 'var(--ts-label)',               fontWeight: fw('var(--fw-label, 650)'),  lineHeight: 1.4,  letterSpacing: '0.1px' },
  labelMedium:    { fontSize: 'calc(var(--ts-label) * 0.92)',  fontWeight: fw('var(--fw-label, 650)'),  lineHeight: 1.35, letterSpacing: '0.4px' },
  labelSmall:     { fontSize: 'calc(var(--ts-label) * 0.85)',  fontWeight: fw('var(--fw-label, 650)'),  lineHeight: 1.4,  letterSpacing: '0.4px' },
  bodyLarge:      { fontSize: 'var(--ts-body)',                fontWeight: fw('var(--fw-body)'), lineHeight: 'var(--ts-line-height)', letterSpacing: '0' },
  bodyMedium:     { fontSize: 'calc(var(--ts-body) * 0.92)',   fontWeight: fw('var(--fw-body)'), lineHeight: 'var(--ts-line-height)', letterSpacing: '0' },
  bodySmall:      { fontSize: 'calc(var(--ts-body) * 0.85)',   fontWeight: fw('var(--fw-body)'), lineHeight: 'var(--ts-line-height)', letterSpacing: '0' },
} satisfies Record<string, CSSProperties>;
export type TypeRoleKey = keyof typeof typeRole;

export const shape = {
  none: 0,
  xs:   4,
  sm:   8,
  md:   12,
  lg:   16,
  xl:   28,
  pill: 999,
} as const;
export type ShapeKey = keyof typeof shape;

/**
 * Z-index scale for layering.
 */
export const zIndex = {
  base: 0,
  dropdown: 100,
  sticky: 200,
  overlay: 500,
  modal: 1000,
  toast: 9999,
} as const;
