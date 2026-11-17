import { useState, useCallback, useRef, useEffect, useId, type ReactNode } from 'react';
import StateLayer from './StateLayer.tsx';
import { transition, typeRole, motion } from './tokens.ts';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';

export interface AHFUCollapsibleCardProps {
  title: string;
  subtitle?: React.ReactNode;
  children: ReactNode;
  defaultOpen?: boolean;
  headerEnd?: ReactNode;
  footer?: ReactNode;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

export default function AHFUCollapsibleCard({
  title,
  subtitle,
  children,
  defaultOpen = false,
  headerEnd,
  footer,
  padding,
  margin,
  style: overrideStyle,
}: AHFUCollapsibleCardProps) {
  const [open, setOpen] = useState(defaultOpen);
  const contentRef = useRef<HTMLDivElement>(null);
  const bodyId = useId();
  const [contentHeight, setContentHeight] = useState<number | undefined>(undefined);

  useEffect(() => {
    const content = contentRef.current;
    if (!content) return;
    const resize = () => setContentHeight(content.scrollHeight);
    resize();
    const observer = new ResizeObserver(resize);
    observer.observe(content);
    return () => observer.disconnect();
  }, []);

  const toggle = useCallback(() => setOpen((prev) => !prev), []);

  const cardStyle: React.CSSProperties = {
    backgroundColor: 'var(--surface-alt)',
    borderRadius: 12,
    border: '1px solid var(--border)',
    boxShadow: '0 1px 2px rgba(0,0,0,0.1)',
    overflow: 'hidden',
    ...(margin ? resolveSpacingTokens(margin) : {}),
    ...overrideStyle,
  };

  const headerStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '12px 16px',
    cursor: 'pointer',
    userSelect: 'none',
    transition: `background-color ${motion.collapsible.header}ms ease`,
    borderBottom: open ? '1px solid var(--border)' : '1px solid transparent',
  };

  const chevronStyle: React.CSSProperties = {
    width: 20,
    height: 20,
    transition: transition('transform', 'quick', 'standard'),
    transform: open ? 'rotate(180deg)' : 'rotate(0deg)',
    color: 'var(--text-muted)',
    flexShrink: 0,
  };

  const bodyStyle: React.CSSProperties = {
    maxHeight: open ? (contentHeight ?? 'none') : 0,
    overflow: 'hidden',
    transition: `max-height ${motion.collapsible.body}ms ease`,
  };

  const bodyInnerStyle: React.CSSProperties = {
    padding: 16,
    fontSize: 14,
    lineHeight: 1.5,
    color: 'var(--text-primary)',
    ...(padding ? resolveSpacingTokens(padding) : {}),
  };

  return (
    <div style={cardStyle}>
      <StateLayer
        as="div"
        role="button"
        tabIndex={0}
        aria-expanded={open}
        aria-controls={bodyId}
        onClick={toggle}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            toggle();
          }
        }}
        style={headerStyle}
      >
        <div className="ahfu-collapsible-heading" style={{ flex: 1, minWidth: 0 }}>
          <div className="ahfu-collapsible-title" style={{ ...typeRole.titleLarge, color: 'var(--text-primary)', margin: 0 }}>
            {title}
          </div>
          {subtitle && (
            <div className="ahfu-collapsible-subtitle" style={{ fontSize: 13, color: 'var(--text-secondary)', marginTop: 4 }}>
              {subtitle}
            </div>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {headerEnd}
          <svg style={chevronStyle} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </div>
      </StateLayer>

      <div id={bodyId} style={bodyStyle} inert={!open} aria-hidden={!open}>
        <div ref={contentRef} style={bodyInnerStyle}>
          {children}
        </div>
      </div>

      {footer && open && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'flex-end',
          gap: 8,
          padding: '12px 16px',
          borderTop: '1px solid var(--border)',
        }}>
          {footer}
        </div>
      )}
    </div>
  );
}
