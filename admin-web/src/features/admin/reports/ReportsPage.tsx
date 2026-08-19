import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/AppShell';
import { DepartmentAvatar } from '@/components/ui/DepartmentAvatar';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { SkeletonCards, SkeletonRows } from '@/components/ui/Skeleton';
import { MonthlyActivityChart } from '@/features/admin/reports/MonthlyActivityChart';
import { downloadReportCsv, fetchReport, type ReportType } from '@/features/admin/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';


/**
 * How much the system is being used, and by whom.
 *
 * <p>The period is a plain date range rather than a set of presets: the office's questions are
 * things like "the last quarter" and "since the new circulars went up", which no preset list gets
 * right. Both bounds are inclusive, and the server widens the end to cover the whole final day.
 */
export function ReportsPage() {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [applied, setApplied] = useState<{ from?: string; to?: string }>({});

  const report = useQuery({
    queryKey: ['report', applied.from, applied.to],
    queryFn: () => fetchReport(applied),
  });

  const exportCsv = useMutation({
    mutationFn: (type: ReportType) => downloadReportCsv(type, applied),
  });

  const apply = () => setApplied({ from: from || undefined, to: to || undefined });
  const clear = () => {
    setFrom('');
    setTo('');
    setApplied({});
  };

  return (
    <AppShell
      title="Reports"
      subtitle="Uploads, downloads and holdings across the office."
    >
      <form
        className="mb-5 flex flex-wrap items-end gap-3 rounded-xl border border-line bg-surface p-4 shadow-card"
        onSubmit={(event) => {
          event.preventDefault();
          apply();
        }}
      >
        <div className="w-40">
          <TextField
            label="From"
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </div>
        <div className="w-40">
          <TextField
            label="To"
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </div>

        <Button type="submit">Apply</Button>
        {(applied.from || applied.to) && (
          <Button type="button" variant="secondary" onClick={clear}>
            Last 12 months
          </Button>
        )}

        <p className="ml-auto self-center text-sm text-slate-500">
          {report.data
            ? `${formatDateTime(report.data.from)} — ${formatDateTime(report.data.to)}`
            : 'Defaults to the last twelve months'}
        </p>
      </form>

      {report.isError && <Alert tone="error">{toApiError(report.error).message}</Alert>}
      {exportCsv.isError && <Alert tone="error">{toApiError(exportCsv.error).message}</Alert>}

      {report.isPending && (
        <div className="space-y-6">
          <SkeletonCards count={3} label="Loading the report" />
          <SkeletonRows count={5} label="Loading department activity" />
        </div>
      )}

      {report.isSuccess && (
        <div className="space-y-6">
          <section className="overflow-hidden rounded-xl border border-line bg-surface pt-4 shadow-card">
            <div className="flex flex-wrap items-center justify-between gap-2 px-5 pb-1">
              <h2 className="font-semibold text-slate-900">Activity by month</h2>
              <Button
                variant="secondary"
                loading={exportCsv.isPending && exportCsv.variables === 'monthly'}
                onClick={() => exportCsv.mutate('monthly')}
              >
                Export CSV
              </Button>
            </div>
            <MonthlyActivityChart data={report.data.monthly} />
          </section>

          <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-card">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-5 py-3">
              <div>
                <h2 className="font-semibold text-slate-900">By department</h2>
                <p className="mt-0.5 text-sm text-slate-500">
                  Busiest first. Every department is listed, including those with nothing filed.
                </p>
              </div>
              <Button
                variant="secondary"
                loading={exportCsv.isPending && exportCsv.variables === 'departments'}
                onClick={() => exportCsv.mutate('departments')}
              >
                Export CSV
              </Button>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full min-w-[560px] text-left text-sm">
                <thead className="border-b border-line bg-surface-sunken text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-semibold">Department</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Held</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Uploads</th>
                    <th scope="col" className="px-5 py-3 text-right font-semibold">Downloads</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {report.data.departments.map((row) => (
                    <tr key={row.departmentId} className="hover:bg-navy-50/40">
                      <td className="px-5 py-2.5">
                        <div className="flex items-center gap-2.5">
                          <DepartmentAvatar name={row.departmentName} size="sm" />
                          <span className="text-slate-800">{row.departmentName}</span>
                        </div>
                      </td>
                      <td className="px-5 py-2.5 text-right tabular-nums text-slate-600">
                        {row.documents}
                      </td>
                      <td className="px-5 py-2.5 text-right tabular-nums font-medium text-slate-900">
                        {row.uploads}
                      </td>
                      <td className="px-5 py-2.5 text-right tabular-nums text-slate-600">
                        {row.downloads}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-card">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-5 py-3">
              <h2 className="font-semibold text-slate-900">Most active members</h2>
              <Button
                variant="secondary"
                loading={exportCsv.isPending && exportCsv.variables === 'uploaders'}
                onClick={() => exportCsv.mutate('uploaders')}
              >
                Export CSV
              </Button>
            </div>

            {report.data.topUploaders.length === 0 ? (
              <p className="px-5 py-10 text-center text-sm text-slate-500">
                Nothing was uploaded in this period.
              </p>
            ) : (
              <ol className="divide-y divide-slate-100">
                {report.data.topUploaders.map((uploader, index) => (
                  <li key={uploader.userId} className="flex items-center gap-3 px-5 py-3">
                    <span className="w-5 text-sm tabular-nums text-slate-400">{index + 1}</span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-medium text-slate-900">{uploader.fullName}</p>
                      <p className="text-xs text-slate-500">{uploader.departmentName ?? '—'}</p>
                    </div>
                    <span className="text-sm tabular-nums font-semibold text-navy-800">
                      {uploader.uploads}
                    </span>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      )}
    </AppShell>
  );
}
