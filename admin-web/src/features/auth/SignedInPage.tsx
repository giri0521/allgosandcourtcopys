import { Navigate, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/lib/auth-context';

/**
 * A minimal landing page confirming the session is real. The full Home dashboard from the plan is
 * built in Phase 4; this exists so the authentication flow can be exercised end to end.
 */
export function SignedInPage() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const initials = user.fullName
    .split(' ')
    .map((part) => part[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();

  const handleSignOut = async () => {
    await signOut();
    navigate('/login');
  };

  return (
    <main className="min-h-screen bg-slate-100 px-4 py-10">
      <div className="mx-auto max-w-2xl space-y-5">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center gap-4">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-navy-600 text-lg font-bold text-white">
              {initials}
            </div>
            <div>
              <h1 className="text-xl font-semibold text-slate-900">Welcome, {user.fullName}</h1>
              <p className="text-sm text-slate-500">
                {user.role === 'admin' ? 'System Administrator' : 'Member'}
                {user.departmentName ? ` · ${user.departmentName}` : ''}
              </p>
            </div>
          </div>

          <dl className="mt-6 grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Row label="Mobile" value={`+91 ${user.mobileNumber}`} />
            <Row label="Designation" value={user.designation ?? '—'} />
            <Row label="Account status" value={user.status} />
            <Row label="Email" value={user.email ?? '—'} />
          </dl>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-sm font-semibold text-slate-900">Coming next</h2>
          <p className="mt-1 text-sm text-slate-600">
            Departments, folders, uploads, search and the admin dashboard arrive in the following
            phases. Signing in is what this build proves.
          </p>
          <div className="mt-4">
            <Button variant="secondary" onClick={handleSignOut}>
              Sign out
            </Button>
          </div>
        </div>
      </div>
    </main>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  );
}
