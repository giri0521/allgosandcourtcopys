import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * The search box in the header, on every signed-in screen.
 *
 * <p>It navigates rather than searching in place, so a result set is a URL: it can be bookmarked,
 * shared with a colleague in the same office, and reached again with the back button. The results
 * screen owns the query from there, including the facets.
 *
 * <p>A form rather than a keydown handler, so Enter submits the way a browser user expects and the
 * field gets its native clear affordance.
 *
 * <p>Now that navigation has moved to the rail, this is the only thing in the header and can simply
 * take the width it needs at every size — including a phone, where it used to collapse to an icon.
 * Search is the fastest way to a document, and a field is one tap where an icon was two.
 *
 * <p>It is dressed in the chrome's own tokens rather than the page's, because it sits on the
 * coloured bar rather than on the page: `--header-field` is a wash lighter than the bar in the light
 * theme and darker than it in the dark one, so the field reads as cut into the chrome either way,
 * and focus fills it in rather than outlining it.
 */
export function HeaderSearch() {
  const navigate = useNavigate();
  const [term, setTerm] = useState('');

  // The server refuses anything shorter than two characters, so there is no point firing one.
  const canSubmit = term.trim().length >= 2;

  return (
    <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        if (!canSubmit) return;
        navigate(`/search?q=${encodeURIComponent(term.trim())}`);
      }}
      className="relative min-w-0 flex-1 sm:max-w-2xl"
    >
      <label htmlFor="header-search" className="sr-only">
        Search documents
      </label>
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="pointer-events-none absolute top-1/2 left-3.5 h-5 w-5 -translate-y-1/2
          text-[var(--header-ink-muted)]"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <input
        id="header-search"
        type="search"
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        placeholder="Search by name or G.O. number…"
        // 16px at every size, deliberately: below that iOS Safari zooms the page in on focus,
        // which leaves the user zoomed into a header they then have to pinch back out of. It is
        // also simply the right size for the one field the whole application is searched from.
        className="w-full rounded-lg border border-[var(--header-edge)] bg-[var(--header-field)]
          py-2.5 pr-3 pl-11 text-base text-[var(--header-ink)] outline-none transition-all
          duration-[--duration-base] ease-[--ease-settle]
          placeholder:text-[var(--header-ink-muted)] focus:border-transparent
          focus:bg-[var(--header-field-focus)] focus:ring-2 focus:ring-[var(--header-ring)]"
      />
    </form>
  );
}
