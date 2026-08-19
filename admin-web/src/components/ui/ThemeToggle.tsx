import { useTheme } from '@/lib/theme-context';
import { THEME_LABELS, THEME_MODES } from '@/lib/theme';
import type { RevealOrigin } from '@/lib/theme';

/**
 * The middle of the control that was pressed, in viewport coordinates.
 *
 * <p>The new theme opens from here. Taken from the element rather than from the pointer so that a
 * keyboard press — where there is no cursor at all — starts from the same place a click would.
 */
function centreOf(element: HTMLElement): RevealOrigin {
  const box = element.getBoundingClientRect();
  return { x: box.left + box.width / 2, y: box.top + box.height / 2 };
}

/**
 * Sun and moon, drawn as one shape that turns into the other.
 *
 * <p>Both glyphs are always in the DOM and are swapped by transform rather than by mounting one and
 * unmounting the other: the sun shrinks and rotates out as the moon rotates in, around the same
 * centre, so the control reads as a single thing changing state. Mounting a different icon would
 * simply blink.
 */
function SunMoon({ dark }: { dark: boolean }) {
  return (
    <span aria-hidden className="relative block h-4 w-4">
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`absolute inset-0 h-4 w-4 transition-all duration-[--duration-base] ease-[--ease-settle] ${
          dark ? 'scale-50 rotate-90 opacity-0' : 'scale-100 rotate-0 opacity-100'
        }`}
      >
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2m0 16v2M4.93 4.93l1.41 1.41m11.32 11.32 1.41 1.41M2 12h2m16 0h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
      </svg>
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={`absolute inset-0 h-4 w-4 transition-all duration-[--duration-base] ease-[--ease-settle] ${
          dark ? 'scale-100 rotate-0 opacity-100' : 'scale-50 -rotate-90 opacity-0'
        }`}
      >
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" />
      </svg>
    </span>
  );
}

/**
 * The one-click appearance control that sits in the chrome.
 *
 * <p>It is a plain button rather than a menu because the header is not where anyone wants to make a
 * three-way decision — they want the lights changed. The third option, following the operating
 * system, lives on the profile screen as {@link ThemeModeChoice}, and this button leaves it: once
 * you click here you have expressed a preference, and it is honoured until you say otherwise.
 *
 * <p>The label says what will happen, not what is true now. "Switch to dark theme" is unambiguous;
 * a button labelled "Dark" leaves a screen reader user guessing whether that is the current state
 * or the offer.
 */
export function ThemeToggle({ className = '' }: { className?: string }) {
  const { resolved, toggle } = useTheme();
  const dark = resolved === 'dark';

  return (
    <button
      type="button"
      onClick={(event) => toggle(centreOf(event.currentTarget))}
      title={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
      className={`inline-flex h-8 w-8 items-center justify-center rounded-md text-slate-600 outline-none
        transition-all duration-[--duration-quick] ease-[--ease-settle]
        hover:bg-navy-50 hover:text-navy-700 focus-visible:ring-2 focus-visible:ring-navy-300
        active:scale-[0.92] ${className}`}
    >
      <SunMoon dark={dark} />
    </button>
  );
}

/**
 * The full three-way choice, for a settings screen where there is room to explain it.
 *
 * <p>A radiogroup rather than a select: three options that fit on one line should not cost a click
 * to see. The moving pill behind the selected option is the same idea as the underline in the main
 * navigation — one indicator travelling, rather than two states blinking.
 */
export function ThemeModeChoice() {
  const { mode, setMode } = useTheme();

  return (
    <div
      role="radiogroup"
      aria-label="Appearance"
      className="inline-flex gap-1 rounded-lg border border-line bg-surface-sunken p-1"
    >
      {THEME_MODES.map((option) => {
        const selected = mode === option;
        return (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={(event) => setMode(option, centreOf(event.currentTarget))}
            className={`rounded-md px-3 py-1.5 text-sm font-semibold outline-none
              transition-all duration-[--duration-base] ease-[--ease-settle]
              focus-visible:ring-2 focus-visible:ring-navy-300 active:scale-[0.97] ${
                selected
                  ? 'bg-brand text-on-brand shadow-card'
                  : 'text-slate-600 hover:text-navy-700'
              }`}
          >
            {THEME_LABELS[option]}
          </button>
        );
      })}
    </div>
  );
}
