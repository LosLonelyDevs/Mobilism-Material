import { useEffect, useCallback, useRef, useState, useId, type ReactNode } from 'react';
import { spacing, radii, zIndex, transitions, duration, easing, motion } from './tokens.ts';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';
import AHFUTextPairing from './AHFUTextPairing.tsx';
import { injectKeyframes } from './utils/injectKeyframes.ts';
import { useReducedMotion } from './hooks/useReducedMotion.ts';
import { createPortal } from 'react-dom';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUModalSize = 'small' | 'medium' | 'large';

export interface AHFUModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: AHFUModalSize;
  closeOnOverlay?: boolean;
  closeOnEsc?: boolean;
  showClose?: boolean;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Size map                                          */
/* -------------------------------------------------- */

const sizeWidths: Record<AHFUModalSize, number> = {
  small: 400,
  medium: 560,
  large: 720,
};

/* -------------------------------------------------- */
/*  Keyframes injection (module level, idempotent)    */
/* -------------------------------------------------- */

const modalKeyframes = `
@keyframes ahfu-modal-overlay-in {
  0%   { opacity: 0; }
  100% { opacity: 1; }
}
@keyframes ahfu-modal-overlay-out {
  0%   { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes ahfu-modal-in {
  0%   { opacity: 0; }
  100% { opacity: 1; }
}
@keyframes ahfu-modal-out {
  0%   { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes ahfu-modal-overlay-in-reduced {
  0%   { opacity: 0; }
  100% { opacity: 1; }
}
@keyframes ahfu-modal-overlay-out-reduced {
  0%   { opacity: 1; }
  100% { opacity: 0; }
}
@keyframes ahfu-modal-in-reduced {
  0%   { opacity: 0; }
  100% { opacity: 1; }
}
@keyframes ahfu-modal-out-reduced {
  0%   { opacity: 1; }
  100% { opacity: 0; }
}
`;

