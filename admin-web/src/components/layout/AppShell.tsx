import type { ReactNode } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { HeaderSearch } from "@/components/layout/HeaderSearch";
import { NotificationBell } from "@/components/layout/NotificationBell";
import { ThemeToggle } from "@/components/ui/ThemeToggle";
import { useAuth } from "@/lib/auth-context";

interface NavItem {
  to: string;
  label: string;
  adminOnly?: boolean;
  /**
   * Marks this entry active only on an exact path match.
   *
   * <p>Needed wherever one nav path is a prefix of another. `/admin` is a prefix of
   * `/admin/requests`, so without this the Dashboard entry lights up on Requests, Members, Reports
   * and the logs as well — two underlined tabs at once, and no way to tell where you are.
   *
   * <p>Deliberately *not* set on `/departments`, where prefix matching is what we want: browsing
   * into `/departments/{id}` should keep Departments highlighted.
   */
  exact?: boolean;
}

/**
 * Navigation for the signed-in application.
 *
 * <p>Admin entries are hidden from members for tidiness only — the server refuses them regardless,
 * so nothing here is a permission check.
 */
const NAV: NavItem[] = [
  // Everyone's, and for a member the only entry in the header. The phonebook is a thing people
  // come to the application for rather than something they arrive at from a document.
  { to: "/phonebook", label: "Phonebook" },
  { to: "/letters", label: "Letters" },
  // Three admin entries, not six. The dashboard is the hub for the rest — Reports, the deletions
  // log and the activity log all hang off it, and a header with ten links is a header nobody reads.
  { to: "/admin", label: "Dashboard", adminOnly: true, exact: true },
  { to: "/admin/requests", label: "Requests", adminOnly: true },
  { to: "/admin/members", label: "Members", adminOnly: true },
];

