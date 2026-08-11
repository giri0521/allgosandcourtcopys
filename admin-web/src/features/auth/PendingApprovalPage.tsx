import { Link, useLocation } from 'react-router-dom';
import { AuthLayout } from '@/features/auth/AuthLayout';

export function PendingApprovalPage() {
  const location = useLocation();
  const state = location.state as { mobileNumber?: string; verified?: boolean } | null;
  const mobileNumber = state?.mobileNumber;

  return (
    <AuthLayout
      title="Registration received"
      footer={
        <Link to="/login" className="font-semibold text-navy-600 hover:underline">
          Back to sign in
        </Link>
      }
    >
      <div className="space-y-4 text-sm text-slate-600">
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
          <p className="font-semibold">Awaiting administrator approval</p>
          <p className="mt-1">
            Your account is not active yet. An administrator must approve it before you can sign in.
          </p>
        </div>

        {mobileNumber &&
          (state?.verified ? (
            <p>
              <span className="font-semibold">+91 {mobileNumber}</span> has been confirmed.
            </p>
          ) : (
            <p>
              <span className="font-semibold">+91 {mobileNumber}</span> was not confirmed. Your
              request has still reached the administrators.
            </p>
          ))}

        <p>
          You will be notified once your request has been reviewed. If it is urgent, contact your
          administrator directly.
        </p>
      </div>
    </AuthLayout>
  );
}
