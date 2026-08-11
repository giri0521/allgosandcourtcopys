import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { fetchDepartments } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';

/**
 * All 43 departments, browsable by anyone signed in.
 *
 * <p>There is no "my department" filter and no lock icon on the others: every active user may read
 * and upload everywhere, so showing a subset would misrepresent what the system does.
 */
export function DepartmentsPage() {
  const [query, setQuery] = useState('');

  const departments = useQuery({
    queryKey: ['departments'],
    queryFn: fetchDepartments,
  });

  const visible = (departments.data ?? []).filter((department) =>
    department.name.toLowerCase().includes(query.trim().toLowerCase()),
  );

  return (
    <AppShell
      title="Departments"
      subtitle="Browse documents across every department."
      actions={
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Filter departments"
          aria-label="Filter departments"
          className="w-64 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm outline-none
            transition focus:border-navy-500 focus:ring-2 focus:ring-navy-200"
        />
      }
    >
      {departments.isPending && <p className="text-sm text-slate-500">Loading departments…</p>}

      {departments.isError && (
        <Alert tone="error">{toApiError(departments.error).message}</Alert>
      )}

      {departments.isSuccess && visible.length === 0 && (
        <p className="text-sm text-slate-500">No department matches “{query}”.</p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((department) => (
          <Link
            key={department.id}
            to={`/departments/${department.id}`}
            className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm transition
              hover:border-navy-300 hover:shadow focus:outline-none focus:ring-2 focus:ring-navy-300"
          >
            <h2 className="font-semibold text-navy-800">{department.name}</h2>
            {department.code && (
              <p className="mt-0.5 text-xs uppercase tracking-wide text-slate-400">
                {department.code}
              </p>
            )}
            <p className="mt-3 text-sm text-slate-500">
              {department.folderCount ?? 0} {department.folderCount === 1 ? 'folder' : 'folders'} ·{' '}
              {department.fileCount ?? 0} {department.fileCount === 1 ? 'document' : 'documents'}
            </p>
          </Link>
        ))}
      </div>
    </AppShell>
  );
}
