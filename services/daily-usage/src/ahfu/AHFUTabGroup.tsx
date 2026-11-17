import { useRef, useLayoutEffect, useState, type ReactNode } from 'react';
import { transition, motion } from './tokens.ts';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';
import { injectKeyframes } from './utils/injectKeyframes.ts';
import { injectTouchTarget } from './utils/injectTouchTarget.ts';

injectTouchTarget();

// Bold-width reservation: each tab button renders a hidden ::after with font-weight:700
// so the container never resizes when the active tab switches weight.
injectKeyframes('ahfu-tabgroup', `
.ahfu-tab {
  position: relative;
}
.ahfu-tab::after {
  content: attr(data-label);
  display: block;
  font-weight: 700;
  height: 0;
  overflow: hidden;
  visibility: hidden;
  pointer-events: none;
  user-select: none;
}
`);

export interface AHFUTabItem {
  key: string;
  label: ReactNode;
  icon?: ReactNode;
  disabled?: boolean;
}

export type AHFUTabVariant = 'underline' | 'fill';

export interface AHFUTabGroupProps {
  items: AHFUTabItem[];
  value: string;
  onChange: (key: string) => void;
  variant?: AHFUTabVariant;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

export default function AHFUTabGroup({
  items,
  value,
  onChange,
  variant = 'underline',
  padding,
  margin,
  style: overrideStyle,
}: AHFUTabGroupProps) {
  const listRef = useRef<HTMLDivElement>(null);
  const [indicator, setIndicator] = useState<{ left: number; top: number; width: number; height: number } | null>(null);

  // Measure the active tab button to position the sliding indicator
  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    const activeBtn = list.querySelector<HTMLButtonElement>('[aria-selected="true"]');
    if (!activeBtn) return;
    const listRect = list.getBoundingClientRect();
    const btnRect  = activeBtn.getBoundingClientRect();
    setIndicator({
      left: btnRect.left - listRect.left,
      top: btnRect.top - listRect.top,
      width: btnRect.width,
      height: btnRect.height,
    });
  }, [value, items]);

  const containerStyle: React.CSSProperties =
    variant === 'fill'
      ? {
          display: 'inline-flex',
          gap: 4,
          padding: 4,
          background: 'var(--surface)',
          borderRadius: 8,
          position: 'relative',
          ...(padding ? resolveSpacingTokens(padding) : {}),
          ...(margin ? resolveSpacingTokens(margin) : {}),
          ...overrideStyle,
        }
      : {
          display: 'flex',
          gap: 4,
          borderBottom: '1px solid var(--border)',
          position: 'relative',
          ...(padding ? resolveSpacingTokens(padding) : {}),
          ...(margin ? resolveSpacingTokens(margin) : {}),
          ...overrideStyle,
        };

  return (
    <div role="tablist" ref={listRef} style={containerStyle}>
      {items.map((item) => {
        const isActive = item.key === value;
        // Stringify label for data-label (bold-width reservation only works for text labels)
        const labelStr = typeof item.label === 'string' ? item.label : undefined;

        const tabStyle: React.CSSProperties = {
          display: 'inline-flex',
          alignItems: 'center',
          gap: 6,
          padding: variant === 'fill' ? '6px 12px' : '8px 12px',
          fontSize: 14,
          fontFamily: 'inherit',
          // Always render at bold weight — ::after reserves the same space so width never changes
          fontWeight: 700,
          background: 'none',
          border: 'none',
          cursor: item.disabled ? 'default' : 'pointer',
          opacity: item.disabled ? 0.4 : 1,
          transition: transition(['color', 'opacity'], 'quick', 'standard'),
          color: variant === 'fill'
            ? (isActive ? 'var(--text-primary)' : 'var(--text-secondary)')
            : (isActive ? 'var(--primary)' : 'var(--text-secondary)'),
          // underline variant: no per-tab border — indicator handles it
          ...(variant === 'underline' ? { marginBottom: -1 } : {}),
          // fill variant: transparent bg — the sliding pill indicator handles the active bg
          ...(variant === 'fill' ? {
            borderRadius: 6,
            background: 'transparent',
          } : {}),
          position: 'relative',
          zIndex: 1,
        };

        return (
          <button
            key={item.key}
            type="button"
            role="tab"
            tabIndex={isActive ? 0 : -1}
            aria-selected={isActive}
            disabled={item.disabled}
            onClick={() => !item.disabled && onChange(item.key)}
            onKeyDown={event => {
              if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
              event.preventDefault();
              const available = items.filter(tab => !tab.disabled);
              const current = available.findIndex(tab => tab.key === item.key);
              const next = event.key === 'Home' ? 0 : event.key === 'End' ? available.length - 1 :
                (current + (event.key === 'ArrowRight' ? 1 : -1) + available.length) % available.length;
              onChange(available[next].key);
              listRef.current?.querySelectorAll<HTMLButtonElement>('[role="tab"]:not(:disabled)')[next]?.focus();
            }}
            className="ahfu-tab ahfu-touch-target"
            data-label={labelStr}
            style={tabStyle}
          >
            {item.icon}
            {item.label}
          </button>
        );
      })}

      {/* Sliding underline indicator (underline variant) */}
      {variant === 'underline' && indicator && (
        <span
          aria-hidden
          style={{
            position: 'absolute',
            bottom: -1,
            left: indicator.left,
            width: indicator.width,
            height: 2,
            background: 'var(--primary)',
            borderRadius: 999,
            transition: `left ${motion.tabs.indicator}ms cubic-bezier(0.4,0,0.2,1), width ${motion.tabs.indicator}ms cubic-bezier(0.4,0,0.2,1)`,
            pointerEvents: 'none',
          }}
        />
      )}

      {/* Sliding pill indicator (fill variant) */}
      {variant === 'fill' && indicator && (
        <span
          aria-hidden
          style={{
            position: 'absolute',
            top: indicator.top,
            left: indicator.left,
            width: indicator.width,
            height: indicator.height,
            background: 'var(--surface-alt)',
            borderRadius: 6,
            boxShadow: '0 1px 3px rgba(0,0,0,0.12)',
            transition: `left ${motion.tabs.indicator}ms cubic-bezier(0.4,0,0.2,1), width ${motion.tabs.indicator}ms cubic-bezier(0.4,0,0.2,1)`,
            pointerEvents: 'none',
            zIndex: 0,
          }}
        />
      )}
    </div>
  );
}
