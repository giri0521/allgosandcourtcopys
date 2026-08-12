import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextField } from '@/components/ui/Field';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { AuditEntryRow } from '@/features/admin/audit/AuditEntryRow';
import { auditLabel } from '@/features/admin/audit/labels';
import { fetchAuditActions, fetchAuditLogs, fetchMembers } from '@/features/admin/api';
import { toApiError } from '@/lib/errors';

/**
 * The audit trail, filtered.
 *
 * <p>Read-only, and there is no endpoint to make it otherwise: a trail somebody can edit is not
 * evidence. The filters live in the URL so a particular view — one member, one action, one week —
 * can be sent to whoever asked for it.
 */
export function AuditLogPage() {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(0);

  const actorId = params.get('actorId') ?? '';
  const action = params.get('action') ?? '';
  const from = params.get('from') ?? '';
  const to = params.get('to') ?? '';

  const logs = useQuery({
    queryKey: ['audit-logs', actorId, action, from, to, page],
    queryFn: () => fetchAuditLogs({ actorId, action, from, to }, page),
  });

  // Every account, so the filter can name them. The list is small — a hundred or so people.
  const members = useQuery({
    queryKey: ['members', 'all', ''],
    queryFn: () => fetchMembers('all', '', 0),
  });

  const actions = useQuery({ queryKey: ['audit-actions'], queryFn: fetchAuditActions });

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    setParams(next, { replace: true });
    setPage(0);
  };

  const hasFilters = Boolean(actorId || action || from || to);

  return (
    <AppShell
      title="Activity Log"
      subtitle="Every recorded action, in the order it happened. This log cannot be edited."
      actions={
        hasFilters ? (
          <Button
            variant="secondary"
            onClick={() => {
              setParams(new URLSearchParams(), { replace: true });
              setPage(0);
            }}
          >
            Clear filters
          </Button>
        ) : undefined
      }
    >
      <div className="mb-5 grid gap-3 rounded-xl border border-line bg-surface p-4 shadow-sm sm:grid-cols-2 lg:grid-cols-4">
        <SelectField
          label="Member"
          value={actorId}
          onChange={(event) => setFilter('actorId', event.target.value)}
        >
          <option value="">Everyone</option>
          {(members.data?.items ?? []).map((member) => (
            <option key={member.id} value={member.id}>
              {member.fullName}
            </option>
          ))}
        </SelectField>

        <SelectField
          label="Action"
          value={action}
          onChange={(event) => setFilter('action', event.target.value)}
        >
          <option value="">Every action</option>
          {(actions.data ?? []).map((value) => (
            <option key={value} value={value}>
              {auditLabel(value)}
            </option>
          ))}
        </SelectField>

        <TextField
          label="From"
          type="date"
          value={from}
          onChange={(event) => setFilter('from', event.target.value)}
        />
        <TextField
          label="To"
          type="date"
          value={to}
          onChange={(event) => setFilter('to', event.target.value)}
        />
      </div>

      {logs.isError && <Alert tone="error">{toApiError(logs.error).message}</Alert>}

      {logs.isPending && <SkeletonRows count={6} label="Loading the activity log" />}

      {logs.isSuccess && (
        <div className="space-y-4">
          <p className="text-sm text-slate-500" role="status">
            {logs.data.totalItems === 0
              ? 'No entries match these filters.'
              : `${logs.data.totalItems} entr${logs.data.totalItems === 1 ? 'y' : 'ies'}`}
          </p>

          {logs.data.items.length > 0 && (
            <div className="overflow-hidden rounded-xl border border-line bg-surface shadow-sm">
              <ul className="divide-y divide-slate-100">
                {logs.data.items.map((entry) => (
                  <AuditEntryRow key={entry.id} entry={entry} />
                ))}
              </ul>
            </div>
          )}

          {logs.data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>
                Page {logs.data.page + 1} of {logs.data.totalPages}
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
                  disabled={page + 1 >= logs.data.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </AppShell>
  );
}
