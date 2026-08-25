import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  applyTheme,
  isThemeMode,
  readStoredMode,
  resolveTheme,
  systemTheme,
  THEME_STORAGE_KEY,
  watchSystemTheme,
  writeStoredMode,
} from '@/lib/theme';

/**
 * jsdom has no matchMedia at all, so every test that touches the system preference has to supply
 * one. This returns a handle that can flip the answer and fire the listeners, which is the only way
 * to test "the machine switched to dark at sunset" without a machine.
 */
function stubMatchMedia(initiallyDark: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  let matches = initiallyDark;

  const query = {
    get matches() {
      return matches;
    },
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) =>
      listeners.add(listener),
    removeEventListener: (_: string, listener: (event: MediaQueryListEvent) => void) =>
      listeners.delete(listener),
    addListener: (listener: (event: MediaQueryListEvent) => void) => listeners.add(listener),
    removeListener: (listener: (event: MediaQueryListEvent) => void) => listeners.delete(listener),
    dispatchEvent: () => true,
    onchange: null,
  };

  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => query),
  );

  return {
    listenerCount: () => listeners.size,
    change(dark: boolean) {
      matches = dark;
      for (const listener of listeners) listener({ matches: dark } as MediaQueryListEvent);
    },
  };
}

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  document.documentElement.style.colorScheme = '';
  document.head.innerHTML = '<meta name="theme-color" content="#d2e5fb" />';
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('mode parsing', () => {
  it('accepts only the three modes', () => {
    expect(isThemeMode('light')).toBe(true);
    expect(isThemeMode('dark')).toBe(true);
    expect(isThemeMode('system')).toBe(true);
    expect(isThemeMode('darkk')).toBe(false);
    expect(isThemeMode(null)).toBe(false);
  });
});

describe('readStoredMode', () => {
  it('defaults to dark, the house style, for anyone who has never chosen', () => {
    expect(readStoredMode()).toBe('dark');
  });

  it('returns what was stored', () => {
    writeStoredMode('dark');
    expect(readStoredMode()).toBe('dark');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('falls back to dark rather than trusting a corrupted value', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'chartreuse');
    expect(readStoredMode()).toBe('dark');
  });

  it('survives storage being unavailable, as it is in private browsing', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });

    expect(readStoredMode()).toBe('dark');
    expect(() => writeStoredMode('dark')).not.toThrow();
  });
});

describe('resolveTheme', () => {
  it('passes an explicit choice straight through, whatever the machine says', () => {
    stubMatchMedia(true);
    expect(resolveTheme('light')).toBe('light');
    expect(resolveTheme('dark')).toBe('dark');
  });

  it('asks the machine for system', () => {
    stubMatchMedia(true);
    expect(resolveTheme('system')).toBe('dark');
  });

  it('treats a browser with no matchMedia as light rather than throwing', () => {
    vi.stubGlobal('matchMedia', undefined);
    expect(systemTheme()).toBe('light');
    expect(resolveTheme('system')).toBe('light');
  });
});

describe('applyTheme', () => {
  it('stamps the attribute the stylesheet keys off', () => {
    applyTheme('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');

    applyTheme('light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('sets color-scheme, which is what darkens scrollbars and form controls', () => {
    applyTheme('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('repaints the browser chrome so the address bar matches the page', () => {
    const meta = () => document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')!.content;

    applyTheme('dark');
    expect(meta()).toBe('#0a1020');

    applyTheme('light');
    expect(meta()).toBe('#d2e5fb');
  });

  it('does not fall over when the meta tag is missing', () => {
    document.head.innerHTML = '';
    expect(() => applyTheme('dark')).not.toThrow();
  });
});

describe('watchSystemTheme', () => {
  it('reports a change and unsubscribes cleanly', () => {
    const media = stubMatchMedia(false);
    const seen: string[] = [];

    const stop = watchSystemTheme((theme) => seen.push(theme));
    media.change(true);
    media.change(false);
    expect(seen).toEqual(['dark', 'light']);

    stop();
    expect(media.listenerCount()).toBe(0);
    media.change(true);
    expect(seen).toEqual(['dark', 'light']);
  });

  it('is a no-op where matchMedia does not exist', () => {
    vi.stubGlobal('matchMedia', undefined);
    const stop = watchSystemTheme(() => {
      throw new Error('should never be called');
    });
    expect(() => stop()).not.toThrow();
  });
});
