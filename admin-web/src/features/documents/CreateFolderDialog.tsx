import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SelectField, TextField } from '@/components/ui/Field';
import { Modal } from '@/components/ui/Modal';
import { createFolder } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatCategory } from '@/lib/format';
import type { FolderCategory } from '@/types/api';

const CATEGORIES: FolderCategory[] = [
  'GENERAL',
  'GOVT_ORDER',
  'COURT_ORDER',
  'CIRCULAR',
  'CONTRACT',
  'ACT_RULE',
];

/**
 * Creates a folder in a department, optionally inside another folder.
 *
 * <p>The duplicate-name check is the server's: a folder name is unique per (department, parent), so
 * the 409 is displayed rather than pre-empted — two people creating "Circulars 2026" at the same
 * moment would both pass a client-side check.
 */
export function CreateFolderDialog({
  open,
  departmentId,
  parentFolderId,
  onClose,
}: {
  open: boolean;
  departmentId: string;
  parentFolderId?: string;
  onClose: () => void;
}) {
  const [name, setName] = useState('');
  const [category, setCategory] = useState<FolderCategory>('GENERAL');
  const queryClient = useQueryClient();

  const create = useMutation({
    mutationFn: () => createFolder(departmentId, name.trim(), category, parentFolderId),
    onSuccess: () => {
      // Re-read from the server rather than splicing the new folder in locally, so a folder someone
      // else created in the meantime appears too.
      void queryClient.invalidateQueries({ queryKey: ['folders'] });
      void queryClient.invalidateQueries({ queryKey: ['departments'] });
      close();
    },
  });

  const close = () => {
    setName('');
    setCategory('GENERAL');
    create.reset();
    onClose();
  };

  return (
    <Modal
      open={open}
      title="New folder"
      description={parentFolderId ? 'Created inside the current folder.' : undefined}
      onClose={close}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          if (name.trim()) create.mutate();
        }}
        className="space-y-4"
      >
        <TextField
          label="Folder name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={255}
          required
          autoFocus
        />

        <SelectField
          label="Category"
          value={category}
          onChange={(event) => setCategory(event.target.value as FolderCategory)}
        >
          {CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {formatCategory(value)}
            </option>
          ))}
        </SelectField>

        {create.isError && <Alert tone="error">{toApiError(create.error).message}</Alert>}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending} disabled={!name.trim()}>
            Create folder
          </Button>
        </div>
      </form>
    </Modal>
  );
}
