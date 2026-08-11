import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { fetchDeletions, restoreFile } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';

type Tab = 'deleted' | 'all';

const TABS: { value: Tab; label: string }[] = [
  { value: 'deleted', label: 'Still deleted' },
  { value: 'all', label: 'All deletions' },
];

/**
 * The deletions log: what was removed, by whom, and the reason they gave.
 *
 * <p>This is the admin's side of rule 4. The same reason arrives as a notification the moment a file
 * is deleted; this screen is the durable record of it, and the only place a file can be put back.
 */
export function DeletionsPage() {
  const [tab, setTab] = useState<Tab>('deleted');
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const deletions = useQuery({
    queryKey: ['deletions', tab, page],
    queryFn: () => fetchDeletions(tab, page),
  });

  const restore = useMutation({
    mutationFn: (fileId: string) => restoreFile(fileId),
    onSuccess: () => {
      // The row stays in the log with its restore stamped, so the list is re-read rather than
      // patched — it moves between tabs on its own.
      void queryClient.invalidateQueries({ queryKey: ['deletions'] });
      void queryClient.invalidateQueries({ queryKey: ['folder-files'] });
      void queryClient.invalidateQueries({ queryKey: ['folders'] });
    },
  });

  const changeTab = (value: Tab) => {
    setTab(value);
    setPage(0);
  };

  return (
    <AppShell
      title="Deleted documents"
      subtitle="Every deletion, with the reason given. Deletes are reversible."
    >
      <div className="mb-4 flex gap-1" role="tablist" aria-label="Deletion filter">
        {TABS.map((entry) => (
          <button
            key={entry.value}
            type="button"
            role="tab"
            aria-selected={tab === entry.value}
            onClick={() => changeTab(entry.value)}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition ${
              tab === entry.value
                ? 'bg-navy-50 text-navy-700'
                : 'text-slate-600 hover:text-navy-700'
            }`}
          >
            {entry.label}
          </button>
        ))}
      </div>

      {deletions.isPending && <p className="text-sm text-slate-500">Loading…</p>}
      {deletions.isError && <Alert tone="error">{toApiError(deletions.error).message}</Alert>}
      {restore.isError && <Alert tone="error">{toApiError(restore.error).message}</Alert>}

      {deletions.isSuccess && deletions.data.items.length === 0 && (
        <p className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
          {tab === 'deleted' ? 'Nothing is currently deleted.' : 'No documents have been deleted.'}
        </p>
      )}

      {deletions.isSuccess && deletions.data.items.length > 0 && (
        <div className="space-y-4">
          <ul className="space-y-3">
            {deletions.data.items.map((entry) => (
              <li
                key={entry.id}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h2 className="font-semibold text-slate-900">{entry.fileName}</h2>
                    <p className="mt-0.5 text-sm text-slate-500">
                      {entry.departmentName} ·{' '}
                      <Link
                        to={`/folders/${entry.folderId}`}
                        className="text-navy-600 hover:underline"
                      >
                        {entry.folderName}
                      </Link>
                    </p>
                  </div>

                  {entry.restorable ? (
                    <Button
                      variant="secondary"
                      loading={restore.isPending && restore.variables === entry.fileId}
                      onClick={() => restore.mutate(entry.fileId)}
                    >
                      Restore
                    </Button>
                  ) : (
                    <span className="rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">
                      Restored
                    </span>
                  )}
                </div>

                {/* The reason is the point of the record, so it is quoted rather than summarised. */}
                <blockquote className="mt-3 border-l-2 border-slate-200 pl-3 text-sm text-slate-700">
                  {entry.reason}
                </blockquote>

                <p className="mt-3 text-xs text-slate-500">
                  Deleted by {entry.deletedByName} on {formatDateTime(entry.deletedAt)}
                  {entry.restoredAt &&
                    ` · Restored by ${entry.restoredByName} on ${formatDateTime(entry.restoredAt)}`}
                </p>
              </li>
            ))}
          </ul>

          {deletions.data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>
                Page {deletions.data.page + 1} of {deletions.data.totalPages}
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
                  disabled={page + 1 >= deletions.data.totalPages}
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
