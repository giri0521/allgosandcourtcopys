import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/lib/auth-context';

/**
 * Route guards.
 *
 * <p>These are a convenience, not a control. Every admin endpoint is guarded server-side by
 * {@code @PreAuthorize}, so a member who types an admin URL is refused by the API whatever the
 * router does; the guard only saves them from a screen full of 403s. Editing this file can never
 * grant access it does not already have.
 */

/** Shown while the start-up refresh decides whether there is a session to restore. */
function Restoring() {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-live="polite">
      <span
        aria-hidden
        className="h-6 w-6 animate-spin rounded-full border-2 border-navy-500 border-t-transparent"
      />
      <span className="sr-only">Restoring your session</span>
    </div>
  );
}

/** Requires any signed-in account. Remembers where the user was headed. */
export function RequireAuth() {
  const { user, restoring } = useAuth();
  const location = useLocation();

  if (restoring) return <Restoring />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;

  return <Outlet />;
}

/** Requires an admin. A signed-in member gets Access Restricted, not the login screen. */
export function RequireAdmin() {
  const { user, restoring } = useAuth();
  const location = useLocation();

  if (restoring) return <Restoring />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.role !== 'ADMIN') return <Navigate to="/restricted" replace />;

  return <Outlet />;
}
