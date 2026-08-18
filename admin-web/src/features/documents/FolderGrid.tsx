import { Link } from 'react-router-dom';
import { CategoryBadge } from '@/components/ui/CategoryBadge';
import { categoryTone } from '@/lib/tones';
import type { Folder } from '@/types/api';

/** The folder cards shown at the top of a department or a folder. */
export function FolderGrid({ folders }: { folders: Folder[] }) {
  if (folders.length === 0) return null;

  return (
    <ul className="stagger grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {folders.map((folder) => (
        <li key={folder.id}>
          <Link
            to={`/folders/${folder.id}`}
            className="group flex h-full flex-col rounded-xl border border-line bg-surface p-4 shadow-card
              outline-none transition-all duration-[--duration-base] ease-[--ease-settle]
              hover:-translate-y-0.5 hover:border-navy-300 hover:shadow-lifted
              focus-visible:ring-2 focus-visible:ring-navy-300 active:translate-y-0"
          >
            <span className="flex items-center gap-2">
              {/* Tinted by category, and the lid lifts a touch on hover. Small, but it is what makes
                  a folder feel like a folder rather than a rectangle. */}
              <svg
                aria-hidden
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={1.8}
                strokeLinecap="round"
                strokeLinejoin="round"
                className={`h-5 w-5 shrink-0 transition-transform duration-[--duration-base]
                  ease-[--ease-settle] group-hover:-translate-y-0.5 ${categoryTone(folder.category).text}`}
              >
                <path d="M3 7a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.6.8l.9 1.2H19a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z" />
              </svg>
              <span className="min-w-0 truncate font-medium text-navy-800">{folder.name}</span>
            </span>
            <span className="mt-2 pl-7">
              <CategoryBadge category={folder.category} />
            </span>
            <span className="mt-3 pl-7 text-sm text-slate-500">
              {folder.fileCount} {folder.fileCount === 1 ? 'document' : 'documents'}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
