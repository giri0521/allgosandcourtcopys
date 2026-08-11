import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { fetchMyUploads } from '@/features/documents/api';
import { DeleteFileDialog } from '@/features/documents/DeleteFileDialog';
import { FileTable } from '@/features/documents/FileTable';
import { ReplaceFileDialog } from '@/features/documents/ReplaceFileDialog';
import { toApiError } from '@/lib/errors';
import type { FileItem } from '@/types/api';

/**
 * Everything this user has uploaded, across every department.
 *
 * <p>Scoped by the server to the caller, so it is the one place where every row is deletable — with
 * a reason.
 */
export function MyUploadsPage() {
  const [page, setPage] = useState(0);
  const [deleting, setDeleting] = useState<FileItem | null>(null);
  const [replacing, setReplacing] = useState<FileItem | null>(null);

  const uploads = useQuery({
    queryKey: ['my-uploads', page],
    queryFn: () => fetchMyUploads(page),
  });

  return (
    <AppShell title="My Uploads" subtitle="Documents you have added to the system.">
      {uploads.isPending && <SkeletonRows count={4} label="Loading your uploads" />}
      {uploads.isError && <Alert tone="error">{toApiError(uploads.error).message}</Alert>}

      {uploads.isSuccess && (
        <div className="space-y-4">
          <FileTable
            files={uploads.data.items}
            showLocation
            emptyMessage="You have not uploaded anything yet."
            onDelete={setDeleting}
            onReplace={setReplacing}
          />

          {uploads.data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>
                Page {uploads.data.page + 1} of {uploads.data.totalPages}
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
                  disabled={page + 1 >= uploads.data.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <DeleteFileDialog file={deleting} onClose={() => setDeleting(null)} />
      <ReplaceFileDialog file={replacing} onClose={() => setReplacing(null)} />
    </AppShell>
  );
}
