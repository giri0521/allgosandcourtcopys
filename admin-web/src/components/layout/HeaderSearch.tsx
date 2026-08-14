import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

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
 * <p>Below `md` the field would crowd the logo and the bell off a 360px header, so it collapses to
 * an icon that opens the search screen instead. It collapses rather than disappearing: search is
 * the fastest way to a document, and hiding it on a phone would leave a whole class of user
 * browsing 43 departments by hand.
 */
export function HeaderSearch() {
  const navigate = useNavigate();
  const [term, setTerm] = useState('');

  // The server refuses anything shorter than two characters, so there is no point firing one.
  const canSubmit = term.trim().length >= 2;

  return (
    <>
      <Link
        to="/search"
        aria-label="Search documents"
        title="Search documents"
        className="rounded-md p-2 text-slate-600 outline-none transition-colors
          duration-[--duration-base] ease-[--ease-settle] hover:bg-navy-50 hover:text-navy-700
          focus-visible:ring-2 focus-visible:ring-navy-300 md:hidden"
      >
        <svg
          aria-hidden
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-5 w-5"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        </svg>
      </Link>

      <form
      role="search"
      onSubmit={(event) => {
        event.preventDefault();
        if (!canSubmit) return;
        navigate(`/search?q=${encodeURIComponent(term.trim())}`);
      }}
      className="relative hidden md:block"
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
        className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400"
      >
        <circle cx="11" cy="11" r="7" />
        <path d="m20 20-3.5-3.5" />
      </svg>
      <input
        id="header-search"
        type="search"
        value={term}
        onChange={(event) => setTerm(event.target.value)}
        placeholder="Search documents…"
        className="w-56 rounded-lg border border-line bg-surface-sunken py-1.5 pl-8 pr-3 text-sm
          outline-none transition-all duration-[--duration-base] ease-[--ease-settle]
          placeholder:text-slate-400 focus:w-72 focus:border-navy-400 focus:bg-surface
          focus:ring-2 focus:ring-navy-200"
      />
      </form>
    </>
  );
}
