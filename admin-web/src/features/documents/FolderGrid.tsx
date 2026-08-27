import { Link } from 'react-router-dom';
import { CategoryBadge } from '@/components/ui/CategoryBadge';
import { useAuth } from '@/lib/auth-context';
import { categoryTone } from '@/lib/tones';
import type { Folder } from '@/types/api';

/** Seeded for every department; never offered for deletion — see FolderService.GENERAL_FOLDER_NAME. */
function isGeneralFolder(folder: Folder): boolean {
  return folder.parentFolderId === null && folder.name.toLowerCase() === 'general';
}

/**
 * The folder cards shown at the top of a department or a folder.
 *
 * <p>`onDelete` is optional so a caller with nowhere to put the confirmation dialog — there is none
 * today — can simply omit it; when it is given, an administrator sees a Delete button on every card
 * except the department's General folder. The server has the final say on whether a folder is
 * actually empty enough to remove.
 */
export function FolderGrid({
  folders,
  onDelete,
}: {
  folders: Folder[];
  onDelete?: (folder: Folder) => void;
}) {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  if (folders.length === 0) return null;

  return (
    <ul className="stagger grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {folders.map((folder) => (
        <li key={folder.id} className="relative">
          <Link
            to={`/folders/${folder.id}`}
            className="group flex h-full flex-col rounded-xl border border-line bg-surface p-4 shadow-card
              outline-none transition-all duration-[--duration-base] ease-[--ease-settle]
              hover:-translate-y-0.5 hover:border-navy-300 hover:shadow-lifted
              focus-visible:ring-2 focus-visible:ring-navy-300 active:translate-y-0"
          >
            <span className="flex items-center gap-2 pr-16">
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

          {onDelete && isAdmin && !isGeneralFolder(folder) && (
            <button
              type="button"
              onClick={(event) => {
                // The card underneath is a Link; without this the click would also navigate.
                event.preventDefault();
                event.stopPropagation();
                onDelete(folder);
              }}
              aria-label={`Delete ${folder.name}`}
              className="absolute right-3 top-3 rounded-md px-2 py-1 text-[0.8125rem] font-semibold
                text-red-600 outline-none transition-colors duration-[--duration-quick]
                ease-[--ease-settle] hover:bg-red-50 focus-visible:ring-2 focus-visible:ring-navy-300"
            >
              Delete
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}
