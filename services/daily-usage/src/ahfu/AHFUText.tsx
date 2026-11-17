import type { ReactNode, HTMLAttributes } from 'react';
import { fontSizes, fontWeights } from './tokens.ts';

export type AHFUTextVariant = 'body' | 'bodyLarge' | 'emphasized' | 'supporting' | 'code';
export type AHFUTextTone = 'primary' | 'secondary' | 'muted' | 'danger' | 'success' | 'warning' | 'info';

export interface AHFUTextProps extends Omit<HTMLAttributes<HTMLSpanElement>, 'style'> {
  children: ReactNode;
  variant?: AHFUTextVariant;
  tone?: AHFUTextTone;
  as?: 'span' | 'p' | 'div' | 'label' | 'code';
  style?: React.CSSProperties;
}

const variantConfig: Record<AHFUTextVariant, React.CSSProperties> = {
  body:       { fontSize: fontSizes.base, fontWeight: fontWeights.normal,   lineHeight: 1.5,    fontFamily: 'var(--sans)' },
  bodyLarge:  { fontSize: fontSizes.lg,   fontWeight: fontWeights.normal,   lineHeight: 1.5,    fontFamily: 'var(--sans)' },
  emphasized: { fontSize: fontSizes.base, fontWeight: fontWeights.bold,     lineHeight: 1.5,    fontFamily: 'var(--sans)', letterSpacing: '-0.24px' },
  supporting: { fontSize: fontSizes.sm,   fontWeight: fontWeights.normal,   lineHeight: 1.5,    fontFamily: 'var(--sans)' },
  code:       { fontSize: fontSizes.base, fontWeight: fontWeights.normal,   lineHeight: 1.5,    fontFamily: 'var(--mono)' },
};

const toneConfig: Record<AHFUTextTone, string> = {
  primary:   'var(--text-primary)',
  secondary: 'var(--text-secondary)',
  muted:     'var(--text-muted)',
  danger:    'var(--danger)',
  success:   'var(--success)',
  warning:   'var(--warning)',
  info:      'var(--info)',
};

export default function AHFUText({
  children,
  variant = 'body',
  tone = 'primary',
  as = 'span',
  style: overrideStyle,
  ...rest
}: AHFUTextProps) {
  const Tag = as;
  const style: React.CSSProperties = {
    ...variantConfig[variant],
    color: toneConfig[tone],
    ...overrideStyle,
  };

  return <Tag style={style} {...rest}>{children}</Tag>;
}
