import { Modal } from '@/components/ui/Modal';
import type { FileItem } from '@/types/api';

/**
 * The Abstract paragraph read from a document, shown on demand rather than inline in the table —
 * a government order's abstract runs to several lines, which is too much for a table row.
 */
export function DescriptionDialog({ file, onClose }: { file: FileItem | null; onClose: () => void }) {
  return (
    <Modal open={file !== null} title="Description" description={file?.fileName} onClose={onClose}>
      {file?.goNumber && (
        <p className="mb-3 text-sm font-semibold text-navy-700">{file.goNumber}</p>
      )}
      <p className="max-h-[50vh] overflow-y-auto whitespace-pre-line text-sm leading-relaxed text-slate-700">
        {file?.description}
      </p>
    </Modal>
  );
}