injectKeyframes('ahfu-modal', modalKeyframes);

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUModal({
  open,
  onClose,
  title,
  subtitle,
  children,
  footer,
  size = 'medium',
  closeOnOverlay = true,
  closeOnEsc = true,
  showClose = true,
  padding,
  margin,
  style: overrideStyle,
}: AHFUModalProps) {
  const reducedMotion = useReducedMotion();
  const titleId = useId();

  const [visible, setVisible] = useState(open);
  const [animating, setAnimating] = useState<'in' | 'out' | null>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  // Freeze children / title / subtitle / footer during exit so content doesn't vanish mid-animation
  const childrenRef = useRef(children);
  const titleRef = useRef(title);
  const subtitleRef = useRef(subtitle);
  const footerRef = useRef(footer);
  if (open) {
    childrenRef.current = children;
    titleRef.current = title;
    subtitleRef.current = subtitle;
    footerRef.current = footer;
  }

  const exitDuration = reducedMotion ? duration.quick : motion.modal.exit;

  useEffect(() => {
    if (open) {
      setVisible(true);
      setAnimating('in');
    } else if (visible) {
      setAnimating('out');
      const timer = setTimeout(() => {
        setVisible(false);
        setAnimating(null);
      }, exitDuration);
      return () => clearTimeout(timer);
    }
  }, [open]);

  const handleEsc = useCallback(
    (e: KeyboardEvent) => {
      if (closeOnEsc && e.key === 'Escape') onClose();
    },
    [closeOnEsc, onClose],
  );

  useEffect(() => {
    if (visible) {
      const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
      document.addEventListener('keydown', handleEsc);
      document.body.style.overflow = 'hidden';
      document.body.style.paddingRight = `${scrollbarWidth}px`;
      return () => {
        document.removeEventListener('keydown', handleEsc);
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
      };
    }
  }, [visible, handleEsc]);

  const handleOverlayClick = useCallback(
    (e: React.MouseEvent) => {
      if (closeOnOverlay && e.target === overlayRef.current) onClose();
    },
    [closeOnOverlay, onClose],
  );

  useEffect(() => {
    if (!open || !visible) return;
    const previous = document.activeElement as HTMLElement | null;
    const panel = panelRef.current;
    const background = Array.from(document.body.children).filter((node): node is HTMLElement => node instanceof HTMLElement && !node.contains(panel));
    const oldInert = background.map(node => node.inert);
    background.forEach(node => { node.inert = true; });
    (panel?.querySelector<HTMLElement>('input:not([disabled]), textarea:not([disabled]), button:not([disabled])') || panel)?.focus();
    const trap = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !panel) return;
      const targets = Array.from(panel.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), a[href], [tabindex="0"]'))
        .filter(node => node.getClientRects().length > 0 && !node.closest('[inert]'));
      const first = targets[0], last = targets[targets.length - 1];
      if (!first) { event.preventDefault(); panel.focus(); }
      else if (event.shiftKey && (document.activeElement === first || !panel.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !panel.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', trap);
    return () => { document.removeEventListener('keydown', trap); background.forEach((node, i) => { node.inert = oldInert[i]; }); if (previous?.isConnected) previous.focus(); };
  }, [open, visible]);

  if (!visible) return null;

  const suffix = reducedMotion ? '-reduced' : '';
  const entryDuration = reducedMotion ? duration.quick : motion.modal.enter;
  const entryEasing = reducedMotion ? easing.standard : easing.decelerate;
  const exitEasing = reducedMotion ? easing.standard : easing.accelerate;

  const overlayStyle: React.CSSProperties = {
    position: 'fixed',
    inset: 0,
    zIndex: zIndex.modal,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    padding: spacing[5],
    animation: animating === 'in'
      ? `ahfu-modal-overlay-in${suffix} ${entryDuration}ms ${entryEasing} forwards`
      : animating === 'out'
        ? `ahfu-modal-overlay-out${suffix} ${exitDuration}ms ${exitEasing} forwards`
        : undefined,
  };

  const panelStyle: React.CSSProperties = {
    width: '100%',
    maxWidth: sizeWidths[size],
    maxHeight: '85vh',
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: 'var(--surface-alt)',
    border: '1px solid var(--border)',
    borderRadius: radii.xl,
    boxShadow: 'var(--shadow-hover)',
    overflow: 'hidden',
    animation: animating === 'in'
      ? `ahfu-modal-in${suffix} ${entryDuration}ms ${entryEasing} forwards`
      : animating === 'out'
        ? `ahfu-modal-out${suffix} ${exitDuration}ms ${exitEasing} forwards`
        : undefined,
    ...overrideStyle,
  };

  return createPortal(
    <div ref={overlayRef} onClick={handleOverlayClick} style={overlayStyle}>
      <div ref={panelRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={title ? titleId : undefined} style={panelStyle}>
        {/* Header */}
        {(title || showClose) && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: `${spacing[4]}px ${spacing[6]}px`,
            flexShrink: 0,
          }}>
            {titleRef.current && (
              <div id={titleId}>
                <AHFUTextPairing
                  title={titleRef.current}
                  subtitle={subtitleRef.current}
                  size="medium"
                  style={{
                    '--pairing-fw-title': 'var(--modal-fw-heading)',
                    '--pairing-ts-title': 'var(--modal-ts-heading)',
                    '--pairing-fw-subtitle': 'var(--modal-fw-body)',
                    '--pairing-ts-subtitle': 'var(--modal-ts-body)',
                    '--pairing-ts-line-height': 'var(--modal-ts-line-height)',
                  } as React.CSSProperties}
                />
              </div>
            )}
            {showClose && (
              <button
                onClick={onClose}
                aria-label="Close"
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--text-muted)',
                  cursor: 'pointer',
                  fontSize: 20,
                  width: 36,
                  height: 36,
                  padding: 0,
                  lineHeight: 1,
                  borderRadius: radii.full,
                  transition: transitions.fast,
                  marginLeft: 'auto',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
                onMouseEnter={(e) => {
                  e.currentTarget.style.color = 'var(--text-primary)';
                  e.currentTarget.style.backgroundColor = 'var(--surface)';
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.color = 'var(--text-muted)';
                  e.currentTarget.style.backgroundColor = 'transparent';
                }}
              >
                ×
              </button>
            )}
          </div>
        )}

        {/* Body */}
        <div style={{
          padding: spacing[6],
          flex: 1,
          overflow: 'auto',
          fontSize: 'var(--modal-ts-body)',
          lineHeight: 'var(--modal-ts-line-height)',
          fontWeight: 'var(--modal-fw-body)' as React.CSSProperties['fontWeight'],
          color: 'var(--text-secondary)',
          ...(padding ? resolveSpacingTokens(padding) : {}),
          ...(margin ? resolveSpacingTokens(margin) : {}),
        }}>
          {childrenRef.current}
        </div>

        {/* Footer */}
        {footerRef.current && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: spacing[3],
            padding: `${spacing[4]}px ${spacing[6]}px`,
            flexShrink: 0,
          }}>
            {footerRef.current}
          </div>
        )}
      </div>
    </div>, document.body
  );
}
