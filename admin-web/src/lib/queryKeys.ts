import type { QueryClient } from '@tanstack/react-query';

/**
 * Every cached query that shows documents.
 *
 * <p>Deleting, restoring, replacing or starring a document changes what several unrelated screens
 * would display, and each of them used to re-list the keys it could think of. That works until
 * somebody adds a screen — which happened in Phase 4, when favourites, search and the home
 * dashboard all began showing files that a delete elsewhere should have removed.
 *
 * <p>So the list lives here, once. Add a new file-listing query key to this array and every existing
 * mutation starts refreshing it.
 */
const FILE_LIST_KEYS = [
  'folder-files',
  'my-uploads',
  'favorites',
  'search',
  'recent-files',
  'downloads',
  'deletions',
  // Folder and department cards carry file counts, so they go stale for the same reasons.
  'folders',
  'departments',
];

/** Marks every document-bearing list stale, so each refetches when it is next on screen. */
export function invalidateFileLists(queryClient: QueryClient): Promise<void> {
  return queryClient.invalidateQueries({
    predicate: (query) => FILE_LIST_KEYS.includes(query.queryKey[0] as string),
  });
}
