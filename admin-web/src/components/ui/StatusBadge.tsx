import type { RegistrationStatus, UserStatus } from '@/types/api';

const styles: Record<UserStatus | RegistrationStatus, string> = {
  PENDING: 'border-amber-200 bg-amber-50 text-amber-800',
  ACTIVE: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  APPROVED: 'border-emerald-200 bg-emerald-50 text-emerald-800',
  INACTIVE: 'border-slate-300 bg-slate-100 text-slate-700',
  REJECTED: 'border-red-200 bg-red-50 text-red-800',
};

const labels: Record<UserStatus | RegistrationStatus, string> = {
  PENDING: 'Pending',
  ACTIVE: 'Active',
  APPROVED: 'Approved',
  INACTIVE: 'Disabled',
  REJECTED: 'Rejected',
};

/** The one place a status is turned into words, so the tabs and the rows never disagree. */
export function StatusBadge({ status }: { status: UserStatus | RegistrationStatus }) {
  return (
    <span
      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-semibold ${styles[status]}`}
    >
      {labels[status]}
    </span>
  );
}
