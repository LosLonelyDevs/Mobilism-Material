import type React from 'react';
import type { SpacingValue } from './tokens';

type AfSide = 'all' | 'horiz' | 'vert' | 'top' | 'bottom' | 'left' | 'right';

export interface AfSpacingToken {
  _prop: 'padding' | 'margin';
  _sides: AfSide;
  _value: SpacingValue;
}

export type AfPaddingToken = AfSpacingToken & { _prop: 'padding' };
export type AfMarginToken = AfSpacingToken & { _prop: 'margin' };

const SPACING_VALUES: SpacingValue[] = [0, 2, 4, 8, 12, 16, 20, 24, 32, 40, 48];
const SIDES: AfSide[] = ['all', 'horiz', 'vert', 'top', 'bottom', 'left', 'right'];

type TokenMap<P extends 'padding' | 'margin'> = {
  [K in `${'all' | 'horiz' | 'vert' | 'top' | 'bottom' | 'left' | 'right'}${SpacingValue}`]: AfSpacingToken & { _prop: P };
};

function buildTokens<P extends 'padding' | 'margin'>(prop: P): TokenMap<P> {
  const map = {} as TokenMap<P>;
  for (const side of SIDES) {
    for (const value of SPACING_VALUES) {
      const key = `${side}${value}` as keyof TokenMap<P>;
      map[key] = { _prop: prop, _sides: side, _value: value } as TokenMap<P>[typeof key];
    }
  }
  return map;
}

export const afPadding = buildTokens('padding');
export const afMargin = buildTokens('margin');

export function resolveSpacingTokens(
  tokens: AfSpacingToken | AfSpacingToken[],
): React.CSSProperties {
  const list = Array.isArray(tokens) ? tokens : [tokens];
  const style: React.CSSProperties = {};

  for (const token of list) {
    const p = token._prop;
    const v = token._value;
    switch (token._sides) {
      case 'all':
        (style as Record<string, number>)[p] = v;
        break;
      case 'horiz':
        (style as Record<string, number>)[p === 'padding' ? 'paddingLeft' : 'marginLeft'] = v;
        (style as Record<string, number>)[p === 'padding' ? 'paddingRight' : 'marginRight'] = v;
        break;
      case 'vert':
        (style as Record<string, number>)[p === 'padding' ? 'paddingTop' : 'marginTop'] = v;
        (style as Record<string, number>)[p === 'padding' ? 'paddingBottom' : 'marginBottom'] = v;
        break;
      case 'top':
        (style as Record<string, number>)[p === 'padding' ? 'paddingTop' : 'marginTop'] = v;
        break;
      case 'bottom':
        (style as Record<string, number>)[p === 'padding' ? 'paddingBottom' : 'marginBottom'] = v;
        break;
      case 'left':
        (style as Record<string, number>)[p === 'padding' ? 'paddingLeft' : 'marginLeft'] = v;
        break;
      case 'right':
        (style as Record<string, number>)[p === 'padding' ? 'paddingRight' : 'marginRight'] = v;
        break;
    }
  }

  return style;
}
