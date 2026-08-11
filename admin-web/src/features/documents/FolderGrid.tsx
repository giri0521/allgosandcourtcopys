import { Link } from 'react-router-dom';
import { formatCategory } from '@/lib/format';
import type { Folder } from '@/types/api';

/** The folder cards shown at the top of a department or a folder. */
export function FolderGrid({ folders }: { folders: Folder[] }) {
  if (folders.length === 0) return null;

  return (
    <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {folders.map((folder) => (
        <li key={folder.id}>
          <Link
            to={`/folders/${folder.id}`}
            className="flex h-full flex-col rounded-xl border border-slate-200 bg-white p-4 shadow-sm
              transition hover:border-navy-300 hover:shadow focus:outline-none focus:ring-2 focus:ring-navy-300"
          >
            <span className="font-medium text-navy-800">{folder.name}</span>
            <span className="mt-1 text-xs uppercase tracking-wide text-slate-400">
              {formatCategory(folder.category)}
            </span>
            <span className="mt-3 text-sm text-slate-500">
              {folder.fileCount} {folder.fileCount === 1 ? 'document' : 'documents'}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
