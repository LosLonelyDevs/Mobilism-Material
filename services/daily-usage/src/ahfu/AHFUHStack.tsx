import type { ReactNode, HTMLAttributes } from 'react';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';

export type AHFUHStackAlign = 'start' | 'center' | 'end' | 'baseline' | 'stretch';
export type AHFUHStackJustify = 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';

export interface AHFUHStackProps extends Omit<HTMLAttributes<HTMLDivElement>, 'style'> {
  children: ReactNode;
  gap?: number;
  align?: AHFUHStackAlign;
  justify?: AHFUHStackJustify;
  wrap?: boolean;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

const alignMap: Record<AHFUHStackAlign, React.CSSProperties['alignItems']> = {
  start: 'flex-start', center: 'center', end: 'flex-end', baseline: 'baseline', stretch: 'stretch',
};
const justifyMap: Record<AHFUHStackJustify, React.CSSProperties['justifyContent']> = {
  start: 'flex-start', center: 'center', end: 'flex-end',
  between: 'space-between', around: 'space-around', evenly: 'space-evenly',
};

export default function AHFUHStack({
  children,
  gap = 8,
  align = 'center',
  justify = 'start',
  wrap = false,
  padding,
  margin,
  style: overrideStyle,
  ...rest
}: AHFUHStackProps) {
  const style: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'row',
    alignItems: alignMap[align],
    justifyContent: justifyMap[justify],
    flexWrap: wrap ? 'wrap' : 'nowrap',
    gap,
    ...(padding ? resolveSpacingTokens(padding) : {}),
    ...(margin ? resolveSpacingTokens(margin) : {}),
    ...overrideStyle,
  };
  return <div style={style} {...rest}>{children}</div>;
}
