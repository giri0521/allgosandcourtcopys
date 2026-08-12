import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { fetchFavorites } from '@/features/documents/api';
import { DeleteFileDialog } from '@/features/documents/DeleteFileDialog';
import { FileTable } from '@/features/documents/FileTable';
import { ReplaceFileDialog } from '@/features/documents/ReplaceFileDialog';
import { toApiError } from '@/lib/errors';
import type { FileItem } from '@/types/api';

/**
 * The documents this user has starred.
 *
 * <p>Private: a star is never visible to anyone else, and no screen shows whose favourites a
 * document is on.
 *
 * <p>A deleted document leaves this list rather than appearing as unavailable — a shortcut to
 * something that no longer exists is only clutter. The star itself is kept, so an admin restore
 * brings it back.
 */
export function FavoritesPage() {
  const [page, setPage] = useState(0);
  const [deleting, setDeleting] = useState<FileItem | null>(null);
  const [replacing, setReplacing] = useState<FileItem | null>(null);

  const favorites = useQuery({
    queryKey: ['favorites', page],
    queryFn: () => fetchFavorites(page),
  });

  return (
    <AppShell
      title="Favorites"
      subtitle="Documents you have starred, from any department."
    >
      {favorites.isPending && <SkeletonRows count={4} label="Loading your favorites" />}
      {favorites.isError && <Alert tone="error">{toApiError(favorites.error).message}</Alert>}

      {favorites.isSuccess && (
        <div className="space-y-4">
          <FileTable
            files={favorites.data.items}
            showLocation
            emptyMessage="Nothing starred yet. Use the star beside a document to keep it here."
            onDelete={setDeleting}
            onReplace={setReplacing}
          />

          {favorites.data.totalPages > 1 && (
            <div className="flex items-center justify-between text-sm text-slate-500">
              <span>
                Page {favorites.data.page + 1} of {favorites.data.totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  Previous
                </Button>
                <Button
                  variant="secondary"
                  disabled={page + 1 >= favorites.data.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <DeleteFileDialog file={deleting} onClose={() => setDeleting(null)} />
      <ReplaceFileDialog file={replacing} onClose={() => setReplacing(null)} />
    </AppShell>
  );
}
