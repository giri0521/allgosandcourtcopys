import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { HeaderSearch } from '@/components/layout/HeaderSearch';
import { NotificationBell } from '@/components/layout/NotificationBell';
import { Sidebar } from '@/components/layout/Sidebar';
import { ThemeToggle } from '@/components/ui/ThemeToggle';
import { useAuth } from '@/lib/auth-context';

/** Remembered so the rail keeps its width across screens and across sessions. */
const COLLAPSE_STORAGE_KEY = 'allgos.sidebar.collapsed';

function readCollapsed(): boolean {
  try {
    return window.localStorage.getItem(COLLAPSE_STORAGE_KEY) === '1';
  } catch {
    // Private browsing, or storage disabled by policy. An expanded rail is the safe default.
    return false;
  }
}

/**
 * The frame every signed-in screen sits in.
 *
 * <p>Anything with its own layout instead of this one ends up without navigation — which is exactly
 * how the home screen shipped unreachable in Phase 3. If a new screen needs a different shape,
 * change this; do not go around it.
 *
 * <p>Navigation is a {@link Sidebar} rather than a header row: a header can hold five entries before
 * it starts hiding things, and this application has fifteen. What stays in the header is what is not
 * navigation — search, and the account cluster in the top-right corner where that kind of control is
 * looked for. Below `lg` the cluster moves into the drawer, leaving the phone header with a
 * hamburger, the mark and a full-width field.
 *
 * <p>The brand belongs to whichever of the two is wide enough for it: the rail while it is expanded,
 * the header once it collapses.
 *
 * <p>Both are sticky, so scrolling a long folder never strands the user without a way out.
 */
