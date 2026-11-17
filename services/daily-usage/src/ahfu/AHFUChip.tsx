import { useState, type ReactNode, type MouseEvent, type HTMLAttributes } from 'react';
import StateLayer from './StateLayer.tsx';
import { disabled as disabledTokens, motion } from './tokens.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUChipVariant = 'filled' | 'outlined';
export type AHFUChipColor = 'default' | 'primary' | 'success' | 'danger' | 'warning' | 'info';
export type AHFUChipSize = 'small' | 'medium';

export interface AHFUChipProps extends Omit<HTMLAttributes<HTMLDivElement>, 'style'> {
  label: ReactNode;
  variant?: AHFUChipVariant;
  color?: AHFUChipColor;
  size?: AHFUChipSize;
  icon?: ReactNode;
  deleteIcon?: ReactNode;
  onDelete?: () => void;
  clickable?: boolean;
  disabled?: boolean;
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Color map                                         */
/* -------------------------------------------------- */

interface ColorDef {
  filled: { bg: string; color: string; border: string };
  outlined: { bg: string; color: string; border: string };
}

const colors: Record<AHFUChipColor, ColorDef> = {
  default: {
    filled:   { bg: 'var(--surface)', color: 'var(--text-primary)', border: 'var(--surface)' },
    outlined: { bg: 'transparent',    color: 'var(--text-primary)', border: 'var(--border-strong)' },
  },
  primary: {
    filled:   { bg: 'var(--primary)',    color: 'var(--primary-foreground)', border: 'var(--primary)' },
    outlined: { bg: 'transparent',       color: 'var(--primary)',            border: 'var(--primary-border)' },
  },
  success: {
    filled:   { bg: 'var(--success)',    color: 'var(--success-foreground)', border: 'var(--success)' },
    outlined: { bg: 'transparent',       color: 'var(--success)',            border: 'var(--success)' },
  },
  danger: {
    filled:   { bg: 'var(--danger)',     color: 'var(--danger-foreground)',  border: 'var(--danger)' },
    outlined: { bg: 'transparent',       color: 'var(--danger)',             border: 'var(--danger)' },
  },
  warning: {
    filled:   { bg: 'var(--warning)',    color: 'var(--warning-foreground)', border: 'var(--warning)' },
    outlined: { bg: 'transparent',       color: 'var(--warning)',            border: 'var(--warning)' },
  },
  info: {
    filled:   { bg: 'var(--info)',       color: 'var(--info-foreground)',    border: 'var(--info)' },
    outlined: { bg: 'transparent',       color: 'var(--info)',              border: 'var(--info)' },
  },
};

/* -------------------------------------------------- */
/*  Delete icon (default ×)                           */
/* -------------------------------------------------- */

function DefaultDeleteIcon({ size, color }: { size: AHFUChipSize; color: string }) {
  const s = size === 'small' ? 16 : 18;
  return (
    <svg width={s} height={s} viewBox="0 0 24 24" fill="none" style={{ display: 'block' }}>
      <circle cx="12" cy="12" r="10" fill="currentColor" opacity="0.15" />
      <path
        d="M8.5 8.5l7 7M15.5 8.5l-7 7"
        stroke={color}
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUChip({
  label,
  variant = 'filled',
  color = 'default',
  size = 'medium',
  icon,
  deleteIcon,
  onDelete,
  clickable,
  disabled,
  onClick,
  style: overrideStyle,
  ...rest
}: AHFUChipProps) {
  const isClickable = clickable ?? !!onClick;
  const [deleteHovered, setDeleteHovered] = useState(false);

  const handleClick = (e: MouseEvent<HTMLElement>) => {
    if (disabled) return;
    onClick?.(e as MouseEvent<HTMLDivElement>);
  };

  const c = colors[color][variant];
  const isSmall = size === 'small';

  const chipStyle: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: isSmall ? 4 : 6,
    height: isSmall ? 24 : 32,
    padding: `0 ${isSmall ? 8 : 12}px`,
    fontSize: isSmall ? 12 : 13,
    fontFamily: 'inherit',
    fontWeight: 500,
    lineHeight: 'normal',
    borderRadius: 999,
    border: `1px solid ${c.border}`,
    background: c.bg,
    color: c.color,
    cursor: disabled ? 'default' : isClickable ? 'pointer' : 'default',
    opacity: disabled ? disabledTokens.content : 1,
    userSelect: 'none',
    boxSizing: 'border-box',
    whiteSpace: 'nowrap',
    ...overrideStyle,
  };

  return (
    <StateLayer
      as="div"
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable && !disabled ? 0 : undefined}
      onClick={handleClick}
      onKeyDown={
        isClickable && !disabled
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick?.(e as unknown as MouseEvent<HTMLDivElement>);
              }
            }
          : undefined
      }
      style={chipStyle}
      disabled={disabled}
      {...rest}
    >
      {/* Icon */}
      {icon && (
        <span style={{ display: 'flex', alignItems: 'center', marginLeft: isSmall ? -2 : -4 }}>
          {icon}
        </span>
      )}

      {/* Label */}
      <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', position: 'relative', lineHeight: 'normal' }}>
        {label}
      </span>

      {/* Delete */}
      {onDelete && (
        <span
          role="button"
          aria-label={`Remove ${typeof label === 'string' ? label : 'selection'}`}
          tabIndex={0}
          onClick={(e) => {
            e.stopPropagation();
            if (!disabled) onDelete();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              e.stopPropagation();
              if (!disabled) onDelete();
            }
          }}
          style={{
            display: 'flex',
            alignItems: 'center',
            marginRight: isSmall ? -2 : -4,
            cursor: disabled ? 'default' : 'pointer',
            opacity: deleteHovered ? 1 : 0.7,
            transition: `opacity ${motion.input.option}ms cubic-bezier(0.2, 0, 0, 1)`,
            position: 'relative',
          }}
          onMouseEnter={() => setDeleteHovered(true)}
          onMouseLeave={() => setDeleteHovered(false)}
        >
          {deleteIcon || <DefaultDeleteIcon size={size} color={c.color} />}
        </span>
      )}
    </StateLayer>
  );
}
