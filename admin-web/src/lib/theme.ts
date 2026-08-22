/**
 * The theme, as three preferences and two appearances.
 *
 * <p>What the user picks is a {@link ThemeMode} — light, dark, or system. What the page wears is a
 * {@link ResolvedTheme}, which is only ever light or dark; `system` is resolved against the
 * operating system every time it is applied and again whenever the system changes its mind.
 *
 * <p>Nothing here imports React. The same {@link applyTheme} runs from the blocking script in
 * index.html, before the bundle has loaded, and from the provider afterwards — if the two disagreed
 * by so much as a class name the page would repaint on hydration, which is precisely the flash this
 * is all here to avoid.
 */

export type ThemeMode = 'light' | 'dark' | 'system';
export type ResolvedTheme = 'light' | 'dark';

/**
 * Shared with the inline script in index.html, which cannot import it. Changing this string means
 * changing it there too — and doing so silently signs every user out of their choice, so don't.
 */
export const THEME_STORAGE_KEY = 'allgos.theme';

export const THEME_MODES: readonly ThemeMode[] = ['light', 'dark', 'system'];

export function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'light' || value === 'dark' || value === 'system';
}

/** The browser's standing question: is the operating system in dark mode? */
const DARK_QUERY = '(prefers-color-scheme: dark)';

function matchDark(): MediaQueryList | null {
  // Guarded for jsdom and for any environment without matchMedia; absent means light.
  return typeof window !== 'undefined' && typeof window.matchMedia === 'function'
    ? window.matchMedia(DARK_QUERY)
    : null;
}

export function systemTheme(): ResolvedTheme {
  return matchDark()?.matches ? 'dark' : 'light';
}

export function resolveTheme(mode: ThemeMode): ResolvedTheme {
  return mode === 'system' ? systemTheme() : mode;
}

/**
 * The stored preference, or `dark` for anyone who has never expressed one.
 *
 * <p>Dark is the house style: the application is meant to be seen on the dark canvas, and anyone
 * who wants the light one — or wants to follow their machine — can say so from the toggle or My
 * Profile, and that choice is what gets stored.
 *
 * <p>A read can throw outright — Safari in private browsing, or a locked-down group policy — so the
 * failure is swallowed. A theme is not worth breaking the application over.
 */
export function readStoredMode(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemeMode(stored) ? stored : 'dark';
  } catch {
    return 'dark';
  }
}

export function writeStoredMode(mode: ThemeMode): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, mode);
  } catch {
    // Not fatal: the theme still applies for this tab, it just will not survive a reload.
  }
}

/**
 * The browser chrome around the page — the address bar on Android, the title bar on installed
 * PWAs — takes its colour from this meta tag, and a navy bar over a dark page is the one seam a
 * user cannot explain.
 */
function paintBrowserChrome(theme: ResolvedTheme): void {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]');
  if (meta) meta.content = theme === 'dark' ? '#0a1020' : '#1f4076';
}

/**
 * Puts a resolved theme on the document, and returns it.
 *
 * <p>`data-theme` is what the stylesheet keys off. `color-scheme` is separate and does a job CSS
 * variables cannot: it tells the browser to render the things the page does not own — scrollbars,
 * the caret, a date picker's dropdown, the default background behind an overscroll — in dark. Set
 * it here rather than only in CSS so it is right during the blocking script too.
 */
export function applyTheme(theme: ResolvedTheme): ResolvedTheme {
  const root = document.documentElement;
  root.dataset.theme = theme;
  root.style.colorScheme = theme;
  paintBrowserChrome(theme);
  return theme;
}

/** Where the new theme should appear from — the centre of whatever was clicked. */
export interface RevealOrigin {
  x: number;
  y: number;
}

/**
 * The View Transitions API, which TypeScript's DOM library does not describe in every version this
 * project builds against. Only the two members used here are declared.
 */
interface ViewTransitionLike {
  ready: Promise<void>;
  finished: Promise<void>;
}

type DocumentWithViewTransitions = Document & {
  startViewTransition?: (callback: () => void) => ViewTransitionLike;
};

/** How long the reveal takes. Long enough to read as a sweep, short enough not to be in the way. */
const REVEAL_MS = 500;

