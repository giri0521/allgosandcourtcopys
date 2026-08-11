import { Link, useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { AuthLayout } from '@/features/auth/AuthLayout';
import { useAuth } from '@/lib/auth-context';

/**
 * Where a 403 lands.
 *
 * <p>Reached either by the route guard, when a member types an admin URL, or by {@code api.ts} when
 * the server refuses a request outright. The wording says who you are signed in as, because the
 * usual cause is being signed in as the wrong person rather than anything being broken.
 */
export function AccessRestrictedPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <AuthLayout
      title="Access restricted"
      subtitle="This area is limited to administrators"
      footer={
        <>
          Think this is a mistake? Contact your administrator, or{' '}
          <Link to="/login" className="font-semibold text-navy-600 hover:underline">
            sign in
          </Link>{' '}
          with a different account.
        </>
      }
    >
      <div className="space-y-4 text-sm text-slate-600">
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
          <p className="font-semibold">You do not have access to that page</p>
          <p className="mt-1">
            {user
              ? `You are signed in as ${user.fullName}, a ${
                  user.role === 'ADMIN' ? 'administrator' : 'member'
                }.`
              : 'You are not signed in.'}
          </p>
        </div>

        <p>
          Approved members can view, download and upload documents in every department. Registration
          review, member administration and reports are reserved for administrators.
        </p>

        <Button fullWidth variant="secondary" onClick={() => navigate(user ? '/home' : '/login')}>
          {user ? 'Back to home' : 'Go to sign in'}
        </Button>
      </div>
    </AuthLayout>
  );
}
