import type { ReactNode, HTMLAttributes } from 'react';
import { fontSizes, fontWeights } from './tokens.ts';

export type AHFUHeadingLevel = 1 | 2 | 3 | 4 | 5 | 6;

export interface AHFUHeadingProps extends Omit<HTMLAttributes<HTMLHeadingElement>, 'style'> {
  children: ReactNode;
  level?: AHFUHeadingLevel;
  style?: React.CSSProperties;
}

const levelConfig: Record<AHFUHeadingLevel, { size: number; weight: number; lineHeight: number }> = {
  1: { size: fontSizes['2xl'], weight: fontWeights.extrabold, lineHeight: 24 },
  2: { size: fontSizes.xl,     weight: fontWeights.bold,      lineHeight: 24 },
  3: { size: fontSizes.lg,     weight: fontWeights.bold,      lineHeight: 20 },
  4: { size: fontSizes.base,   weight: fontWeights.bold,      lineHeight: 20 },
  5: { size: fontSizes.md,     weight: fontWeights.semibold,  lineHeight: 18 },
  6: { size: fontSizes.sm,     weight: fontWeights.semibold,  lineHeight: 16 },
};

export default function AHFUHeading({
  children,
  level = 2,
  style: overrideStyle,
  ...rest
}: AHFUHeadingProps) {
  const c = levelConfig[level];
  const Tag = (`h${level}`) as 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';

  const style: React.CSSProperties = {
    fontSize: c.size,
    fontWeight: c.weight,
    lineHeight: `${c.lineHeight}px`,
    color: 'var(--text-primary)',
    margin: 0,
    ...overrideStyle,
  };

  return <Tag style={style} {...rest}>{children}</Tag>;
}