/*
 * A symmetric curve, and the same one the no-view-transition fallback uses — see --ease-theme in
 * index.css.
 *
 * The application's --ease-settle was tried here first and measured: it put the circle halfway
 * across the screen in 74ms and then spent 280ms creeping through the last tenth, which is a flash
 * followed by nothing rather than a sweep. That curve is tuned for a button acknowledging a press,
 * where being almost-done immediately is the point.
 */
const REVEAL_EASING = 'cubic-bezier(0.45, 0, 0.25, 1)';

/** What the browsers without view transitions get instead. Matches --duration-theme in index.css. */
const FALLBACK_MS = 340;

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

/**
 * Puts the new theme on screen as a circle opening from the control that was pressed.
 *
 * <p>The obvious approach — transition every colour on every element — is what this replaced, and it
 * did not work. Two reasons. The page's ground is two radial gradients, and `background-image` is
 * not a transitionable property: measured mid-switch, the wash had already jumped to its dark value
 * while the base colour behind it was a third of the way across, so the ground snapped while the
 * cards faded. And the application's easing curve is deliberately front-loaded — right for a button
 * acknowledging a press, wrong for a whole page, where it reads as a flip rather than a change.
 *
 * <p>A view transition sidesteps both. The browser photographs the page before and after and
 * cross-fades the two images on the compositor, so gradients, shadows and images all come along at
 * no extra cost, and nothing is left behind because it happened not to be a colour. The default
 * cross-fade is turned off in index.css and replaced with this: the new page clipped to a circle
 * that grows from the toggle to beyond the far corner.
 *
 * <p>Anyone who has asked for reduced motion gets the change immediately, with no animation at all.
 */
export function applyThemeAnimated(theme: ResolvedTheme, origin?: RevealOrigin): ResolvedTheme {
  const root = document.documentElement;

  if (prefersReducedMotion()) {
    return applyTheme(theme);
  }

  const start = (document as DocumentWithViewTransitions).startViewTransition?.bind(document);

  if (!start) {
    // Firefox and older Safari. The colours still cross-fade; only the gradient ground snaps, which
    // is what this did everywhere before and is far better than nothing.
    root.classList.add('theme-changing');
    applyTheme(theme);
    window.setTimeout(() => root.classList.remove('theme-changing'), FALLBACK_MS);
    return theme;
  }

  const x = origin?.x ?? window.innerWidth / 2;
  const y = origin?.y ?? window.innerHeight / 2;

  // The far corner. Anything short of this leaves a wedge of the old theme in shot at the end.
  const radius = Math.hypot(
    Math.max(x, window.innerWidth - x),
    Math.max(y, window.innerHeight - y),
  );

  const transition = start(() => {
    applyTheme(theme);
  });

  transition.ready
    .then(() => {
      root.animate(
        {
          clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`],
        },
        {
          duration: REVEAL_MS,
          easing: REVEAL_EASING,
          // The *new* page is the one clipped, so the old stays whole underneath and the new
          // sweeps over it. Clipping the old would tear a hole in the page instead.
          pseudoElement: '::view-transition-new(root)',
        },
      );
    })
    .catch(() => {
      // A transition that is skipped — because another began before this one drew — rejects here.
      // The theme has already been applied by the callback, so there is nothing to undo.
    });

  return theme;
}

/**
 * Watches the operating system, for as long as the user is following it.
 *
 * <p>Returns its own unsubscribe. `addEventListener` on a MediaQueryList is the modern spelling;
 * older WebKit only has `addListener`, and this application is used on whatever the office has.
 */
export function watchSystemTheme(onChange: (theme: ResolvedTheme) => void): () => void {
  const query = matchDark();
  if (!query) return () => {};

  const handler = (event: MediaQueryListEvent) => onChange(event.matches ? 'dark' : 'light');

  if (typeof query.addEventListener === 'function') {
    query.addEventListener('change', handler);
    return () => query.removeEventListener('change', handler);
  }
  query.addListener(handler);
  return () => query.removeListener(handler);
}

/** How the toggle names each mode. Kept beside the type so a fourth mode cannot be added silently. */
export const THEME_LABELS: Record<ThemeMode, string> = {
  light: 'Light',
  dark: 'Dark',
  system: 'System',
};
