import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextAreaField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { StatusBadge } from '@/components/ui/StatusBadge';
import {
  approveRequest,
  fetchRegistrationRequests,
  rejectRequest,
  type RequestFilter,
} from '@/features/admin/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import type { RegistrationRequest } from '@/types/api';

const TABS: { value: RequestFilter; label: string }[] = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'APPROVED', label: 'Approved' },
  { value: 'REJECTED', label: 'Rejected' },
  { value: 'all', label: 'All' },
];

/**
 * The admin approval queue — the one screen that turns a registration into a usable account.
 *
 * <p>Nothing here decides access. Approving calls the endpoint and then invalidates the list, so
 * what is shown is what the server did, not what the click assumed. That matters when two admins
 * work the queue at once: the second click gets a 409 and the refreshed list explains why.
 */
export function RegistrationRequestsPage() {
  const queryClient = useQueryClient();

  const [tab, setTab] = useState<RequestFilter>('PENDING');
  const [selected, setSelected] = useState<RegistrationRequest | null>(null);
  const [rejecting, setRejecting] = useState<RegistrationRequest | null>(null);
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const requests = useQuery({
    queryKey: ['registration-requests', tab],
    queryFn: () => fetchRegistrationRequests(tab),
  });

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ['registration-requests'] });

  const approve = useMutation({
    mutationFn: (request: RegistrationRequest) => approveRequest(request.id),
    onSuccess: async (_result, request) => {
      setNotice(`${request.fullName} can now sign in.`);
      setSelected(null);
      await refresh();
    },
    // Even a rejected decision refreshes the list: the usual cause is another admin getting there
    // first, and the queue should show that rather than a stale row.
    onError: () => void refresh(),
  });

  const reject = useMutation({
    mutationFn: (request: RegistrationRequest) => rejectRequest(request.id, reason.trim()),
    onSuccess: async (_result, request) => {
      setNotice(`${request.fullName}'s registration was rejected.`);
      setRejecting(null);
      setSelected(null);
      setReason('');
      await refresh();
    },
  });

  const error = requests.error ?? approve.error ?? reject.error;
  const items = requests.data?.items ?? [];

  return (
    <AppShell
      title="Registration Requests"
      subtitle="Approval is what grants access. Nobody can sign in until a request here is approved."
    >
      <div className="mb-5 inline-flex flex-wrap gap-1 rounded-lg bg-surface-sunken p-1 ring-1 ring-line">
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
                ? 'bg-brand text-on-brand shadow-card'
                : 'text-slate-600 hover:bg-navy-50/70 hover:text-navy-700'
            }`}
          >
            {option.label}
            {tab === option.value && requests.data ? ` (${requests.data.totalItems})` : ''}
          </button>
        ))}
      </div>

      <div className="space-y-3">
        {notice && <Alert tone="success">{notice}</Alert>}
        {error && <Alert tone="error">{toApiError(error).message}</Alert>}
      </div>

      <div className="mt-4 overflow-hidden rounded-2xl border border-line bg-surface shadow-card">
        {requests.isPending ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">Loading requests…</p>
        ) : items.length === 0 ? (
          <p className="px-5 py-10 text-center text-sm text-slate-500">
            {tab === 'PENDING' ? 'No requests are waiting for review.' : 'Nothing to show here.'}
          </p>
        ) : (
          <ul className="divide-y divide-slate-200">
            {items.map((request) => (
              <li key={request.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
                <div className="min-w-56 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-slate-900">{request.fullName}</span>
                    <StatusBadge status={request.status} />
                  </div>
                  <p className="mt-0.5 text-sm text-slate-500">
                    +91 {request.mobileNumber}
                    {request.departmentName ? ` · ${request.departmentName}` : ''}
                    {request.designation ? ` · ${request.designation}` : ''}
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <Button variant="ghost" onClick={() => setSelected(request)}>
                    View
                  </Button>
                  {request.status === 'PENDING' && (
                    <>
                      <Button
                        variant="secondary"
                        onClick={() => {
                          setRejecting(request);
                          setReason('');
                        }}
                      >
                        Reject
                      </Button>
                      <Button
                        loading={approve.isPending && approve.variables?.id === request.id}
                        onClick={() => approve.mutate(request)}
                      >
                        Approve
                      </Button>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Detail view */}
      <Modal
        open={selected !== null}
        title={selected?.fullName ?? ''}
        description="Registration details"
        onClose={() => setSelected(null)}
      >
        {selected && (
          <>
            <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              <Detail label="Mobile" value={`+91 ${selected.mobileNumber}`} />
              <Detail label="Department" value={selected.departmentName ?? '—'} />
              <Detail label="Designation" value={selected.designation ?? '—'} />
              <Detail label="Email" value={selected.email ?? '—'} />
              <Detail
                label="Requested role"
                value={selected.requestedRole === 'ADMIN' ? 'Admin' : 'Member'}
              />
              <Detail label="Submitted" value={formatDateTime(selected.submittedAt)} />
              {selected.reviewedByName && (
                <Detail label="Reviewed by" value={selected.reviewedByName} />
              )}
              {selected.reviewedAt && (
                <Detail label="Reviewed" value={formatDateTime(selected.reviewedAt)} />
              )}
            </dl>

            {selected.reviewNote && <Alert tone="error">Reason given: {selected.reviewNote}</Alert>}

            <div className="flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setSelected(null)}>
                Close
              </Button>
              {selected.status === 'PENDING' && (
                <Button loading={approve.isPending} onClick={() => approve.mutate(selected)}>
                  Approve
                </Button>
              )}
            </div>
          </>
        )}
      </Modal>

      {/* Rejection needs a reason: the applicant is told what it was. */}
      <Modal
        open={rejecting !== null}
        title="Reject this registration"
        description={
          rejecting
            ? `${rejecting.fullName} will be told why, and will need to register again.`
            : undefined
        }
        onClose={() => setRejecting(null)}
      >
        <TextAreaField
          label="Reason"
          rows={3}
          value={reason}
          onChange={(event) => setReason(event.target.value.slice(0, 500))}
          placeholder="e.g. Not a member of this office"
          hint={`${reason.length}/500`}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setRejecting(null)}>
            Cancel
          </Button>
          <Button
            loading={reject.isPending}
            disabled={reason.trim().length === 0}
            onClick={() => rejecting && reject.mutate(rejecting)}
          >
            Reject registration
          </Button>
        </div>
      </Modal>
    </AppShell>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  );
}
