import { useLayoutEffect, useState, type RefObject } from 'react';

/** Keep a portaled popup inside the visible viewport, including the soft keyboard. */
export function useFloatingPosition(
  open: boolean,
  anchor: RefObject<HTMLElement | null>,
  popup: RefObject<HTMLElement | null>,
  preferredHeight = 264,
  preferredWidth = 0,
  allowOverlap = false,
) {
  const [position, setPosition] = useState({ top: 0, left: 0, width: 0, maxHeight: preferredHeight });

  useLayoutEffect(() => {
    if (!open || !anchor.current) return;
    const update = () => {
      if (!anchor.current) return;
      const rect = anchor.current.getBoundingClientRect();
      const viewport = window.visualViewport;
      const x = viewport?.offsetLeft ?? 0;
      const y = viewport?.offsetTop ?? 0;
      const viewportWidth = viewport?.width ?? window.innerWidth;
      const viewportHeight = viewport?.height ?? window.innerHeight;
      const width = Math.min(Math.max(rect.width, preferredWidth), Math.max(0, viewportWidth - 16));
      const below = Math.max(0, y + viewportHeight - rect.bottom - 12);
      const above = Math.max(0, rect.top - y - 12);
      const height = Math.min(preferredHeight, popup.current?.scrollHeight || preferredHeight);
      const flip = below < height && above > below;
      const useViewport = allowOverlap && Math.max(above, below) < height;
      const maxHeight = Math.min(preferredHeight, useViewport ? viewportHeight - 16 : flip ? above : below);
      const top = flip ? rect.top - Math.min(height, maxHeight) - 4 : rect.bottom + 4;
      const next = {
        top: Math.max(y + 8, Math.min(top, y + viewportHeight - Math.min(height, maxHeight) - 8)),
        left: Math.max(x + 8, Math.min(rect.left, x + viewportWidth - width - 8)),
        width,
        maxHeight,
      };
      setPosition(previous => Object.keys(next).every(key => previous[key as keyof typeof next] === next[key as keyof typeof next]) ? previous : next);
    };
    update();
    const observer = new ResizeObserver(update);
    observer.observe(anchor.current);
    if (popup.current) observer.observe(popup.current);
    window.addEventListener('resize', update);
    window.addEventListener('scroll', update, true);
    window.visualViewport?.addEventListener('resize', update);
    window.visualViewport?.addEventListener('scroll', update);
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', update);
      window.removeEventListener('scroll', update, true);
      window.visualViewport?.removeEventListener('resize', update);
      window.visualViewport?.removeEventListener('scroll', update);
    };
  }, [open, anchor, popup, preferredHeight, preferredWidth, allowOverlap]);

  return position;
}
