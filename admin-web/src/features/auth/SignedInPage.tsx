import { useQuery } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { fetchDepartments, fetchMyUploads } from '@/features/documents/api';
import { useAuth } from '@/lib/auth-context';
import { tone } from '@/lib/tones';
import type { ToneName } from '@/lib/tones';

/**
 * Home: where a signed-in user starts, and the only screen that has to answer "what can I do here?"
 *
 * <p>It uses {@link AppShell} like every other signed-in screen, so the header navigation is
 * present. It did not until Phase 3 shipped, which left someone who signed in on a page with no way
 * to reach anything — worth remembering before adding another standalone layout.
 *
 * <p>The full dashboard from the plan — recent activity, favourites, notifications — is Phase 4.
 * What is here is the set of places that actually exist.
 */
export function SignedInPage() {
  const { user } = useAuth();

  const departments = useQuery({ queryKey: ['departments'], queryFn: fetchDepartments });
  const myUploads = useQuery({ queryKey: ['my-uploads', 0], queryFn: () => fetchMyUploads(0) });

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  const isAdmin = user.role === 'ADMIN';
  const documentCount = (departments.data ?? []).reduce(
    (total, department) => total + (department.fileCount ?? 0),
    0,
  );

  return (
    <AppShell
      title={`Welcome, ${user.fullName}`}
      subtitle={`${isAdmin ? 'Administrator' : 'Member'}${
        user.departmentName ? ` · ${user.departmentName}` : ''
      }`}
    >
      <div className="space-y-6">
        <section className="stagger grid gap-4 sm:grid-cols-3">
          <Tile
            to="/departments"
            tone="navy"
            label="Departments"
            value={departments.data ? String(departments.data.length) : '—'}
            hint="Browse and upload anywhere"
          />
          <Tile
            to="/departments"
            tone="teal"
            label="Documents"
            value={departments.data ? String(documentCount) : '—'}
            hint="Across every department"
          />
          <Tile
            to="/my-uploads"
            tone="gold"
            label="My uploads"
            value={myUploads.data ? String(myUploads.data.totalItems) : '—'}
            hint="Yours to replace or delete"
          />
        </section>

        {documentCount === 0 && departments.isSuccess && (
          <section className="rounded-xl border border-line bg-surface p-6 shadow-sm">
            <h2 className="font-semibold text-slate-900">Nothing has been filed yet</h2>
            <p className="mt-1 text-sm text-slate-600">
              Open a department, create a folder, then upload into it. You may file documents into
              any department, not only your own.
            </p>
            <Link
              to="/departments"
              className="mt-4 inline-flex rounded-lg bg-navy-600 px-4 py-2.5 text-sm font-semibold
                text-white transition hover:bg-navy-700"
            >
              Browse departments
            </Link>
          </section>
        )}

        {isAdmin && (
          <section className="rounded-xl border border-line bg-surface p-6 shadow-sm">
            <h2 className="font-semibold text-slate-900">Administration</h2>
            <p className="mt-1 text-sm text-slate-600">
              Nobody can sign in until their registration is approved, so the queue is the first
              place to look. Deleted documents are recoverable from the log.
            </p>
            <div className="mt-4 flex flex-wrap gap-2">
              <Link
                to="/admin/requests"
                className="rounded-lg bg-navy-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-navy-700"
              >
                Registration Requests
              </Link>
              <Link
                to="/admin/members"
                className="rounded-lg border border-navy-300 bg-white px-4 py-2.5 text-sm font-semibold text-navy-700 transition hover:bg-navy-50"
              >
                Members
              </Link>
              <Link
                to="/admin/deletions"
                className="rounded-lg border border-navy-300 bg-white px-4 py-2.5 text-sm font-semibold text-navy-700 transition hover:bg-navy-50"
              >
                Deleted documents
              </Link>
            </div>
          </section>
        )}

        <section className="rounded-xl border border-line bg-surface p-6 shadow-sm">
          <h2 className="font-semibold text-slate-900">Your account</h2>
          <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
            <Row label="Mobile" value={`+91 ${user.mobileNumber}`} />
            <Row label="Designation" value={user.designation ?? '—'} />
            <Row label="Account status" value={user.status} />
            <Row label="Email" value={user.email ?? '—'} />
          </dl>
        </section>
      </div>
    </AppShell>
  );
}

/** A count that is also the way in — the numbers on this screen are all navigation. */
function Tile({
  to,
  tone: toneName,
  label,
  value,
  hint,
}: {
  to: string;
  tone: ToneName;
  label: string;
  value: string;
  hint: string;
}) {
  return (
    <Link
      to={to}
      className="group relative overflow-hidden rounded-xl border border-line bg-surface p-5 shadow-sm
        outline-none transition-all duration-[--duration-base] ease-[--ease-settle]
        hover:-translate-y-0.5 hover:border-navy-300 hover:shadow-md
        focus-visible:ring-2 focus-visible:ring-navy-300 active:translate-y-0"
    >
      {/* A colour bar across the top, growing on hover. Enough to tell the three tiles apart at a
          glance without turning the page into a paintbox. */}
      <span
        aria-hidden
        className={`absolute inset-x-0 top-0 h-1 transition-all duration-[--duration-base]
          ease-[--ease-settle] group-hover:h-1.5 ${tone(toneName).edge}`}
      />
      <p className="text-xs uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-navy-800 transition-colors
        duration-[--duration-base] group-hover:text-navy-600">
        {value}
      </p>
      <p className="mt-1 text-sm text-slate-500">{hint}</p>
    </Link>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-slate-500">{label}</dt>
      <dd className="mt-0.5 font-medium text-slate-900">{value}</dd>
    </div>
  );
}
