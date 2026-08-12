import { Link } from 'react-router-dom';
import { auditLabel, auditTone, readableMetadata } from '@/features/admin/audit/labels';
import { formatDateTime } from '@/lib/format';
import { tone } from '@/lib/tones';
import type { AuditEntry } from '@/types/api';

/** One line of the trail. `showActor` is off on a member's own timeline, where it is always them. */
export function AuditEntryRow({
  entry,
  showActor = true,
}: {
  entry: AuditEntry;
  showActor?: boolean;
}) {
  const { chip } = tone(auditTone(entry.action));
  const metadata = readableMetadata(entry.metadata);

  return (
    <li className="flex gap-3 px-5 py-3.5">
      <span
        aria-hidden
        className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${chip}`}
      >
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.8}
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-4 w-4"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        </svg>
      </span>

      <div className="min-w-0 flex-1">
        <p className="text-sm text-slate-900">
          <span className="font-medium">{auditLabel(entry.action)}</span>
          {showActor && (
            <>
              {' · '}
              {entry.actorId ? (
                <Link
                  to={`/admin/members/${entry.actorId}`}
                  className="font-medium text-navy-600 hover:underline"
                >
                  {entry.actorName}
                </Link>
              ) : (
                // No actor: something happened before anyone was authenticated.
                <span className="text-slate-500">unauthenticated</span>
              )}
            </>
          )}
        </p>

        {metadata.length > 0 && (
          <dl className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500">
            {metadata.map(([key, value]) => (
              <div key={key} className="flex gap-1">
                <dt>{key}:</dt>
                <dd className="font-medium text-slate-600">{value}</dd>
              </div>
            ))}
          </dl>
        )}

        <p className="mt-1 text-xs text-slate-400">
          {formatDateTime(entry.at)}
          {entry.ipAddress && ` · ${entry.ipAddress}`}
        </p>
      </div>
    </li>
  );
}
