import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { SkeletonCards } from '@/components/ui/Skeleton';
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
            transition-all duration-[--duration-base] ease-[--ease-settle]
            focus:w-72 focus:border-navy-500 focus:ring-2 focus:ring-navy-200"
        />
      }
    >
      {departments.isPending && <SkeletonCards count={6} label="Loading departments" />}

      {departments.isError && (
        <Alert tone="error">{toApiError(departments.error).message}</Alert>
      )}

      {departments.isSuccess && visible.length === 0 && (
        <p className="animate-fade rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">
          No department matches “{query}”.
        </p>
      )}

      {/* Keyed on the query so filtering re-runs the stagger — the list visibly re-forms rather
          than silently losing rows. */}
      <div key={query} className="stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {visible.map((department) => (
          <Link
            key={department.id}
            to={`/departments/${department.id}`}
            className="group rounded-xl border border-slate-200 bg-white p-5 shadow-sm outline-none
              transition-all duration-[--duration-base] ease-[--ease-settle]
              hover:-translate-y-0.5 hover:border-navy-300 hover:shadow-md
              focus-visible:ring-2 focus-visible:ring-navy-300 active:translate-y-0"
          >
            <h2 className="font-semibold text-navy-800 transition-colors duration-[--duration-base] group-hover:text-navy-600">
              {department.name}
            </h2>
            {department.code && (
              <p className="mt-0.5 text-xs uppercase tracking-wide text-slate-400">
                {department.code}
              </p>
            )}
            <p className="mt-3 flex items-center gap-1.5 text-sm text-slate-500">
              <span>
                {department.folderCount ?? 0}{' '}
                {department.folderCount === 1 ? 'folder' : 'folders'}
              </span>
              <span className="text-slate-300">·</span>
              <span>
                {department.fileCount ?? 0}{' '}
                {department.fileCount === 1 ? 'document' : 'documents'}
              </span>
              {/* Slides in on hover; a quiet cue that the whole card is the target. */}
              <span
                aria-hidden
                className="ml-auto text-navy-500 opacity-0 transition-all duration-[--duration-base]
                  ease-[--ease-settle] group-hover:translate-x-0.5 group-hover:opacity-100"
              >
                →
              </span>
            </p>
          </Link>
        ))}
      </div>
    </AppShell>
  );
}
