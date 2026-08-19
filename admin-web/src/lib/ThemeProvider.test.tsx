import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { ThemeProvider } from '@/lib/ThemeProvider';
import { useTheme } from '@/lib/theme-context';
import { THEME_STORAGE_KEY } from '@/lib/theme';

function stubMatchMedia(initiallyDark: boolean) {
  const listeners = new Set<(event: MediaQueryListEvent) => void>();
  let matches = initiallyDark;
  const query = {
    get matches() {
      return matches;
    },
    media: '(prefers-color-scheme: dark)',
    addEventListener: (_: string, l: (event: MediaQueryListEvent) => void) => listeners.add(l),
    removeEventListener: (_: string, l: (event: MediaQueryListEvent) => void) => listeners.delete(l),
    addListener: (l: (event: MediaQueryListEvent) => void) => listeners.add(l),
    removeListener: (l: (event: MediaQueryListEvent) => void) => listeners.delete(l),
    dispatchEvent: () => true,
    onchange: null,
  };
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => query),
  );
  return {
    change(dark: boolean) {
      matches = dark;
      act(() => {
        for (const listener of listeners) listener({ matches: dark } as MediaQueryListEvent);
      });
    },
  };
}

/** Shows what the context says, and gives the test something to click. */
function Probe() {
  const { mode, resolved, setMode, toggle } = useTheme();
  return (
    <div>
      <span data-testid="mode">{mode}</span>
      <span data-testid="resolved">{resolved}</span>
      {/* Called with no origin: the reveal starts from the centre, which is what a change with no
          control behind it should look like. */}
      <button onClick={() => toggle()}>toggle</button>
      <button onClick={() => setMode('system')}>use system</button>
    </div>
  );
}

const renderProbe = () =>
  render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>,
  );

const attribute = () => document.documentElement.dataset.theme;
const click = (name: string) => act(() => void screen.getByText(name).click());

beforeEach(() => {
  window.localStorage.clear();
  document.documentElement.removeAttribute('data-theme');
  document.head.innerHTML = '<meta name="theme-color" content="#1f4076" />';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ThemeProvider', () => {
  it('starts on system and wears whatever the machine is wearing', () => {
    stubMatchMedia(true);
    renderProbe();

    expect(screen.getByTestId('mode')).toHaveTextContent('system');
    expect(screen.getByTestId('resolved')).toHaveTextContent('dark');
    expect(attribute()).toBe('dark');
  });

  it('honours a stored choice over the machine', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'light');
    stubMatchMedia(true);
    renderProbe();

    expect(screen.getByTestId('resolved')).toHaveTextContent('light');
    expect(attribute()).toBe('light');
  });

  it('toggles away from what is on screen, not from the stored mode', () => {
    // On `system` over a dark machine, one click must give light — the opposite of what is visible.
    stubMatchMedia(true);
    renderProbe();

    click('toggle');
    expect(screen.getByTestId('mode')).toHaveTextContent('light');
    expect(attribute()).toBe('light');

    click('toggle');
    expect(screen.getByTestId('mode')).toHaveTextContent('dark');
    expect(attribute()).toBe('dark');
  });

  it('persists the choice so it survives a reload', () => {
    stubMatchMedia(false);
    renderProbe();

    click('toggle');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  it('follows the machine while on system, and stops once a choice is made', () => {
    const media = stubMatchMedia(false);
    renderProbe();

    media.change(true);
    expect(attribute()).toBe('dark');

    // An explicit light must not be undone the next time the machine changes its mind.
    click('toggle');
    expect(attribute()).toBe('light');
    media.change(true);
    expect(attribute()).toBe('light');

    click('use system');
    expect(attribute()).toBe('dark');
  });

  it('follows a change made in another tab', () => {
    stubMatchMedia(false);
    renderProbe();
    expect(attribute()).toBe('light');

    act(() => {
      window.localStorage.setItem(THEME_STORAGE_KEY, 'dark');
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: THEME_STORAGE_KEY,
          newValue: 'dark',
          storageArea: window.localStorage,
        }),
      );
    });

    expect(screen.getByTestId('mode')).toHaveTextContent('dark');
    expect(attribute()).toBe('dark');
  });

  it('ignores another tab writing an unrelated key', () => {
    stubMatchMedia(false);
    renderProbe();

    act(() => {
      window.dispatchEvent(
        new StorageEvent('storage', {
          key: 'something.else',
          newValue: 'dark',
          storageArea: window.localStorage,
        }),
      );
    });

    expect(attribute()).toBe('light');
  });
});
