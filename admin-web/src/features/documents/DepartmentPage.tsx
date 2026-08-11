import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonCards } from '@/components/ui/Skeleton';
import { fetchDepartments, fetchFolders } from '@/features/documents/api';
import { CreateFolderDialog } from '@/features/documents/CreateFolderDialog';
import { FolderGrid } from '@/features/documents/FolderGrid';
import { toApiError } from '@/lib/errors';

/**
 * The folders at the top level of one department.
 *
 * <p>Documents live in folders, never loose in a department, so this screen lists folders only —
 * files appear once you are inside one.
 */
export function DepartmentPage() {
  const { departmentId = '' } = useParams();
  const [creating, setCreating] = useState(false);

  // Served from the cache when arriving from the department list, so the name renders immediately.
  const departments = useQuery({ queryKey: ['departments'], queryFn: fetchDepartments });
  const department = departments.data?.find((entry) => entry.id === departmentId);

  const folders = useQuery({
    queryKey: ['folders', departmentId, null],
    queryFn: () => fetchFolders(departmentId),
    enabled: Boolean(departmentId),
  });

  return (
    <AppShell
      title={department?.name ?? 'Department'}
      subtitle="Choose a folder, or create one."
      actions={<Button onClick={() => setCreating(true)}>New folder</Button>}
    >
      <nav aria-label="Breadcrumb" className="mb-4 text-sm text-slate-500">
        <Link to="/departments" className="font-medium text-navy-600 hover:underline">
          Departments
        </Link>
        <span className="mx-2 text-slate-300">/</span>
        <span className="text-slate-700">{department?.name ?? '…'}</span>
      </nav>

      {folders.isPending && <SkeletonCards count={3} label="Loading folders" />}
      {folders.isError && <Alert tone="error">{toApiError(folders.error).message}</Alert>}

      {folders.isSuccess && folders.data.length === 0 && (
        <div className="animate-rise flex flex-col items-center rounded-xl border border-dashed
          border-slate-300 bg-white px-6 py-12 text-center">
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
            <path d="M3 7a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.6.8l.9 1.2H19a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
          </svg>
          <p className="mt-3 text-sm text-slate-500">
            This department has no folders yet. Create the first one to start filing documents.
          </p>
          <Button className="mt-4" onClick={() => setCreating(true)}>
            New folder
          </Button>
        </div>
      )}

      <FolderGrid folders={folders.data ?? []} />

      <CreateFolderDialog
        open={creating}
        departmentId={departmentId}
        onClose={() => setCreating(false)}
      />
    </AppShell>
  );
}
