import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import {
  fetchBreadcrumb,
  fetchFolder,
  fetchFolderFiles,
  fetchSubfolders,
} from '@/features/documents/api';
import { CreateFolderDialog } from '@/features/documents/CreateFolderDialog';
import { DeleteFileDialog } from '@/features/documents/DeleteFileDialog';
import { FileTable } from '@/features/documents/FileTable';
import { FolderGrid } from '@/features/documents/FolderGrid';
import { ReplaceFileDialog } from '@/features/documents/ReplaceFileDialog';
import { UploadDialog } from '@/features/documents/UploadDialog';
import { CategoryBadge } from '@/components/ui/CategoryBadge';
import { toApiError } from '@/lib/errors';
import type { FileItem } from '@/types/api';

/**
 * Inside one folder: its subfolders, its documents, and the two actions available here — upload and
 * delete.
 *
 * <p>Uploading is offered in every department, not only the viewer's own, because that is what the
 * server allows. Deleting is offered per row, and only where the server said the caller may.
 */
export function FolderPage() {
  const { folderId = '' } = useParams();
  const [uploading, setUploading] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<FileItem | null>(null);
  const [replacing, setReplacing] = useState<FileItem | null>(null);

  const folder = useQuery({
    queryKey: ['folder', folderId],
    queryFn: () => fetchFolder(folderId),
    enabled: Boolean(folderId),
  });

  const breadcrumb = useQuery({
    queryKey: ['breadcrumb', folderId],
    queryFn: () => fetchBreadcrumb(folderId),
    enabled: Boolean(folderId),
  });

  const subfolders = useQuery({
    queryKey: ['folders', folder.data?.departmentId, folderId],
    queryFn: () => fetchSubfolders(folderId),
    enabled: Boolean(folderId),
  });

  const files = useQuery({
    queryKey: ['folder-files', folderId],
    queryFn: () => fetchFolderFiles(folderId),
    enabled: Boolean(folderId),
  });

  const trail = breadcrumb.data ?? [];

  return (
    <AppShell
      title={folder.data?.name ?? 'Folder'}
      subtitle={folder.data?.departmentName}
      actions={
        <div className="flex items-center gap-2">
          {folder.data && <CategoryBadge category={folder.data.category} />}
          <Button variant="secondary" onClick={() => setCreating(true)}>
            New folder
          </Button>
          <Button onClick={() => setUploading(true)}>Upload</Button>
        </div>
      }
    >
      <nav aria-label="Breadcrumb" className="mb-4 flex flex-wrap items-center text-sm text-slate-500">
        <Link to="/departments" className="font-medium text-navy-600 hover:underline">
          Departments
        </Link>
        {folder.data && (
          <>
            <span className="mx-2 text-slate-300">/</span>
            <Link
              to={`/departments/${folder.data.departmentId}`}
              className="font-medium text-navy-600 hover:underline"
            >
              {folder.data.departmentName}
            </Link>
          </>
        )}
        {trail.map((entry, index) => (
          <span key={entry.id} className="flex items-center">
            <span className="mx-2 text-slate-300">/</span>
            {index === trail.length - 1 ? (
              <span className="text-slate-700">{entry.name}</span>
            ) : (
              <Link to={`/folders/${entry.id}`} className="font-medium text-navy-600 hover:underline">
                {entry.name}
              </Link>
            )}
          </span>
        ))}
      </nav>

      {folder.isError && <Alert tone="error">{toApiError(folder.error).message}</Alert>}

      <div className="space-y-6">
        {(subfolders.data?.length ?? 0) > 0 && (
          <section>
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
              Folders
            </h2>
            <FolderGrid folders={subfolders.data ?? []} />
          </section>
        )}

        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Documents{files.data ? ` (${files.data.totalItems})` : ''}
          </h2>

          {files.isPending && <SkeletonRows count={4} label="Loading documents" />}
          {files.isError && <Alert tone="error">{toApiError(files.error).message}</Alert>}

          {files.isSuccess && (
            <FileTable
              files={files.data.items}
              emptyMessage="No documents here yet. Use Upload to add the first one."
              onDelete={setDeleting}
              onReplace={setReplacing}
            />
          )}
        </section>
      </div>

      {folder.data && (
        <>
          <UploadDialog
            open={uploading}
            folderId={folderId}
            folderName={folder.data.name}
            onClose={() => setUploading(false)}
          />
          <CreateFolderDialog
            open={creating}
            departmentId={folder.data.departmentId}
            parentFolderId={folderId}
            onClose={() => setCreating(false)}
          />
        </>
      )}

      <DeleteFileDialog file={deleting} onClose={() => setDeleting(null)} />
      <ReplaceFileDialog file={replacing} onClose={() => setReplacing(null)} />
    </AppShell>
  );
}
