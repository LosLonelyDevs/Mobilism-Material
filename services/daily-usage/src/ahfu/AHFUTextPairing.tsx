import { spacing } from './tokens.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export type AHFUTextPairingSize = 'small' | 'medium' | 'large';

export interface AHFUTextPairingProps {
  title: string;
  subtitle?: string;
  size?: AHFUTextPairingSize;
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Size config                                       */
/* -------------------------------------------------- */

const sizeConfig: Record<AHFUTextPairingSize, { titleScale: number; subtitleScale: number; gap: number }> = {
  small:  { titleScale: 0.85, subtitleScale: 0.92, gap: spacing[1] },
  medium: { titleScale: 1,    subtitleScale: 1,     gap: spacing[1] },
  large:  { titleScale: 1.15, subtitleScale: 1.08,  gap: spacing[2] },
};

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFUTextPairing({
  title,
  subtitle,
  size = 'medium',
  style: overrideStyle,
}: AHFUTextPairingProps) {
  const s = sizeConfig[size];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: s.gap, ...overrideStyle }}>
      <div style={{
        fontSize: `calc(var(--pairing-ts-title) * ${s.titleScale})`,
        fontWeight: 'var(--pairing-fw-title)' as React.CSSProperties['fontWeight'],
        color: 'var(--text-primary)',
        lineHeight: 1.3,
      }}>
        {title}
      </div>
      {subtitle && (
        <div style={{
          fontSize: `calc(var(--pairing-ts-subtitle) * ${s.subtitleScale})`,
          fontWeight: 'var(--pairing-fw-subtitle)' as React.CSSProperties['fontWeight'],
          color: 'var(--text-secondary)',
          lineHeight: 'var(--pairing-ts-line-height)',
        }}>
          {subtitle}
        </div>
      )}
    </div>
  );
}
