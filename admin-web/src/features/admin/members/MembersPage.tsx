import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  changeMemberStatus,
  fetchMemberCounts,
  fetchMembers,
  type MemberFilter,
} from '@/features/admin/api';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import type { Member, MemberCounts } from '@/types/api';

const TABS: { value: MemberFilter; label: string; countKey: keyof MemberCounts }[] = [
  { value: 'all', label: 'All', countKey: 'all' },
  { value: 'PENDING', label: 'Pending', countKey: 'pending' },
  { value: 'ACTIVE', label: 'Active', countKey: 'active' },
  { value: 'INACTIVE', label: 'Disabled', countKey: 'inactive' },
];

/**
 * Everyone with an account, and the enable/disable switch.
 *
 * <p>A pending account is deliberately not activatable from here: the server sends it back to the
 * requests queue, so every activation leaves a reviewed request behind it.
 */
export function MembersPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<MemberFilter>('all');
  const [query, setQuery] = useState('');
  const [search, setSearch] = useState('');
  const [confirming, setConfirming] = useState<Member | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // Debounced, so typing in the search box does not fire a request per keystroke.
  useEffect(() => {
    const timer = window.setTimeout(() => setSearch(query), 300);
    return () => window.clearTimeout(timer);
  }, [query]);

  const members = useQuery({
    queryKey: ['members', tab, search],
    queryFn: () => fetchMembers(tab, search),
  });

  const counts = useQuery({ queryKey: ['member-counts'], queryFn: fetchMemberCounts });

  const toggle = useMutation({
    mutationFn: (member: Member) =>
      changeMemberStatus(member.id, member.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'),
    onSuccess: async (_result, member) => {
      setNotice(
        member.status === 'ACTIVE'
          ? `${member.fullName} has been disabled and signed out.`
          : `${member.fullName} can sign in again.`,
      );
      setConfirming(null);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['members'] }),
        queryClient.invalidateQueries({ queryKey: ['member-counts'] }),
      ]);
    },
  });

  const error = members.error ?? counts.error ?? toggle.error;
  const items = members.data?.items ?? [];
  const summary = counts.data;

  return (
    <AppShell
      title="Members"
      subtitle="Everyone with an account. Once approved, every member can view, download and upload in any department."
      actions={
        summary && summary.pendingRequests > 0 ? (
          <Link
            to="/admin/requests"
            className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-900 transition hover:bg-amber-100"
          >
            {summary.pendingRequests} request{summary.pendingRequests === 1 ? '' : 's'} awaiting
            review
          </Link>
        ) : undefined
      }
    >
      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="flex flex-wrap gap-1 rounded-lg bg-surface p-1 shadow-card ring-1 ring-slate-200">
          {TABS.map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => {
                setTab(option.value);
                setNotice(null);
              }}
              className={`rounded-md px-4 py-2 text-sm font-semibold transition ${
                tab === option.value
                  ? 'bg-brand text-on-brand'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-navy-700'
              }`}
            >
              {option.label}
              {summary ? ` (${summary[option.countKey]})` : ''}
            </button>
          ))}
        </div>

        <div className="ml-auto w-full sm:w-72">
          <TextField
            label="Search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Name or mobile number"
          />
        </div>
      </div>

      <div className="space-y-3">
        {notice && <Alert tone="success">{notice}</Alert>}
        {error && <Alert tone="error">{toApiError(error).message}</Alert>}
      </div>

      <div className="mt-4 overflow-x-auto rounded-2xl border border-line bg-surface shadow-card">
        {members.isPending ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">Loading members…</p>
        ) : items.length === 0 ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">
            No members match this view.
          </p>
        ) : (
          <table className="w-full min-w-3xl text-left text-sm">
            <thead className="border-b border-line bg-navy-50/60 text-xs uppercase tracking-wide text-slate-600">
              <tr>
                <th scope="col" className="px-5 py-3 font-semibold">Name</th>
                <th scope="col" className="px-5 py-3 font-semibold">Department</th>
                <th scope="col" className="px-5 py-3 font-semibold">Status</th>
                <th scope="col" className="px-5 py-3 font-semibold">Last sign-in</th>
                <th scope="col" className="px-5 py-3 text-right font-semibold">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {items.map((member) => (
                <tr key={member.id}>
                  <td className="px-5 py-3">
                    <div className="font-semibold text-slate-900">
                      {member.fullName}
                      {member.role === 'ADMIN' && (
                        <span className="ml-2 rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700">
                          Admin
                        </span>
                      )}
                    </div>
                    <div className="text-slate-500">+91 {member.mobileNumber}</div>
                  </td>
                  <td className="px-5 py-3 text-slate-600">
                    {member.departmentName ?? '—'}
                    {member.designation && (
                      <div className="text-xs text-slate-500">{member.designation}</div>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    <StatusBadge status={member.status} />
                  </td>
                  <td className="px-5 py-3 text-slate-600">{formatDateTime(member.lastLoginAt)}</td>
                  <td className="px-5 py-3 text-right">
                    <MemberAction
                      member={member}
                      isSelf={member.id === user?.id}
                      busy={toggle.isPending && toggle.variables?.id === member.id}
                      onToggle={() => setConfirming(member)}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        open={confirming !== null}
        title={confirming?.status === 'ACTIVE' ? 'Disable this account?' : 'Enable this account?'}
        description={
          confirming?.status === 'ACTIVE'
            ? `${confirming.fullName} will be signed out immediately and will not be able to sign in again until re-enabled.`
            : `${confirming?.fullName} will be able to sign in again.`
        }
        onClose={() => setConfirming(null)}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirming(null)}>
            Cancel
          </Button>
          <Button
            loading={toggle.isPending}
            onClick={() => confirming && toggle.mutate(confirming)}
          >
            {confirming?.status === 'ACTIVE' ? 'Disable account' : 'Enable account'}
          </Button>
        </div>
      </Modal>
    </AppShell>
  );
}

/**
 * A pending or rejected account has no switch: the server refuses to activate one that has not been
 * through the queue, so offering the button would only produce an error.
 */
function MemberAction({
  member,
  isSelf,
  busy,
  onToggle,
}: {
  member: Member;
  isSelf: boolean;
  busy: boolean;
  onToggle: () => void;
}) {
  if (isSelf) {
    return <span className="text-xs text-slate-400">This is you</span>;
  }

  if (member.status === 'PENDING') {
    return (
      <Link to="/admin/requests" className="text-sm font-semibold text-navy-600 hover:underline">
        Review request
      </Link>
    );
  }

  if (member.status === 'REJECTED') {
    return <span className="text-xs text-slate-400">Rejected</span>;
  }

  return (
    <Button variant="secondary" loading={busy} onClick={onToggle}>
      {member.status === 'ACTIVE' ? 'Disable' : 'Enable'}
    </Button>
  );
}
