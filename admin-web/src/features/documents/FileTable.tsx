import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { fetchDownloadLink } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import type { FileItem } from '@/types/api';

/**
 * The list of documents, used by both a folder and My Uploads.
 *
 * <p>Downloading is two steps: ask the server for a presigned URL, then follow it. The URL is
 * fetched at the moment of the click rather than rendered into every row, so nothing on screen is a
 * live link to storage and a stale one cannot linger in the DOM.
 */
export function FileTable({
  files,
  emptyMessage,
  showLocation = false,
  onDelete,
  onReplace,
}: {
  files: FileItem[];
  emptyMessage: string;
  /** My Uploads spans departments, so it needs the "where" column that a folder does not. */
  showLocation?: boolean;
  onDelete: (file: FileItem) => void;
  onReplace: (file: FileItem) => void;
}) {
  const [downloading, setDownloading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const download = async (file: FileItem) => {
    setDownloading(file.id);
    setError(null);
    try {
      const link = await fetchDownloadLink(file.id);
      window.location.assign(link.url);
    } catch (cause) {
      setError(toApiError(cause).message);
    } finally {
      setDownloading(null);
    }
  };

  if (files.length === 0) {
    return <p className="rounded-xl border border-slate-200 bg-white p-6 text-sm text-slate-500">{emptyMessage}</p>;
  }

  return (
    <div className="space-y-3">
      {error && (
        <p role="alert" className="text-sm font-medium text-red-600">
          {error}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-5 py-3 font-semibold">Document</th>
              {showLocation && <th scope="col" className="px-5 py-3 font-semibold">Location</th>}
              <th scope="col" className="px-5 py-3 font-semibold">Uploaded by</th>
              <th scope="col" className="px-5 py-3 font-semibold">Uploaded</th>
              <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {files.map((file) => (
              <tr key={file.id} className="hover:bg-slate-50">
                <td className="px-5 py-3">
                  <p className="font-medium text-slate-900">{file.fileName}</p>
                  <p className="text-xs text-slate-500">
                    {formatFileType(file.fileType)} · {formatFileSize(file.sizeBytes)}
                    {/* Only worth saying once a document has actually been replaced. */}
                    {file.version > 1 && ` · version ${file.version}`}
                  </p>
                </td>
                {showLocation && (
                  <td className="px-5 py-3 text-slate-600">
                    <p>{file.departmentName}</p>
                    <p className="text-xs text-slate-400">{file.folderName}</p>
                  </td>
                )}
                <td className="px-5 py-3 text-slate-600">{file.uploadedByName}</td>
                <td className="px-5 py-3 text-slate-600">{formatDateTime(file.uploadedAt)}</td>
                <td className="px-5 py-3">
                  <div className="flex justify-end gap-1">
                    <Button
                      variant="ghost"
                      onClick={() => void download(file)}
                      loading={downloading === file.id}
                    >
                      Download
                    </Button>
                    {/* Hidden when the server says the caller may not change it; the server
                        re-checks anyway, so this is tidiness rather than protection. */}
                    {file.canModify && (
                      <>
                        <Button variant="ghost" onClick={() => onReplace(file)}>
                          Replace
                        </Button>
                        <Button
                          variant="ghost"
                          onClick={() => onDelete(file)}
                          className="!text-red-600 hover:!bg-red-50"
                        >
                          Delete
                        </Button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
