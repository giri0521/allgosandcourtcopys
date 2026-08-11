import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextAreaField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { deleteFile } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import type { FileItem } from '@/types/api';

/**
 * Deleting a document, with the reason the rules require.
 *
 * <p>The reason is not a formality and the dialog says so: it goes to every administrator as a
 * notification. The Delete button stays disabled until something is typed, but the server refuses a
 * blank reason regardless — this only saves the user a round trip.
 */
export function DeleteFileDialog({
  file,
  onClose,
}: {
  file: FileItem | null;
  onClose: () => void;
}) {
  const [reason, setReason] = useState('');
  const queryClient = useQueryClient();

  const remove = useMutation({
    mutationFn: () => deleteFile(file!.id, reason.trim()),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['folder-files'] });
      void queryClient.invalidateQueries({ queryKey: ['my-uploads'] });
      void queryClient.invalidateQueries({ queryKey: ['folders'] });
      void queryClient.invalidateQueries({ queryKey: ['deletions'] });
      close();
    },
  });

  const close = () => {
    setReason('');
    remove.reset();
    onClose();
  };

  return (
    <Modal
      open={file !== null}
      title="Delete this document?"
      description={file ? `“${file.fileName}” will be removed from ${file.folderName}.` : undefined}
      onClose={close}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (reason.trim()) remove.mutate();
        }}
        className="space-y-4"
      >
        <TextAreaField
          label="Reason for deleting"
          hint="Every administrator is notified with this reason."
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          rows={3}
          maxLength={1000}
          required
        />

        {remove.isError && <Alert tone="error">{toApiError(remove.error).message}</Alert>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close}>
            Keep it
          </Button>
          <Button
            type="submit"
            loading={remove.isPending}
            disabled={!reason.trim()}
            className="!bg-red-600 hover:!bg-red-700"
          >
            Delete document
          </Button>
        </div>
      </form>
    </Modal>
  );
}
