import { useState, useRef, useCallback, useEffect, useLayoutEffect, useId, cloneElement, isValidElement, type ReactElement, type AriaAttributes, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { spacing, radii, fontSizes, fontWeights, zIndex, motion } from './tokens.ts';
import { usePopupMotion } from './hooks/usePopupMotion.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUTooltipPosition = 'top' | 'bottom' | 'left' | 'right';

export interface AHFUTooltipProps {
  content: ReactNode;
  children: ReactNode;
  position?: AHFUTooltipPosition;
  delay?: number;
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUTooltip({
  content,
  children,
  position = 'top',
  delay = motion.tooltip.delay,
  style: overrideStyle,
}: AHFUTooltipProps) {
  const tooltipId = useId();
  const { open, mounted, shown: visible, show: reveal, hide: dismissPopup } = usePopupMotion();
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const tooltipRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const triggerRef = useRef<HTMLSpanElement>(null);
  const touchRef = useRef(false);

  const computeCoords = useCallback(() => {
    if (!triggerRef.current) return;
    const rect = triggerRef.current.getBoundingClientRect();
    const gap = 8;

    let top = 0;
    let left = 0;

    switch (position) {
      case 'top':
        top = rect.top - gap;
        left = rect.left + rect.width / 2;
        break;
      case 'bottom':
        top = rect.bottom + gap;
        left = rect.left + rect.width / 2;
        break;
      case 'left':
        top = rect.top + rect.height / 2;
        left = rect.left - gap;
        break;
      case 'right':
        top = rect.top + rect.height / 2;
        left = rect.right + gap;
        break;
    }

    const width = tooltipRef.current?.offsetWidth ?? 0;
    const height = tooltipRef.current?.offsetHeight ?? 0;
    if (position === 'top' || position === 'bottom') left -= width / 2;
    if (position === 'top') top -= height;
    if (position === 'left') left -= width;
    if (position === 'left' || position === 'right') top -= height / 2;
    setCoords({ top: Math.max(8, Math.min(top, window.innerHeight - height - 8)), left: Math.max(8, Math.min(left, window.innerWidth - width - 8)) });
  }, [position]);

  const show = useCallback(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      computeCoords();
      reveal();
    }, delay);
  }, [computeCoords, delay, reveal]);

  useLayoutEffect(() => {
    if (!mounted) return;
    const frame = requestAnimationFrame(computeCoords);
    window.addEventListener('resize', computeCoords);
    window.addEventListener('scroll', computeCoords, true);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', computeCoords);
      window.removeEventListener('scroll', computeCoords, true);
    };
  }, [mounted, computeCoords]);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  const hide = useCallback(() => {
    clearTimeout(timerRef.current);
    dismissPopup();
  }, [dismissPopup]);

  useEffect(() => {
    if (!mounted) return;
    const dismiss = (event: PointerEvent) => {
      if (!triggerRef.current?.contains(event.target as Node)) hide();
    };
    document.addEventListener('pointerdown', dismiss);
    return () => document.removeEventListener('pointerdown', dismiss);
  }, [mounted, hide]);

  const transformOrigin: Record<AHFUTooltipPosition, string> = {
    top: 'center bottom',
    bottom: 'center top',
    left: 'right center',
    right: 'left center',
  };

  const tooltipStyle: React.CSSProperties = {
    position: 'fixed',
    top: coords.top,
    left: coords.left,
    maxWidth: 'min(320px, calc(100vw - 16px))',
    boxSizing: 'border-box',
    overflowWrap: 'anywhere',
    zIndex: zIndex.toast,
    padding: `${spacing[3]}px ${spacing[4]}px`,
    fontSize: fontSizes.xs,
    fontWeight: fontWeights.normal,
    color: 'var(--text-secondary)',
    backgroundColor: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: radii.sm,
    boxShadow: 'var(--shadow)',
    whiteSpace: 'normal',
    pointerEvents: 'none',
    opacity: visible ? 1 : 0,
    transition: `opacity ${motion.tooltip.transition}ms ease, transform ${motion.tooltip.transition}ms ease`,
    transformOrigin: transformOrigin[position],
    ...overrideStyle,
  };

  return (
    <>
      <span
        ref={triggerRef}
        onPointerEnter={event => { if (event.pointerType === 'mouse') { touchRef.current = false; show(); } }}
        onPointerLeave={event => { if (event.pointerType === 'mouse' && !event.currentTarget.contains(document.activeElement)) hide(); }}
        onFocus={() => { if (!touchRef.current) show(); }}
        onBlur={hide}
        onPointerDown={event => {
          touchRef.current = event.pointerType !== 'mouse';
          if (touchRef.current) { clearTimeout(timerRef.current); if (open) hide(); else { computeCoords(); reveal(); } }
        }}
        onKeyDown={(event) => { if (event.key === 'Escape') hide(); }}
        style={{ display: 'inline-flex' }}
      >
        {isValidElement(children) ? cloneElement(children as ReactElement<AriaAttributes>, { 'aria-describedby': open ? tooltipId : undefined }) : children}
      </span>
      {mounted && content && createPortal(
        <div
          role="tooltip"
          id={tooltipId}
          aria-hidden={!open || undefined}
          ref={tooltipRef}
          style={tooltipStyle}
        >
          {content}
        </div>,
        document.body,
      )}
    </>
  );
}
