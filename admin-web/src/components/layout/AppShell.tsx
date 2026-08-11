import type { ReactNode } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
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
  { to: '/admin/requests', label: 'Requests', adminOnly: true },
  { to: '/admin/members', label: 'Members', adminOnly: true },
];

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
    <div className="min-h-screen bg-slate-100">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-3 px-4 py-3">
          <Link to="/home" className="flex items-center gap-2.5">
            <img
              src="/logo.webp"
              alt=""
              width={36}
              height={36}
              className="h-9 w-9 rounded-full object-cover"
            />
            <span className="text-sm font-bold tracking-tight text-navy-800">
              ALLGOSANDCOURTCOPYS
            </span>
          </Link>

          <nav className="flex items-center gap-1" aria-label="Main">
            {items.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `rounded-md px-3 py-1.5 text-sm font-medium transition ${
                    isActive ? 'bg-navy-50 text-navy-700' : 'text-slate-600 hover:text-navy-700'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm text-slate-600 sm:inline">
              {user?.fullName}
              {isAdmin && (
                <span className="ml-2 rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700">
                  Admin
                </span>
              )}
            </span>
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-md px-3 py-1.5 text-sm font-semibold text-navy-600 transition hover:bg-navy-50"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
            {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          </div>
          {actions}
        </div>
        {children}
      </main>
    </div>
  );
}
