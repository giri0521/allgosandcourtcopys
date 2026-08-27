import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { purgeFile } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { invalidateFileLists } from '@/lib/queryKeys';
import type { FileDeletion } from '@/types/api';

/**
 * Removing a deleted document for good, ahead of the 30-day sweep that would otherwise do it.
 *
 * <p>No reason field, unlike deleting the document in the first place: rule 4 already collected one,
 * it is quoted right above wherever this dialog opens from, and asking for a second reason to finish
 * what the first one started would be asking the same question twice. What this needs instead is
 * plain certainty, because unlike deleting it — which restore can undo — nothing undoes this.
 */
export function PurgeFileDialog({
  deletion,
  onClose,
}: {
  deletion: FileDeletion | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();

  const purge = useMutation({
    mutationFn: () => purgeFile(deletion!.fileId!),
    onSuccess: () => {
      void invalidateFileLists(queryClient);
      close();
    },
  });

  const close = () => {
    purge.reset();
    onClose();
  };

  return (
    <Modal
      open={deletion !== null}
      title="Permanently delete this document?"
      description={
        deletion
          ? `“${deletion.fileName}” and its file will be removed for good. This cannot be undone.`
          : undefined
      }
      onClose={close}
    >
      <div className="space-y-4">
        <Alert tone="error">
          There is no restore from here. Anyone who could still put this back — including you —
          loses that option the moment you confirm.
        </Alert>

        {purge.isError && <Alert tone="error">{toApiError(purge.error).message}</Alert>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close}>
            Keep it
          </Button>
          <Button
            type="button"
            variant="danger"
            loading={purge.isPending}
            onClick={() => purge.mutate()}
          >
            Delete permanently
          </Button>
        </div>
      </div>
    </Modal>
  );
}
