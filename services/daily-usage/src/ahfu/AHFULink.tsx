import { useState, type AnchorHTMLAttributes, type ReactNode } from 'react';
import AHFUTooltip from './AHFUTooltip.tsx';
import { fontSizes, fontWeights, transition } from './tokens.ts';

/* -------------------------------------------------- */
/*  Types                                             */
/* -------------------------------------------------- */

export interface AHFULinkProps extends Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'style'> {
  text?: ReactNode;
  href: string;
  tooltip?: string;
  target?: string;
  style?: React.CSSProperties;
}

/* -------------------------------------------------- */
/*  Component                                         */
/* -------------------------------------------------- */

export default function AHFULink({
  text,
  children,
  href,
  tooltip,
  target = '_blank',
  style: overrideStyle,
  ...rest
}: AHFULinkProps) {
  const [hovered, setHovered] = useState(false);

  const linkStyle: React.CSSProperties = {
    color: 'var(--link)',
    fontSize: fontSizes.base,
    fontWeight: fontWeights.medium,
    textDecoration: 'none',
    cursor: 'pointer',
    borderBottom: `1px solid ${hovered ? 'var(--link)' : 'transparent'}`,
    transition: transition('border-bottom-color', 'quick', 'standard'),
    ...overrideStyle,
  };

  const link = (
    <a
      href={href}
      target={target}
      rel={target === '_blank' ? 'noopener noreferrer' : undefined}
      style={linkStyle}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      {...rest}
    >
      {children ?? text}
      {target === '_blank' && (
        <svg
          width={12}
          height={12}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          style={{ marginLeft: 4, verticalAlign: 'middle', opacity: 0.6 }}
        >
          <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
          <polyline points="15 3 21 3 21 9" />
          <line x1="10" y1="14" x2="21" y2="3" />
        </svg>
      )}
    </a>
  );

  if (tooltip) {
    return <AHFUTooltip content={tooltip}>{link}</AHFUTooltip>;
  }

  return link;
}
