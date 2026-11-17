import { useState, type ReactNode } from 'react';
import { shape, typeRole, STATE_DURATION, easing } from './tokens.ts';
import Ripple from './Ripple.tsx';
import { type AfPaddingToken, type AfMarginToken, resolveSpacingTokens } from './afSpacing';

export interface AHFUCardProps {
  title?: string;
  subtitle?: string;
  headerEnd?: ReactNode;
  footer?: ReactNode;
  children: ReactNode;
  sectionBorders?: boolean;
  tinted?: boolean;
  avatar?: ReactNode;
  media?: ReactNode;
  mediaHeight?: number;
  radius?: number;
  clickable?: boolean;
  onClick?: () => void;
  padding?: AfPaddingToken | AfPaddingToken[];
  margin?: AfMarginToken | AfMarginToken[];
  style?: React.CSSProperties;
}

export default function AHFUCard({
  title,
  subtitle,
  headerEnd,
  footer,
  children,
  sectionBorders = false,
  tinted = false,
  avatar,
  media,
  mediaHeight = 200,
  radius = shape.md,
  clickable = false,
  onClick,
  padding,
  margin,
  style: overrideStyle,
}: AHFUCardProps) {
  const [hovered, setHovered] = useState(false);
  const isInteractive = clickable || !!onClick;
  const lifted = hovered && isInteractive;

  // Material 3 elevated card: a tonal surface-container carries the colour, a
  // soft shadow carries the elevation. Interactive cards spring up on hover and
  // ripple on press; motion uses token duration/easing only.
  const cardStyle: React.CSSProperties = {
    position: 'relative',
    backgroundColor: 'var(--surface-alt)',
    borderRadius: radius,
    boxShadow: lifted ? 'var(--elevation-3)' : 'var(--elevation-1)',
    overflow: 'hidden',
    transition: `transform ${STATE_DURATION}ms ${easing.overshoot}, box-shadow ${STATE_DURATION}ms ${easing.standard}`,
    transform: lifted ? 'translateY(-3px)' : 'translateY(0)',
    cursor: isInteractive ? 'pointer' : undefined,
    ...(margin ? resolveSpacingTokens(margin) : {}),
    ...overrideStyle,
  };

  const headerStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    padding: '16px 20px',
    backgroundColor: tinted ? 'var(--primary-bg)' : undefined,
    ...(sectionBorders ? { borderBottom: '1px solid var(--border)' } : {}),
  };

  const avatarStyle: React.CSSProperties = {
    width: 44,
    height: 44,
    borderRadius: shape.pill,
    backgroundColor: 'var(--primary-bg)',
    color: 'var(--md-sys-color-on-primary-container, var(--primary))',
    display: 'grid',
    placeItems: 'center',
    flexShrink: 0,
    ...typeRole.titleMedium,
  };

  const bodyStyle: React.CSSProperties = {
    padding: 20,
    ...typeRole.bodyMedium,
    color: 'var(--text-primary)',
    backgroundColor: tinted ? 'color-mix(in srgb, var(--primary-bg) 40%, var(--surface-alt))' : undefined,
    ...(padding ? resolveSpacingTokens(padding) : {}),
  };

  const mediaStyle: React.CSSProperties = {
    height: mediaHeight,
    backgroundColor: 'var(--surface)',
    display: 'grid',
    placeItems: 'center',
    overflow: 'hidden',
    ...(sectionBorders ? { borderBottom: '1px solid var(--border)' } : {}),
  };

  const footerStyle: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    gap: 8,
    padding: '12px 20px 16px',
    backgroundColor: tinted ? 'color-mix(in srgb, var(--primary-bg) 40%, var(--surface-alt))' : undefined,
    ...(sectionBorders ? { borderTop: '1px solid var(--border)' } : {}),
  };

  return (
    <div
      style={cardStyle}
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {isInteractive && <Ripple color="var(--state-on-surface)" />}
      {(title || avatar || headerEnd) && (
        <div style={headerStyle}>
          {avatar && <div style={avatarStyle}>{avatar}</div>}
          {title && (
            <div style={{ flex: 1, minWidth: 0 }}>
              <p style={{ ...typeRole.titleLarge, color: 'var(--text-primary)', margin: 0 }}>{title}</p>
              {subtitle && (
                <p style={{ ...typeRole.bodyMedium, color: 'var(--text-secondary)', margin: 0, marginTop: 2 }}>{subtitle}</p>
              )}
            </div>
          )}
          {headerEnd && <div style={{ marginLeft: 'auto', flexShrink: 0 }}>{headerEnd}</div>}
        </div>
      )}
      {media && <div style={mediaStyle}>{media}</div>}
      <div style={bodyStyle}>{children}</div>
      {footer && <div style={footerStyle}>{footer}</div>}
    </div>
  );
}
