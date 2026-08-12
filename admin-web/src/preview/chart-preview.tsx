import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { MonthlyActivityChart } from '@/features/admin/reports/MonthlyActivityChart';
import type { MonthlyActivity } from '@/types/api';
import '@/index.css';

/**
 * A throwaway page for looking at the chart without running the backend.
 *
 * <p>Not part of the application: nothing routes here, and the entry is only reachable by opening
 * /chart-preview.html on the dev server. It exists because a chart's geometry — collisions, a
 * column overflowing its band, a tooltip clipped at the edge — is not something a type checker or a
 * unit test can see.
 */
const TYPICAL: MonthlyActivity[] = [
  { month: '2025-09', uploads: 12, downloads: 34 },
  { month: '2025-10', uploads: 28, downloads: 51 },
  { month: '2025-11', uploads: 7, downloads: 19 },
  { month: '2025-12', uploads: 41, downloads: 88 },
  { month: '2026-01', uploads: 33, downloads: 64 },
  { month: '2026-02', uploads: 0, downloads: 0 },
  { month: '2026-03', uploads: 19, downloads: 45 },
  { month: '2026-04', uploads: 56, downloads: 120 },
  { month: '2026-05', uploads: 22, downloads: 39 },
  { month: '2026-06', uploads: 8, downloads: 12 },
  { month: '2026-07', uploads: 37, downloads: 71 },
  { month: '2026-08', uploads: 3, downloads: 5 },
];

/** One month, where the band is at its widest and the labels have the most room to collide. */
const SPARSE: MonthlyActivity[] = [{ month: '2026-08', uploads: 4, downloads: 9 }];

/** Every value zero — the axis must still render sensibly rather than dividing by nothing. */
const EMPTY: MonthlyActivity[] = TYPICAL.map((point) => ({ ...point, uploads: 0, downloads: 0 }));

/** Values far apart, so the short columns are near-invisible and the rounding is under strain. */
const LOPSIDED: MonthlyActivity[] = [
  { month: '2026-01', uploads: 1, downloads: 2400 },
  { month: '2026-02', uploads: 2, downloads: 3 },
  { month: '2026-03', uploads: 1200, downloads: 1 },
];

/** The cases worth looking at: the ordinary one, and the four that break naive chart code. */
const CASES: { title: string; data: MonthlyActivity[] }[] = [
  { title: 'Twelve months, typical', data: TYPICAL },
  { title: 'A single month', data: SPARSE },
  { title: 'All zero', data: EMPTY },
  { title: 'Wildly uneven', data: LOPSIDED },
  { title: 'No data at all', data: [] },
];

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <main className="mx-auto max-w-4xl space-y-6 p-8">
      {CASES.map((example) => (
        <section
          key={example.title}
          className="rounded-xl border border-line bg-surface pt-4 shadow-sm"
        >
          <h2 className="px-5 pb-1 font-semibold text-slate-900">{example.title}</h2>
          <MonthlyActivityChart data={example.data} />
        </section>
      ))}
    </main>
  </StrictMode>,
);
