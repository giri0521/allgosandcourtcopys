import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { AuditEntryRow } from '@/features/admin/audit/AuditEntryRow';
import { fetchMemberActivity } from '@/features/admin/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { initials, tone } from '@/lib/tones';

/**
 * One member: who they are, what they have done, and the trail of it.
 *
 * <p>The timeline is the audit log filtered to this person — the same evidence a security review
 * reads, not a friendlier parallel record that could disagree with it.
 */
export function MemberActivityPage() {
  const { memberId = '' } = useParams();
  const [page, setPage] = useState(0);

  const activity = useQuery({
    queryKey: ['member-activity', memberId, page],
    queryFn: () => fetchMemberActivity(memberId, page),
  });

  if (activity.isError) {
    return (
      <AppShell title="Member">
        <Alert tone="error">{toApiError(activity.error).message}</Alert>
        <Link to="/admin/members" className="mt-4 inline-block text-sm font-semibold text-navy-600 hover:underline">
          Back to members
        </Link>
      </AppShell>
    );
  }

  const member = activity.data?.member;
  const summary = activity.data?.summary;
  const timeline = activity.data?.timeline;

  return (
    <AppShell
      title={member?.fullName ?? 'Member'}
      subtitle={
        member
          ? `${member.role === 'ADMIN' ? 'Administrator' : 'Member'}${
              member.departmentName ? ` · ${member.departmentName}` : ''
            }`
          : undefined
      }
      actions={
        <Link
          to="/admin/members"
          className="inline-flex items-center rounded-lg border border-navy-300 bg-surface px-4 py-2.5
            text-sm font-semibold text-navy-700 transition-colors duration-[--duration-base]
            hover:bg-navy-50"
        >
          All members
        </Link>
      }
    >
      {activity.isPending ? (
        <SkeletonRows count={5} label="Loading this member's activity" />
      ) : (
        <div className="grid gap-6 lg:grid-cols-3">
          <div className="space-y-6 lg:col-span-2">
            <section className="stagger grid gap-4 sm:grid-cols-4">
              <Stat label="Uploads" value={summary?.uploads ?? 0} toneName="navy" />
              <Stat label="Downloads" value={summary?.downloads ?? 0} toneName="sky" />
              <Stat label="Deletions" value={summary?.deletions ?? 0} toneName="rose" />
              <Stat label="Sign-ins" value={summary?.logins ?? 0} toneName="emerald" />
            </section>

            <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-card">
              <div className="border-b border-line px-5 py-3">
                <h2 className="font-semibold text-slate-900">Activity</h2>
                <p className="mt-0.5 text-sm text-slate-500">
                  Everything recorded against this account, newest first.
                </p>
              </div>

              {(timeline?.items.length ?? 0) === 0 ? (
                <p className="px-5 py-10 text-center text-sm text-slate-500">
                  Nothing recorded for this member yet.
                </p>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {timeline?.items.map((entry) => (
                    // The actor is this member on every row, so naming them each time is noise.
                    <AuditEntryRow key={entry.id} entry={entry} showActor={false} />
                  ))}
                </ul>
              )}
            </section>

            {(timeline?.totalPages ?? 0) > 1 && (
              <div className="flex items-center justify-between text-sm text-slate-500">
                <span>
                  Page {(timeline?.page ?? 0) + 1} of {timeline?.totalPages}
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    disabled={page === 0}
                    onClick={() => setPage((current) => current - 1)}
                  >
                    Previous
                  </Button>
                  <Button
                    variant="secondary"
                    disabled={page + 1 >= (timeline?.totalPages ?? 1)}
                    onClick={() => setPage((current) => current + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </div>

          <aside>
            <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
              <div className="flex items-center gap-3">
                <span
                  className={`flex h-12 w-12 items-center justify-center rounded-full text-sm font-bold
                    ${tone('navy').chip}`}
                >
                  {initials(member?.fullName ?? '')}
                </span>
                <div className="min-w-0">
                  <p className="truncate font-semibold text-slate-900">{member?.fullName}</p>
                  {member && <StatusBadge status={member.status} />}
                </div>
              </div>

              <dl className="mt-5 space-y-3 text-sm">
                <Row label="Mobile" value={member ? `+91 ${member.mobileNumber}` : '—'} />
                <Row label="Email" value={member?.email ?? '—'} />
                <Row label="Designation" value={member?.designation ?? '—'} />
                <Row label="Department" value={member?.departmentName ?? '—'} />
                <Row label="Last sign-in" value={formatDateTime(summary?.lastLoginAt ?? null)} />
                <Row label="Member since" value={formatDateTime(member?.createdAt ?? null)} />
              </dl>
            </div>
          </aside>
        </div>
      )}
    </AppShell>
  );
}

function Stat({
  label,
  value,
  toneName,
}: {
  label: string;
  value: number;
  toneName: Parameters<typeof tone>[0];
}) {
  return (
    <div className="relative overflow-hidden rounded-xl border border-line bg-surface p-4 shadow-card">
      <span aria-hidden className={`absolute inset-x-0 top-0 h-1 ${tone(toneName).edge}`} />
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-xl font-semibold tabular-nums text-navy-800">{value}</p>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-700">{value}</dd>
    </div>
  );
}
