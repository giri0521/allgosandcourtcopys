import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { fetchDownloadLink } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import { fileTypeTone } from '@/lib/tones';
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
    return (
      <div className="animate-rise flex flex-col items-center rounded-xl border border-dashed border-line-strong
        bg-white px-6 py-12 text-center">
        <svg
          aria-hidden
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          strokeLinecap="round"
          strokeLinejoin="round"
          className="h-10 w-10 text-slate-300"
        >
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
          <path d="M14 2v6h6" />
        </svg>
        <p className="mt-3 text-sm text-slate-500">{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {error && (
        <p role="alert" className="text-sm font-medium text-red-600">
          {error}
        </p>
      )}

      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-line bg-surface-sunken text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="px-5 py-3 font-semibold">Document</th>
              {showLocation && <th scope="col" className="px-5 py-3 font-semibold">Location</th>}
              <th scope="col" className="px-5 py-3 font-semibold">Uploaded by</th>
              <th scope="col" className="px-5 py-3 font-semibold">Uploaded</th>
              <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="stagger divide-y divide-slate-100">
            {files.map((file) => (
              <tr
                key={file.id}
                className="group/row transition-colors duration-[--duration-base] ease-[--ease-settle]
                  hover:bg-navy-50/40"
              >
                <td className="px-5 py-3">
                  <div className="flex items-center gap-3">
                    <FileGlyph contentType={file.fileType} />
                    <div className="min-w-0">
                      <p className="truncate font-medium text-slate-900">{file.fileName}</p>
                      <p className="text-xs text-slate-500">
                        {formatFileType(file.fileType)} · {formatFileSize(file.sizeBytes)}
                        {/* Only worth saying once a document has actually been replaced. */}
                        {file.version > 1 && (
                          <span className="ml-1.5 rounded-full bg-navy-50 px-1.5 py-0.5 font-medium text-navy-700">
                            v{file.version}
                          </span>
                        )}
                      </p>
                    </div>
                  </div>
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
                  {/* Actions fade up on row hover on a pointer device, but stay permanently visible
                      on touch and for keyboard users — hover-only controls are unreachable there. */}
                  <div className="flex justify-end gap-1 sm:opacity-60 sm:transition-opacity
                    sm:duration-[--duration-base] sm:group-hover/row:opacity-100
                    sm:focus-within:opacity-100 sm:hover:opacity-100">
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
                          className="text-red-600! hover:bg-red-50!"
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

/**
 * A tinted square per file type — red for PDF, emerald for a spreadsheet, and so on.
 *
 * <p>Colour is never the only signal: the type is also spelled out in the line underneath, so this
 * is recognition at a glance rather than information only some people receive.
 */
function FileGlyph({ contentType }: { contentType: string }) {
  return (
    <span
      aria-hidden
      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg transition-transform
        duration-[--duration-base] ease-[--ease-settle] group-hover/row:scale-105
        ${fileTypeTone(contentType).chip}`}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-4.5 w-4.5"
      >
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
        <path d="M14 2v6h6" />
      </svg>
    </span>
  );
}
