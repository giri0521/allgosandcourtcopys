import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { TextAreaField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { deleteFolder } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { invalidateFileLists } from '@/lib/queryKeys';
import type { Folder } from '@/types/api';

/**
 * Deleting an empty folder, with the reason the rules require.
 *
 * <p>Admin-only, and the server refuses this for the department's General folder or for one still
 * holding a document or a subfolder — the dialog surfaces whichever of those the server says rather
 * than guessing in advance, since a folder shown as "empty" here could have gained a subfolder or a
 * document between the page loading and the click.
 */
export function DeleteFolderDialog({
  folder,
  onClose,
}: {
  folder: Folder | null;
  onClose: () => void;
}) {
  const [reason, setReason] = useState('');
  const queryClient = useQueryClient();

  const remove = useMutation({
    mutationFn: () => deleteFolder(folder!.id, reason.trim()),
    onSuccess: () => {
      void invalidateFileLists(queryClient);
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
      open={folder !== null}
      title="Delete this folder?"
      description={folder ? `“${folder.name}” will be removed from ${folder.departmentName}.` : undefined}
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
          hint="Everyone else is notified with this reason, and with your name."
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
          <Button type="submit" variant="danger" loading={remove.isPending} disabled={!reason.trim()}>
            Delete folder
          </Button>
        </div>
      </form>
    </Modal>
  );
}
