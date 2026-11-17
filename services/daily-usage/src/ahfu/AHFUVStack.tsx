import type { ReactNode, HTMLAttributes } from 'react';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';

export type AHFUVStackAlign = 'start' | 'center' | 'end' | 'stretch';
export type AHFUVStackJustify = 'start' | 'center' | 'end' | 'between' | 'around' | 'evenly';

export interface AHFUVStackProps extends Omit<HTMLAttributes<HTMLDivElement>, 'style'> {
  children: ReactNode;
  gap?: number;
  align?: AHFUVStackAlign;
  justify?: AHFUVStackJustify;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

const alignMap: Record<AHFUVStackAlign, React.CSSProperties['alignItems']> = {
  start: 'flex-start', center: 'center', end: 'flex-end', stretch: 'stretch',
};
const justifyMap: Record<AHFUVStackJustify, React.CSSProperties['justifyContent']> = {
  start: 'flex-start', center: 'center', end: 'flex-end',
  between: 'space-between', around: 'space-around', evenly: 'space-evenly',
};

export default function AHFUVStack({
  children,
  gap = 8,
  align = 'stretch',
  justify = 'start',
  padding,
  margin,
  style: overrideStyle,
  ...rest
}: AHFUVStackProps) {
  const style: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: alignMap[align],
    justifyContent: justifyMap[justify],
    gap,
    ...(padding ? resolveSpacingTokens(padding) : {}),
    ...(margin ? resolveSpacingTokens(margin) : {}),
    ...overrideStyle,
  };
  return <div style={style} {...rest}>{children}</div>;
}