export function AppShell({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [menuOpen, setMenuOpen] = useState(false);

  const isAdmin = user?.role === 'ADMIN';

  const handleSignOut = async () => {
    await signOut();
    navigate('/login');
  };

  useEffect(() => {
    try {
      window.localStorage.setItem(COLLAPSE_STORAGE_KEY, collapsed ? '1' : '0');
    } catch {
      // Nothing to do: the rail simply forgets between sessions.
    }
  }, [collapsed]);

  // Stable, because the drawer closes itself from an effect keyed on the route — a fresh function
  // each render would re-run that effect on every render instead.
  const closeMenu = useCallback(() => setMenuOpen(false), []);

  return (
    // No background of its own: the body's wash shows through, so every screen sits on the same
    // ground rather than a flat grey panel over it.
    //
    // The rail's width is a variable rather than a pair of literals, so the aside and the padding
    // that clears it can never disagree. It only narrows from `lg` up; below that the rail is a
    // drawer laid over the page, and the page is not indented for it at all.
    <div
      className={`min-h-screen [--sidebar-w:17rem] ${
        collapsed ? 'lg:[--sidebar-w:4.75rem]' : 'lg:[--sidebar-w:17rem]'
      }`}
    >
      <Sidebar
        collapsed={collapsed}
        onToggleCollapsed={() => setCollapsed((value) => !value)}
        open={menuOpen}
        onClose={closeMenu}
      />

      <div
        className="flex min-h-screen flex-col transition-[padding] duration-[--duration-rail]
          ease-[--ease-settle] lg:pl-[var(--sidebar-w)]"
      >
        {/*
          The same skin as the rail, from the same `--header-*` tokens: pale sky blue carrying navy
          ink in the light theme, deep navy carrying white in the dark one. Two different chromes
          meeting at the top-left corner is the seam this removes — the rail and the bar are one
          L-shaped frame around the page, and a frame should be one colour.

          Opaque rather than translucent, for the same reason: a bar that lets the page tint it is a
          bar that stops matching the rail as soon as anything scrolls under it.
        */}
        <header
          className="sticky top-0 z-30 border-b border-[var(--header-edge)]
            bg-[image:var(--header-bg)] shadow-[var(--header-shadow)]"
        >
          <div className="flex items-center gap-2 px-3 py-3 sm:gap-3 sm:px-6 sm:py-4 lg:px-8">
            {/* The way into navigation on a phone, and the only reason the drawer is reachable at
                all below `lg`. */}
            <button
              type="button"
              onClick={() => setMenuOpen(true)}
              aria-label="Open menu"
              aria-expanded={menuOpen}
              className="-ml-1 rounded-lg p-2 text-[var(--header-ink-muted)] outline-none
                transition-colors duration-[--duration-quick] ease-[--ease-settle]
                hover:bg-[var(--header-hover)] hover:text-[var(--header-ink)] focus-visible:ring-2
                focus-visible:ring-[var(--header-ring)] active:scale-[0.94] lg:hidden"
            >
              <svg
                aria-hidden
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={1.8}
                strokeLinecap="round"
                className="h-5.5 w-5.5"
              >
                <path d="M4 7h16M4 12h16M4 17h16" />
              </svg>
            </button>

            {/*
              The brand, wherever the rail is not carrying it: below `lg`, where the rail is
              off-canvas along with its logo, and on a desktop whenever the rail is collapsed. A
              screen with no visible identity at all reads as a broken page, and shrinking the seal
              to an anonymous disc in the rail would be the same thing more slowly.

              This is the other end of the hop. Rather than being switched on when the rail folds,
              it is always in the DOM and its width, scale and opacity are animated — so the seal
              appears to leave the rail and land here, arriving a beat after the rail's copy has
              gone (and leaving a beat before that copy comes back). `--ease-hop` overshoots very
              slightly at the end, which is what makes it read as a landing rather than a fade.

              The name comes with it above `sm` only — on a 360px phone it would take the width the
              search field needs.
            */}
            <Link
              to="/home"
              className={`group flex shrink-0 items-center gap-2.5 overflow-hidden rounded-lg
                outline-none transition-[max-width,opacity,transform,margin,visibility]
                duration-[--duration-rail] ease-[--ease-hop] focus-visible:ring-2
                focus-visible:ring-[var(--header-ring)] ${
                  collapsed
                    ? 'lg:max-w-96 lg:translate-x-0 lg:scale-100 lg:opacity-100 lg:delay-[110ms] lg:visible'
                    : 'lg:pointer-events-none lg:invisible lg:-ml-2 lg:max-w-0 lg:-translate-x-3 lg:scale-75 lg:opacity-0'
                }`}
            >
              <img
                src="/logo.webp"
                alt=""
                width={88}
                height={88}
                className="h-10 w-10 shrink-0 rounded-full object-cover transition-transform
                  duration-[--duration-base] ease-[--ease-settle] group-hover:scale-105 sm:h-12 sm:w-12"
              />
              <span
                className="hidden whitespace-nowrap text-[1.1875rem] leading-none font-bold
                  tracking-tight text-[var(--header-ink)] sm:inline"
              >
                All GO’s AND COURT COPIES
              </span>
            </Link>

            <HeaderSearch />

            {/*
              The account cluster, in the corner people look for it. Desktop only: on a phone these
              four live in the drawer's foot, where they have room to be labelled rather than
              crowding the search field down to nothing.
            */}
            <div className="ml-auto hidden shrink-0 items-center gap-1 lg:flex">
              <NotificationBell
                className="text-[var(--header-ink-muted)] hover:bg-[var(--header-hover)]
                  hover:text-[var(--header-ink)] focus-visible:ring-[var(--header-ring)]"
              />
              {/* The quick two-way flip, one click from anywhere. The three-way choice, including
                  following the operating system, is on My Profile. */}
              <ThemeToggle className="text-[var(--header-ink-muted)]! hover:bg-[var(--header-hover)]! hover:text-[var(--header-ink)]!" />
              {/* The name is the way into My Profile — the place people look for it. */}
              <NavLink
                to="/profile"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm outline-none transition-colors
                   duration-[--duration-base] ease-[--ease-settle] focus-visible:ring-2
                   focus-visible:ring-[var(--header-ring)] ${
                     isActive
                       ? 'bg-[var(--header-active)] text-[var(--header-ink)]'
                       : 'text-[var(--header-ink-muted)] hover:bg-[var(--header-hover)] hover:text-[var(--header-ink)]'
                   }`
                }
              >
                <span
                  className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full
                    bg-[var(--header-active)] text-[var(--header-ink)]"
                >
                  <svg
                    aria-hidden
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth={1.7}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    className="h-4 w-4"
                  >
                    <path d="M12 11.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7M5 20c0-3.3 3.1-5.5 7-5.5s7 2.2 7 5.5" />
                  </svg>
                </span>
                <span className="max-w-40 truncate font-medium">{user?.fullName}</span>
                {isAdmin && (
                  <span
                    className="rounded-full bg-[var(--header-active)] px-2 py-0.5 text-[10px]
                      font-semibold text-[var(--header-marker)]"
                  >
                    Admin
                  </span>
                )}
              </NavLink>
              <button
                type="button"
                onClick={handleSignOut}
                className="rounded-lg px-3 py-1.5 text-sm font-semibold text-[var(--header-ink-muted)]
                  outline-none transition-all duration-[--duration-quick] ease-[--ease-settle]
                  hover:bg-[var(--header-hover)] hover:text-[var(--header-ink)] focus-visible:ring-2
                  focus-visible:ring-[var(--header-ring)] active:scale-[0.97]"
              >
                Sign out
              </button>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-[100rem] flex-1 px-4 py-7 sm:px-6 sm:py-9 lg:px-8">
          {/*
            The title alone. There was a short gold-into-navy rule above it, which read as an
            unexplained stripe rather than as a letterhead's mark — the chrome already carries the
            brand, and a second flag on every page was one too many.
          */}
          <div className="animate-fade mb-7 flex flex-wrap items-start justify-between gap-4 print:hidden">
            <div className="min-w-0">
              <h1 className="text-[1.75rem] leading-tight font-semibold text-navy-900">{title}</h1>
              {subtitle && (
                <p className="mt-1.5 max-w-2xl text-[0.9375rem] leading-relaxed text-slate-500">
                  {subtitle}
                </p>
              )}
            </div>
            {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
          </div>
          {children}
        </main>

        {/* Small, quiet, and on every signed-in screen — the pages people only look for when
            something has gone wrong or an auditor has asked. */}
        <footer className="mx-auto w-full max-w-[100rem] px-4 pt-4 pb-8 sm:px-6 lg:px-8">
          <div
            className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4
              text-xs text-slate-500"
          >
            <p>All GO’s AND COURT COPIES · Document Management System</p>
            <nav className="flex gap-4" aria-label="Information">
              <Link to="/help" className="hover:text-navy-700 hover:underline">
                Help
              </Link>
              <Link to="/about" className="hover:text-navy-700 hover:underline">
                About
              </Link>
              <Link to="/privacy" className="hover:text-navy-700 hover:underline">
                Privacy
              </Link>
            </nav>
          </div>
        </footer>
      </div>
    </div>
  );
}
