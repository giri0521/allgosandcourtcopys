import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { uploadFiles } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatFileSize } from '@/lib/format';

type Status = 'waiting' | 'uploading' | 'done' | 'failed';

interface Item {
  file: File;
  status: Status;
  percent: number;
  /** The server's wording for a refusal — never invented here. */
  error?: string;
}

/**
 * Uploads documents into the current folder, one request per file.
 *
 * <p>One request each rather than one for the batch, because the browser reports bytes sent for the
 * whole body: a single request could only ever draw one bar for eight files. It also means a
 * rejected file — a .pdf whose bytes are an executable — leaves the others untouched, which matches
 * what the server does anyway.
 *
 * <p>Not a TanStack mutation: this is progressive per-file state driven by upload callbacks rather
 * than a single request with a single result. The folder's queries are invalidated at the end, which
 * is the part that has to go through the query client.
 */
export function UploadDialog({
  open,
  folderId,
  folderName,
  onClose,
}: {
  open: boolean;
  folderId: string;
  folderName: string;
  onClose: () => void;
}) {
  const [items, setItems] = useState<Item[]>([]);
  const [busy, setBusy] = useState(false);
  const queryClient = useQueryClient();

  const update = (index: number, patch: Partial<Item>) => {
    setItems((current) =>
      current.map((item, position) => (position === index ? { ...item, ...patch } : item)),
    );
  };

  const start = async () => {
    setBusy(true);

    for (let index = 0; index < items.length; index += 1) {
      if (items[index].status === 'done') continue;
      update(index, { status: 'uploading', percent: 0, error: undefined });

      try {
        const result = await uploadFiles(folderId, [items[index].file], (percent) =>
          update(index, { percent }),
        );

        // A 200 does not mean accepted: the server reports refusals in `rejected`.
        const refusal = result.rejected[0];
        if (refusal) {
          update(index, { status: 'failed', error: refusal.message });
        } else {
          update(index, { status: 'done', percent: 100 });
        }
      } catch (error) {
        update(index, { status: 'failed', error: toApiError(error).message });
      }
    }

    setBusy(false);
    void queryClient.invalidateQueries({ queryKey: ['folder-files', folderId] });
    void queryClient.invalidateQueries({ queryKey: ['my-uploads'] });
    void queryClient.invalidateQueries({ queryKey: ['folders'] });
  };

  const close = () => {
    if (busy) return; // closing mid-upload would leave the bars lying about what happened
    setItems([]);
    onClose();
  };

  const pending = items.filter((item) => item.status !== 'done').length;

  return (
    <Modal open={open} title="Upload documents" description={`Into ${folderName}`} onClose={close}>
      <div className="space-y-4">
        <div>
          <label
            htmlFor="upload-input"
            className="flex cursor-pointer flex-col items-center justify-center rounded-xl border-2
              border-dashed border-slate-300 px-4 py-6 text-center transition hover:border-navy-400"
          >
            <span className="text-sm font-medium text-navy-700">Choose files</span>
            <span className="mt-1 text-xs text-slate-500">
              PDF, JPG, PNG, TIFF, Word or Excel · up to 50 MB each
            </span>
          </label>
          <input
            id="upload-input"
            type="file"
            multiple
            className="sr-only"
            disabled={busy}
            onChange={(event) => {
              const chosen = Array.from(event.target.files ?? []);
              setItems(chosen.map((file) => ({ file, status: 'waiting', percent: 0 })));
              event.target.value = ''; // so the same file can be picked again after a failure
            }}
          />
        </div>

        {items.length > 0 && (
          <ul className="max-h-64 space-y-2 overflow-y-auto">
            {items.map((item, index) => (
              <li key={`${item.file.name}-${index}`} className="rounded-lg border border-slate-200 p-3">
                <div className="flex items-baseline justify-between gap-3">
                  <span className="truncate text-sm font-medium text-slate-800">
                    {item.file.name}
                  </span>
                  <span className="shrink-0 text-xs text-slate-500">
                    {formatFileSize(item.file.size)}
                  </span>
                </div>

                <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className={`h-full transition-all ${
                      item.status === 'failed'
                        ? 'bg-red-500'
                        : item.status === 'done'
                          ? 'bg-emerald-500'
                          : 'bg-navy-500'
                    }`}
                    style={{ width: `${item.status === 'failed' ? 100 : item.percent}%` }}
                  />
                </div>

                {item.status === 'failed' && (
                  <p role="alert" className="mt-1.5 text-xs font-medium text-red-600">
                    {item.error}
                  </p>
                )}
                {item.status === 'done' && (
                  <p className="mt-1.5 text-xs font-medium text-emerald-700">Uploaded</p>
                )}
              </li>
            ))}
          </ul>
        )}

        {items.some((item) => item.status === 'failed') && !busy && (
          <Alert tone="error">
            Some files were not accepted. The rest were uploaded — fix those and try again.
          </Alert>
        )}

        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={close} disabled={busy}>
            {items.some((item) => item.status === 'done') ? 'Done' : 'Cancel'}
          </Button>
          <Button type="button" onClick={start} loading={busy} disabled={pending === 0}>
            Upload {pending > 0 ? `${pending} file${pending === 1 ? '' : 's'}` : ''}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
