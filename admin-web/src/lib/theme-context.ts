import { createContext, useContext } from 'react';
import type { ResolvedTheme, RevealOrigin, ThemeMode } from '@/lib/theme';

export interface ThemeState {
  /** What the user chose: light, dark, or follow the system. */
  mode: ThemeMode;
  /** What the page is actually wearing right now — never `system`. */
  resolved: ResolvedTheme;
  /**
   * `origin` is where on screen the new theme should open from — the centre of the control that was
   * pressed. Optional, and the middle of the viewport without it, which is what a change made by
   * the operating system or by another tab should look like: it came from nowhere in particular.
   */
  setMode: (mode: ThemeMode, origin?: RevealOrigin) => void;
  /**
   * Light to dark and back, for the one-click control in the header.
   *
   * <p>It commits to an explicit mode rather than cycling through `system`: someone who clicks a
   * sun expects a light page, not a page that agrees to think about it.
   */
  toggle: (origin?: RevealOrigin) => void;
}

export const ThemeContext = createContext<ThemeState | null>(null);

export function useTheme(): ThemeState {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used inside ThemeProvider');
  }
  return context;
}
