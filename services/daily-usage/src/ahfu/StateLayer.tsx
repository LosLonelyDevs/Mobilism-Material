import React, { useState, useCallback, type ReactNode, type HTMLAttributes } from 'react';
import { stateLayer as stateOpacity, stateTransition } from './tokens.ts';
import Ripple from './Ripple.tsx';

type InteractionState = 'default' | 'hover' | 'focus' | 'pressed';

export interface StateLayerProps extends HTMLAttributes<HTMLElement> {
  href?: string;
  target?: string;
  rel?: string;
  type?: 'button' | 'submit' | 'reset';
  as?: keyof React.JSX.IntrinsicElements;
  color?: string;
  disabled?: boolean;
  /** Show the Material touch ripple on press. Default true. */
  ripple?: boolean;
  children: ReactNode;
}

export default function StateLayer({
  as: Tag = 'div',
  color = 'var(--state-on-surface)',
  disabled = false,
  ripple = true,
  children,
  style,
  onMouseEnter,
  onMouseLeave,
  onMouseDown,
  onMouseUp,
  onFocus,
  onBlur,
  ...rest
}: StateLayerProps) {
  const [state, setState] = useState<InteractionState>('default');

  const opacity = disabled ? 0 : (
    state === 'hover'   ? stateOpacity.hover   :
    state === 'focus'   ? stateOpacity.focus   :
    state === 'pressed' ? stateOpacity.pressed :
    0
  );

  const handleMouseEnter = useCallback((e: React.MouseEvent<HTMLElement>) => {
    setState('hover');
    onMouseEnter?.(e);
  }, [onMouseEnter]);

  const handleMouseLeave = useCallback((e: React.MouseEvent<HTMLElement>) => {
    setState('default');
    onMouseLeave?.(e);
  }, [onMouseLeave]);

  const handleMouseDown = useCallback((e: React.MouseEvent<HTMLElement>) => {
    setState('pressed');
    onMouseDown?.(e);
  }, [onMouseDown]);

  const handleMouseUp = useCallback((e: React.MouseEvent<HTMLElement>) => {
    setState(prev => prev === 'pressed' ? 'hover' : prev);
    onMouseUp?.(e);
  }, [onMouseUp]);

  const handleFocus = useCallback((e: React.FocusEvent<HTMLElement>) => {
    setState('focus');
    onFocus?.(e);
  }, [onFocus]);

  const handleBlur = useCallback((e: React.FocusEvent<HTMLElement>) => {
    setState('default');
    onBlur?.(e);
  }, [onBlur]);

  // Cast Tag to avoid TypeScript's overly strict JSX intrinsic element checks
  // when using a dynamic element type. Runtime behavior is unchanged.
  const Element = Tag as React.ElementType;

  return (
    <Element
      disabled={Tag === 'button' ? disabled : undefined}
      style={{ position: 'relative', overflow: 'hidden', ...style }}
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
      onFocus={handleFocus}
      onBlur={handleBlur}
      {...rest}
    >
      <span
        aria-hidden="true"
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 'inherit',
          pointerEvents: 'none',
          backgroundColor: color,
          opacity,
          transition: stateTransition('opacity'),
        }}
      />
      {ripple && !disabled && <Ripple color={color} />}
      {children}
    </Element>
  );
}
