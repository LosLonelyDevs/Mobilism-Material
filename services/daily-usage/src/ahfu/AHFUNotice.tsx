import { useState, useCallback, type ReactNode } from 'react';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';
import { motion } from './tokens.ts';

export type AHFUNoticeVariant = 'info' | 'success' | 'warning' | 'danger';

export interface AHFUNoticeProps {
  children: ReactNode;
  variant?: AHFUNoticeVariant;
  title?: string;
  dismissible?: boolean;
  onDismiss?: () => void;
  icon?: ReactNode;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

const variants: Record<AHFUNoticeVariant, { bg: string; border: string; accent: string; icon: string }> = {
  info:    { bg: 'rgba(0, 122, 239, 0.08)',  border: 'var(--info)',    accent: 'var(--info)',    icon: 'ℹ' },
  success: { bg: 'rgba(78, 199, 42, 0.08)',  border: 'var(--success)', accent: 'var(--success)', icon: '✓' },
  warning: { bg: 'rgba(242, 183, 5, 0.08)',  border: 'var(--warning)', accent: 'var(--warning)', icon: '⚠' },
  danger:  { bg: 'rgba(245, 57, 79, 0.08)',  border: 'var(--danger)',  accent: 'var(--danger)',  icon: '✕' },
};

export default function AHFUNotice({
  children,
  variant = 'info',
  title,
  dismissible = false,
  onDismiss,
  icon,
  padding,
  margin,
  style: overrideStyle,
}: AHFUNoticeProps) {
  const [visible, setVisible] = useState(true);
  const v = variants[variant];

  const handleDismiss = useCallback(() => {
    setVisible(false);
    onDismiss?.();
  }, [onDismiss]);

  if (!visible) return null;

  const rootStyle: React.CSSProperties = {
    display: 'flex',
    gap: 12,
    padding: 16,
    backgroundColor: v.bg,
    border: `1.5px solid ${v.border}`,
    borderLeft: `3.5px solid ${v.border}`,
    borderRadius: 8,
    fontSize: 14,
    lineHeight: 1.6,
    color: 'var(--text-primary)',
    position: 'relative',
    ...(padding ? resolveSpacingTokens(padding) : {}),
    ...(margin ? resolveSpacingTokens(margin) : {}),
    ...overrideStyle,
  };

  const iconStyle: React.CSSProperties = {
    fontSize: 16,
    fontWeight: 700,
    color: v.accent,
    flexShrink: 0,
    marginTop: 2,
  };

  return (
    <div role="alert" style={rootStyle}>
      <span style={iconStyle}>{icon ?? v.icon}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && (
          <div style={{ fontWeight: 700, marginBottom: 4, color: v.accent }}>
            {title}
          </div>
        )}
        <div style={{ color: 'var(--text-secondary)' }}>{children}</div>
      </div>
      {dismissible && (
        <button
          onClick={handleDismiss}
          aria-label="Dismiss"
          style={{
            background: 'none',
            border: 'none',
            color: 'var(--text-muted)',
            cursor: 'pointer',
            fontSize: 18,
            padding: 0,
            lineHeight: 1,
            flexShrink: 0,
            alignSelf: 'flex-start',
            transition: `color ${motion.input.focus}ms ease`,
          }}
          onMouseEnter={(e) => { (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
        >
          ×
        </button>
      )}
    </div>
  );
}