/**
 * The frame every signed-in screen sits in.
 *
 * <p>Anything with its own layout instead of this one ends up without navigation — which is exactly
 * how the home screen shipped unreachable in Phase 3. If a new screen needs a different shape,
 * change this; do not go around it.
 *
 * <p>The header is sticky and slightly translucent, so scrolling a long folder never strands the
 * user without a way out. The active nav item keeps an underline that slides between entries rather
 * than jumping, which is the one flourish here that is purely for pleasure.
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

  const isAdmin = user?.role === "ADMIN";
  const items = NAV.filter((item) => !item.adminOnly || isAdmin);

  const handleSignOut = async () => {
    await signOut();
    navigate("/login");
  };

  return (
    // No background of its own: the body's wash shows through, so every screen sits on the same
    // ground rather than a flat grey panel over it.
    <div className="min-h-screen">
      {/*
        Deep navy in both themes, written as fixed colours rather than palette steps: the ramp
        inverts in dark, so a step here would turn the bar pale on exactly the theme it is meant to
        anchor. Everything inside it is therefore light-on-dark in both themes too.
      */}
      <header
        className="sticky top-0 z-40 border-b border-[var(--header-edge)]
          bg-[image:var(--header-bg)] shadow-[var(--header-shadow)]"
      >
        {/* A hairline of the brand colours across the very top — navy into gold, the seal's two
            colours. It is the one piece of pure decoration in the chrome. */}
        <div
          aria-hidden
          className="h-0.5 w-full bg-gradient-to-r from-navy-600 via-navy-400 to-gold-400"
        />
        <div
          className="mx-auto flex max-w-[120rem] flex-wrap items-center gap-x-4 gap-y-3 px-4 py-3
            sm:px-6 lg:gap-x-3 lg:flex-nowrap lg:px-10"
        >
          <Link
            to="/home"
            className="group flex items-center gap-2.5 rounded-lg outline-none
              focus-visible:ring-2 focus-visible:ring-[var(--header-ring)]"
          >
            <img
              src="/logo.webp"
              alt=""
              width={88}
              height={88}
              className="h-22 w-22 rounded-full object-cover transition-transform duration-[--duration-base]
                ease-[--ease-settle] group-hover:scale-105"
            />
            <span className="text-[1.75rem] leading-none font-bold tracking-tight text-[var(--header-ink)]">
              All GO’s AND COURT COPIES
            </span>
          </Link>

          {/* Members are left with no entries at all now that Home and Departments have gone from
              the header, and an empty <nav> still eats a gap in the flex row. */}
          {items.length > 0 && (
            <nav
              className="flex items-center gap-0.5 overflow-x-auto overflow-y-hidden lg:min-w-0 lg:flex-1"
              aria-label="Main"
            >
              {items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.exact}
                  className={({ isActive }) =>
                    `relative rounded-md px-2 py-1.5 text-sm font-medium whitespace-nowrap outline-none
                   transition-colors duration-[--duration-base] ease-[--ease-settle]
                   focus-visible:ring-2 focus-visible:ring-[var(--header-ring)] ${
                     isActive
                       ? "bg-[var(--header-active)] text-[var(--header-ink)]"
                       : "text-[var(--header-ink-muted)] hover:bg-[var(--header-hover)] hover:text-[var(--header-ink)]"
                   }`
                  }
                >
                  {({ isActive }) => (
                    <>
                      {item.label}
                      {/* Scales out from the centre, so moving between tabs reads as one indicator
                        travelling rather than two separate underlines. */}
                      <span
                        aria-hidden
                        className={`absolute inset-x-2 bottom-0 h-0.5 origin-center rounded-full bg-gold-400
                        transition-transform duration-[--duration-base] ease-[--ease-settle] ${
                          isActive ? "scale-x-100" : "scale-x-0"
                        }`}
                      />
                    </>
                  )}
                </NavLink>
              ))}
            </nav>
          )}

          <div className="ml-auto flex shrink-0 items-center gap-1 sm:gap-2">
            <HeaderSearch />
            <NotificationBell />
            {/* Beside the bell rather than buried in the profile screen: people who want a dark
                interface want it now, not after two navigations. The three-way choice, including
                following the operating system, is on My Profile. */}
            <ThemeToggle className="text-[var(--header-ink-muted)]! hover:bg-[var(--header-hover)]! hover:text-[var(--header-ink)]!" />
            {/* The name is the way into My Profile — the place people look for it. */}
            <NavLink
              to="/profile"
              className={({ isActive }) =>
                `hidden rounded-md px-2 py-1 text-sm outline-none transition-colors
                 duration-[--duration-base] focus-visible:ring-2 focus-visible:ring-[var(--header-ring)]
                 lg:inline-flex lg:items-center ${
                   isActive ? "text-[var(--header-ink)]" : "text-[var(--header-ink-muted)] hover:text-[var(--header-ink)]"
                 }`
              }
            >
              {user?.fullName}
              {isAdmin && (
                <span className="ml-2 rounded-full bg-gold-400/20 px-2 py-0.5 text-xs font-semibold text-gold-400">
                  Admin
                </span>
              )}
            </NavLink>
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-md px-3 py-1.5 text-sm font-semibold text-[var(--header-ink-muted)] outline-none
                transition-all duration-[--duration-quick] ease-[--ease-settle] hover:bg-[var(--header-hover)]
                hover:text-[var(--header-ink)] focus-visible:ring-2 focus-visible:ring-[var(--header-ring)] active:scale-[0.97]"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[120rem] px-4 py-8 sm:px-6 sm:py-10 lg:px-10">
        {/*
          The page title carries a short gold rule above it — the seal's second colour, used the way
          a letterhead uses one. It is the only ornament on the page, which is what lets it read as
          deliberate rather than decorative.
        */}
        <div className="animate-fade mb-7 flex flex-wrap items-start justify-between gap-4 print:hidden">
          <div className="min-w-0">
            <span
              aria-hidden
              className="mb-3 block h-[3px] w-12 rounded-full bg-gradient-to-r from-gold-400 to-navy-400"
            />
            <h1 className="text-[1.75rem] leading-tight font-semibold text-navy-900">
              {title}
            </h1>
            {subtitle && (
              <p className="mt-1.5 max-w-2xl text-[0.9375rem] leading-relaxed text-slate-500">
                {subtitle}
              </p>
            )}
          </div>
          {actions && (
            <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>
          )}
        </div>
        {children}
      </main>

      {/* Small, quiet, and on every signed-in screen — the pages people only look for when
          something has gone wrong or an auditor has asked. */}
      <footer className="mx-auto max-w-[120rem] px-4 pb-8 pt-4 sm:px-6 lg:px-10">
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
  );
}
