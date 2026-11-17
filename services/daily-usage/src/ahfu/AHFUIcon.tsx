import type { ReactNode, HTMLAttributes } from 'react';
import { transition } from './tokens.ts';

export type AHFUIconSize = 12 | 14 | 16 | 18 | 20 | 24 | 28 | 32;

export interface AHFUIconProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'style' | 'children'> {
  children: ReactNode;
  size?: AHFUIconSize;
  color?: string;
  label?: string;
  /** Material Symbols FILL axis: 0 = outline, 1 = filled */
  fill?: 0 | 1;
  /** Material Symbols wght (weight) axis */
  weight?: 100 | 200 | 300 | 400 | 500 | 600 | 700;
  /** Material Symbols GRAD (grade) axis */
  grade?: -25 | 0 | 200;
  /** Material Symbols opsz (optical size) axis */
  opticalSize?: 20 | 24 | 40 | 48;
  style?: React.CSSProperties;
}

// These picker icons stay available without loading an external font.
const pickerPaths: Record<string, string> = {
  close: 'm6 6 12 12M6 18 18 6',
  chevron_left: 'm15 6-6 6 6 6',
  chevron_right: 'm9 6 6 6-6 6',
  expand_more: 'm6 9 6 6 6-6',
  schedule: 'M12 8v4l3 2M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0',
  calendar_month: 'M8 3v4m8-4v4M4 10h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1M8 14h2m4 0h2m-8 3h2m4 0h2',
};

export default function AHFUIcon({
  children,
  size = 16,
  color,
  label,
  fill,
  weight,
  grade,
  opticalSize,
  style: overrideStyle,
  ...rest
}: AHFUIconProps) {
  // Compute font-variation-settings if any axis props are provided
  const hasFontVariation = fill !== undefined || weight !== undefined || grade !== undefined || opticalSize !== undefined;
  const fontVariationSettings = hasFontVariation
    ? `'FILL' ${fill ?? 0}, 'wght' ${weight ?? 400}, 'GRAD' ${grade ?? 0}, 'opsz' ${opticalSize ?? 24}`
    : undefined;

  // Add transition on font-variation-settings when fill is animated
  const fvsTransition = fill !== undefined ? transition('font-variation-settings', 'quick') : undefined;

  const style: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: size,
    height: size,
    color: color ?? 'currentColor',
    fontFamily: '"Material Symbols Rounded"',
    fontSize: size,
    lineHeight: 1,
    fontStyle: 'normal',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textTransform: 'none',
    fontFeatureSettings: "'liga' 1",
    flexShrink: 0,
    ...(fontVariationSettings && { fontVariationSettings }),
    ...(fvsTransition && { transition: fvsTransition }),
    ...overrideStyle,
  };

  return (
    <span
      role={label ? 'img' : 'presentation'}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      style={style}
      {...rest}
    >
      {typeof children === 'string' && pickerPaths[children] ? <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={pickerPaths[children]} /></svg> : children}
    </span>
  );
}
