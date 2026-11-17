import type { ReactNode, HTMLAttributes } from 'react';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';

export type AHFUBoxSurface = 'none' | 'surface' | 'surfaceAlt' | 'background';

export interface AHFUBoxProps extends Omit<HTMLAttributes<HTMLDivElement>, 'style'> {
  children?: ReactNode;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  surface?: AHFUBoxSurface;
  radius?: number;
  border?: boolean;
  style?: React.CSSProperties;
}

const surfaceMap: Record<AHFUBoxSurface, string | undefined> = {
  none: undefined,
  surface: 'var(--surface)',
  surfaceAlt: 'var(--surface-alt)',
  background: 'var(--background)',
};

export default function AHFUBox({
  children,
  padding,
  margin,
  surface = 'none',
  radius,
  border,
  style: overrideStyle,
  ...rest
}: AHFUBoxProps) {
  const style: React.CSSProperties = {
    ...(padding ? resolveSpacingTokens(padding) : {}),
    ...(margin ? resolveSpacingTokens(margin) : {}),
    background: surfaceMap[surface],
    borderRadius: radius,
    border: border ? '1px solid var(--border)' : undefined,
    ...overrideStyle,
  };
  return <div style={style} {...rest}>{children}</div>;
}
