import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { HeaderSearch } from '@/components/layout/HeaderSearch';
import { NotificationBell } from '@/components/layout/NotificationBell';
import { useAuth } from '@/lib/auth-context';

interface NavItem {
  to: string;
  label: string;
  adminOnly?: boolean;
}

/**
 * Navigation for the signed-in application.
 *
 * <p>Admin entries are hidden from members for tidiness only — the server refuses them regardless,
 * so nothing here is a permission check.
 */
const NAV: NavItem[] = [
  { to: '/home', label: 'Home' },
  { to: '/departments', label: 'Departments' },
  { to: '/my-uploads', label: 'My Uploads' },
  { to: '/favorites', label: 'Favorites' },
  // Three admin entries, not six. The dashboard is the hub for the rest — Reports, the deletions
  // log and the activity log all hang off it, and a header with ten links is a header nobody reads.
  { to: '/admin', label: 'Dashboard', adminOnly: true },
  { to: '/admin/requests', label: 'Requests', adminOnly: true },
  { to: '/admin/members', label: 'Members', adminOnly: true },
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

  const isAdmin = user?.role === 'ADMIN';
  const items = NAV.filter((item) => !item.adminOnly || isAdmin);

  const handleSignOut = async () => {
    await signOut();
    navigate('/login');
  };

  return (
    // No background of its own: the body's wash shows through, so every screen sits on the same
    // ground rather than a flat grey panel over it.
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-line bg-surface/85 backdrop-blur-md">
        {/* A hairline of the brand colours across the very top — navy into gold, the seal's two
            colours. It is the one piece of pure decoration in the chrome. */}
        <div
          aria-hidden
          className="h-0.5 w-full bg-gradient-to-r from-navy-600 via-navy-400 to-gold-400"
        />
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
          <Link
            to="/home"
            className="group flex items-center gap-2.5 rounded-lg outline-none
              focus-visible:ring-2 focus-visible:ring-navy-300"
          >
            <img
              src="/logo.webp"
              alt=""
              width={36}
              height={36}
              className="h-9 w-9 rounded-full object-cover transition-transform duration-[--duration-base]
                ease-[--ease-settle] group-hover:scale-105"
            />
            <span className="text-sm font-bold tracking-tight text-navy-800">
              ALLGOSANDCOURTCOPYS
            </span>
          </Link>

          <nav className="flex items-center gap-1 overflow-x-auto" aria-label="Main">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `relative rounded-md px-3 py-1.5 text-sm font-medium whitespace-nowrap outline-none
                   transition-colors duration-[--duration-base] ease-[--ease-settle]
                   focus-visible:ring-2 focus-visible:ring-navy-300 ${
                     isActive ? 'text-navy-700' : 'text-slate-600 hover:text-navy-700'
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
                      className={`absolute inset-x-2 -bottom-px h-0.5 origin-center rounded-full bg-navy-600
                        transition-transform duration-[--duration-base] ease-[--ease-settle] ${
                          isActive ? 'scale-x-100' : 'scale-x-0'
                        }`}
                    />
                  </>
                )}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2 sm:gap-3">
            <HeaderSearch />
            <NotificationBell />
            {/* The name is the way into My Profile — the place people look for it. */}
            <NavLink
              to="/profile"
              className={({ isActive }) =>
                `hidden rounded-md px-2 py-1 text-sm outline-none transition-colors
                 duration-[--duration-base] focus-visible:ring-2 focus-visible:ring-navy-300
                 lg:inline-flex lg:items-center ${
                   isActive ? 'text-navy-700' : 'text-slate-600 hover:text-navy-700'
                 }`
              }
            >
              {user?.fullName}
              {isAdmin && (
                <span className="ml-2 rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700">
                  Admin
                </span>
              )}
            </NavLink>
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-md px-3 py-1.5 text-sm font-semibold text-navy-600 outline-none
                transition-all duration-[--duration-quick] ease-[--ease-settle] hover:bg-navy-50
                focus-visible:ring-2 focus-visible:ring-navy-300 active:scale-[0.97]"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="animate-fade mb-6 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-slate-900">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          </div>
          {actions}
        </div>
        {children}
      </main>

      {/* Small, quiet, and on every signed-in screen — the pages people only look for when
          something has gone wrong or an auditor has asked. */}
      <footer className="mx-auto max-w-6xl px-4 pb-8 pt-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-4
          text-xs text-slate-500">
          <p>ALLGOSANDCOURTCOPYS · Document Management System</p>
          <nav className="flex gap-4" aria-label="Information">
            <Link to="/help" className="hover:text-navy-700 hover:underline">Help</Link>
            <Link to="/about" className="hover:text-navy-700 hover:underline">About</Link>
            <Link to="/privacy" className="hover:text-navy-700 hover:underline">Privacy</Link>
          </nav>
        </div>
      </footer>
    </div>
  );
}
