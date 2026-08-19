import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';
import { ThemeContext } from '@/lib/theme-context';
import type { ResolvedTheme, RevealOrigin, ThemeMode } from '@/lib/theme';
import {
  applyTheme,
  applyThemeAnimated,
  readStoredMode,
  systemTheme,
  THEME_STORAGE_KEY,
  watchSystemTheme,
  writeStoredMode,
} from '@/lib/theme';

/*
 * The operating system's preference is not this application's state — it belongs to the machine,
 * changes without asking, and is read rather than owned. `useSyncExternalStore` is the way to read
 * something like that: React subscribes, and a change at sunset re-renders whatever depends on it
 * without a `useState` shadowing a value we do not control.
 *
 * Both functions are defined out here so their identity is stable; declared inside the component
 * they would resubscribe on every render.
 */
const subscribeToSystem = (onChange: () => void) => watchSystemTheme(() => onChange());
const readSystem = (): ResolvedTheme => systemTheme();
const readSystemOnServer = (): ResolvedTheme => 'light';

/**
 * Holds the appearance preference for the running tab.
 *
 * <p>The document is already themed by the time this mounts — the blocking script in index.html saw
 * to that. What is left is everything afterwards: keeping the preference, writing it to
 * localStorage, following the operating system for anyone on `system`, and following *other tabs*,
 * so that switching to dark in one window does not leave a second window on the same machine
 * sitting there white.
 *
 * <p>Note that `resolved` is derived rather than stored. There is exactly one piece of state here —
 * what the user chose — and the appearance falls out of it. Two pieces of state that must agree is
 * how a theme ends up saying `dark` while the page is still light.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(() =>
    typeof window === 'undefined' ? 'system' : readStoredMode(),
  );

  const system = useSyncExternalStore(subscribeToSystem, readSystem, readSystemOnServer);
  const resolved: ResolvedTheme = mode === 'system' ? system : mode;

  /*
   * Push the resolved theme onto the document. This is the legitimate shape of an effect: React
   * state going out to something React does not own — the `data-theme` attribute, `color-scheme`,
   * and the browser chrome's meta tag.
   *
   * The first run is a plain apply, every later one crossfades. On mount the attribute is already
   * correct and there is nothing to fade between; animating it would only add a pointless 260ms of
   * transition class to the very first paint.
   */
  const mounted = useRef(false);
  // Where the next change should open from. A ref rather than state: it is read once by the effect
  // that follows and must never itself cause a render.
  const origin = useRef<RevealOrigin | undefined>(undefined);

  useEffect(() => {
    if (mounted.current) {
      applyThemeAnimated(resolved, origin.current);
      // Cleared so a change that arrives from the operating system or another tab does not reuse
      // the position of a button somebody pressed ten minutes ago.
      origin.current = undefined;
    } else {
      mounted.current = true;
      // Idempotent, and worth doing: a bfcache restore or an extension can leave the attribute
      // saying something the provider disagrees with, and one cheap write beats a mismatch nobody
      // can reproduce.
      applyTheme(resolved);
    }
  }, [resolved]);

  /*
   * Follow the other tabs. `storage` fires only in the windows that did *not* make the change,
   * which is exactly the set that needs telling.
   */
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.storageArea !== window.localStorage) return;
      // A null key means the whole store was cleared, which counts; any other key is not ours.
      if (event.key !== null && event.key !== THEME_STORAGE_KEY) return;
      setModeState(readStoredMode());
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const setMode = useCallback((next: ThemeMode, from?: RevealOrigin) => {
    origin.current = from;
    setModeState(next);
    writeStoredMode(next);
  }, []);

  const toggle = useCallback(
    (from?: RevealOrigin) => {
      // Against `resolved` rather than `mode`, so the first click away from `system` goes to the
      // opposite of what is on screen — the only reading of "toggle" that matches what the user
      // sees.
      setMode(resolved === 'dark' ? 'light' : 'dark', from);
    },
    [resolved, setMode],
  );

  const value = useMemo(
    () => ({ mode, resolved, setMode, toggle }),
    [mode, resolved, setMode, toggle],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
