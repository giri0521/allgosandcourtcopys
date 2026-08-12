import { useQuery } from '@tanstack/react-query';
import { Link, Navigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { SkeletonRows } from '@/components/ui/Skeleton';
import {
  fetchDepartments,
  fetchFavorites,
  fetchMyUploads,
  fetchRecentFiles,
} from '@/features/documents/api';
import { fetchMemberCounts } from '@/features/admin/api';
import { useAuth } from '@/lib/auth-context';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import { fileTypeTone, tone } from '@/lib/tones';
import type { ToneName } from '@/lib/tones';
import type { FileItem } from '@/types/api';

/**
 * Home: where a signed-in user starts, and the only screen that has to answer "what can I do here?"
 *
 * <p>It uses {@link AppShell} like every other signed-in screen, so the header navigation is
 * present. It did not until Phase 3 shipped, which left someone who signed in on a page with no way
 * to reach anything — worth remembering before adding another standalone layout.
 *
 * <p>Every number on it is also a way in: the tiles are links, because a count with no route is a
 * fact the user can do nothing with.
 */
export function SignedInPage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const departments = useQuery({ queryKey: ['departments'], queryFn: fetchDepartments });
  const myUploads = useQuery({ queryKey: ['my-uploads', 0], queryFn: () => fetchMyUploads(0) });
  const favorites = useQuery({ queryKey: ['favorites', 0], queryFn: () => fetchFavorites(0) });
  const recent = useQuery({ queryKey: ['recent-files'], queryFn: () => fetchRecentFiles(5) });

  // Only admins may call this one, so it is not even attempted for a member — a 403 would send
  // them to the Access Restricted screen from their own home page.
  const counts = useQuery({
    queryKey: ['member-counts'],
    queryFn: fetchMemberCounts,
    enabled: isAdmin,
  });

  if (!user) {
    return <Navigate to="/login" replace />;
  }
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
        <section className="stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
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
          <Tile
            to="/favorites"
            tone="violet"
            label="Favorites"
            value={favorites.data ? String(favorites.data.totalItems) : '—'}
            hint="Starred for quick access"
          />
        </section>

        {/* Admins get the queue in front of them: an unapproved registration blocks a real person
            from working, and it is the one thing here that nobody else can clear. */}
        {isAdmin && (counts.data?.pendingRequests ?? 0) > 0 && (
          <Link
            to="/admin/requests"
            className="animate-rise flex flex-wrap items-center justify-between gap-3 rounded-xl
              border border-amber-300 bg-amber-50 px-5 py-4 transition-all duration-[--duration-base]
              ease-[--ease-settle] hover:-translate-y-0.5 hover:shadow-md"
          >
            <span>
              <span className="block font-semibold text-amber-900">
                {counts.data?.pendingRequests} registration
                {counts.data?.pendingRequests === 1 ? '' : 's'} awaiting your approval
              </span>
              <span className="mt-0.5 block text-sm text-amber-800">
                Nobody can sign in until their request is approved.
              </span>
            </span>
            <span className="text-sm font-semibold text-amber-900">Review now →</span>
          </Link>
        )}

        <RecentlyFiled files={recent.data?.items ?? []} loading={recent.isPending} />

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
              <Link
                to="/downloads"
                className="rounded-lg border border-navy-300 bg-white px-4 py-2.5 text-sm font-semibold text-navy-700 transition hover:bg-navy-50"
              >
                My downloads
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

/**
 * The five most recent documents, across every department.
 *
 * <p>Deliberately a short list rather than a feed: this answers "what has been filed lately", and
 * anyone wanting more has Departments and the search box.
 */
function RecentlyFiled({ files, loading }: { files: FileItem[]; loading: boolean }) {
  if (loading) {
    return <SkeletonRows count={3} label="Loading recent documents" />;
  }

  if (files.length === 0) {
    return null;
  }

  return (
    <section className="overflow-hidden rounded-xl border border-line bg-surface shadow-sm">
      <div className="flex items-center justify-between border-b border-line px-5 py-3">
        <h2 className="font-semibold text-slate-900">Recently filed</h2>
        <Link to="/departments" className="text-sm font-semibold text-navy-600 hover:underline">
          Browse all
        </Link>
      </div>
      <ul className="stagger divide-y divide-slate-100">
        {files.map((file) => (
          <li key={file.id}>
            <Link
              to={`/files/${file.id}`}
              className="flex items-center gap-3 px-5 py-3 outline-none transition-colors
                duration-[--duration-base] hover:bg-navy-50/40 focus-visible:bg-navy-50/40"
            >
              <span
                aria-hidden
                className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg
                  ${fileTypeTone(file.fileType).chip}`}
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
              <span className="min-w-0 flex-1">
                <span className="block truncate font-medium text-slate-900">{file.fileName}</span>
                <span className="block text-xs text-slate-500">
                  {file.departmentName} · {formatFileType(file.fileType)} ·{' '}
                  {formatFileSize(file.sizeBytes)}
                </span>
              </span>
              <span className="hidden text-xs text-slate-400 sm:block">
                {formatDateTime(file.uploadedAt)}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
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
