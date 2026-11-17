import { useCallback, useEffect, useRef, useState } from 'react';
import { motion } from '../tokens.ts';

/** Keep the surface mounted through both directions of its transition. */
export function usePopupMotion() {
  const [open, setOpen] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [shown, setShown] = useState(false);
  const frame = useRef<number | undefined>(undefined);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const cancel = useCallback(() => {
    if (frame.current !== undefined) cancelAnimationFrame(frame.current);
    clearTimeout(timer.current);
  }, []);
  const show = useCallback(() => {
    cancel(); setOpen(true); setMounted(true);
    frame.current = requestAnimationFrame(() => {
      frame.current = requestAnimationFrame(() => setShown(true));
    });
  }, [cancel]);
  const hide = useCallback(() => {
    cancel(); setOpen(false); setShown(false);
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    timer.current = setTimeout(() => setMounted(false), reduced ? 100 : motion.dropdown.entrance);
  }, [cancel]);
  useEffect(() => cancel, [cancel]);
  return { open, mounted, shown, show, hide };
}
