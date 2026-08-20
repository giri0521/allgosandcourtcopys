import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { fetchDownloadHistory } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import { fileTypeTone } from '@/lib/tones';
import type { DownloadRecord } from '@/types/api';

/**
 * Every document this user has taken a copy of.
 *
 * <p>Rows for documents that were later deleted stay, marked unavailable: a history that quietly
 * dropped entries when something was removed would not be a history. Previewing a document does not
 * appear here — looking at something on screen is not the same act as taking a copy of it.
 */
export function DownloadsPage() {
  const [page, setPage] = useState(0);

  const downloads = useQuery({
    queryKey: ['downloads', page],
    queryFn: () => fetchDownloadHistory(page),
  });

  return (
    <AppShell
      title="Downloads"
      subtitle="Documents you have downloaded, most recent first."
    >
      {downloads.isPending && <SkeletonRows count={4} label="Loading your download history" />}
      {downloads.isError && <Alert tone="error">{toApiError(downloads.error).message}</Alert>}

      {downloads.isSuccess && (
        <div className="space-y-4">
          {downloads.data.items.length === 0 ? (
            <EmptyState />
          ) : (
            <div className="overflow-x-auto rounded-xl border border-line bg-surface shadow-card">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead className="border-b border-line bg-navy-50/60 text-xs uppercase tracking-wide text-slate-600">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Document</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Location</th>
                    <th scope="col" className="px-5 py-3 font-semibold">Downloaded</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Action</th>
                  </tr>
                </thead>
                <tbody className="stagger divide-y divide-slate-100">
                  {downloads.data.items.map((record) => (
                    <Row key={record.id} record={record} />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {downloads.data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>
                Page {downloads.data.page + 1} of {downloads.data.totalPages}
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
                  disabled={page + 1 >= downloads.data.totalPages}
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

function Row({ record }: { record: DownloadRecord }) {
  return (
    <tr className="transition-colors duration-[--duration-base] hover:bg-navy-50/70">
      <td className="px-5 py-3">
        <div className="flex items-center gap-3">
          <span
            aria-hidden
            className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg
              ${fileTypeTone(record.fileType).chip} ${record.available ? '' : 'opacity-50'}`}
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.8}
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-4.5 w-4.5"
            >
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
              <path d="M14 2v6h6" />
            </svg>
          </span>
          <div className="min-w-0">
            <p className={`truncate font-medium ${record.available ? 'text-slate-900' : 'text-slate-500'}`}>
              {record.fileName}
            </p>
            <p className="text-xs text-slate-500">
              {formatFileType(record.fileType)} · {formatFileSize(record.sizeBytes)}
              {!record.available && (
                <span className="ml-1.5 rounded-full bg-slate-100 px-1.5 py-0.5 font-medium text-slate-600">
                  Deleted since
                </span>
              )}
            </p>
          </div>
        </div>
      </td>
      <td className="px-5 py-3 text-slate-600">
        <p>{record.departmentName}</p>
        <p className="text-xs text-slate-400">{record.folderName}</p>
      </td>
      <td className="px-5 py-3 text-slate-600">{formatDateTime(record.downloadedAt)}</td>
      <td className="px-5 py-3 text-right">
        {record.available ? (
          <Link
            to={`/files/${record.fileId}`}
            className="text-sm font-semibold text-navy-600 hover:underline"
          >
            Open
          </Link>
        ) : (
          <span className="text-xs text-slate-400">No longer available</span>
        )}
      </td>
    </tr>
  );
}

function EmptyState() {
  return (
    <div
      className="animate-rise flex flex-col items-center rounded-xl border border-dashed
        border-line-strong bg-surface px-6 py-12 text-center"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-10 w-10 text-slate-300"
      >
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
        <path d="M7 10l5 5 5-5" />
        <path d="M12 15V3" />
      </svg>
      <p className="mt-3 text-sm text-slate-500">You have not downloaded anything yet.</p>
    </div>
  );
}
