import { useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { fetchDownloadLink, setFavorite } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import { invalidateFileLists } from '@/lib/queryKeys';
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
  emptyAction,
  showLocation = false,
  onDelete,
  onReplace,
}: {
  files: FileItem[];
  emptyMessage: string;
  /**
   * The way out of an empty screen.
   *
   * <p>An empty state that only explains itself leaves the user to work out where to go next. Where
   * there is an obvious next step — uploading, on a list of your own uploads — it belongs here,
   * under the sentence that says nothing is there.
   */
  emptyAction?: ReactNode;
  /** My Uploads spans departments, so it needs the "where" column that a folder does not. */
  showLocation?: boolean;
  onDelete: (file: FileItem) => void;
  onReplace: (file: FileItem) => void;
}) {
  const [downloading, setDownloading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  /**
   * Starring re-reads the lists rather than patching the row, so a document that has just left the
   * favourites list actually leaves it. Both directions are idempotent server-side, so a rapid
   * double click settles on whatever the last request said.
   */
  const star = useMutation({
    mutationFn: (file: FileItem) => setFavorite(file.id, !file.favorite),
    onSuccess: () => void invalidateFileLists(queryClient),
    onError: (cause) => setError(toApiError(cause).message),
  });

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
        bg-surface px-6 py-12 text-center">
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
        {emptyAction && <div className="mt-4">{emptyAction}</div>}
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
        <table
          className={`w-full text-left text-sm ${showLocation ? 'min-w-[880px]' : 'min-w-[760px]'}`}
        >
          <thead className="border-b border-line bg-navy-50/60 text-xs uppercase tracking-wide text-slate-600">
            <tr>
              <th scope="col" className="px-5 py-3 font-semibold">Document</th>
              {showLocation && <th scope="col" className="px-5 py-3 font-semibold">Location</th>}
              <th scope="col" className="px-5 py-3 font-semibold whitespace-nowrap">Uploaded by</th>
              <th scope="col" className="px-5 py-3 font-semibold whitespace-nowrap">Uploaded</th>
              <th scope="col" className="px-5 py-3 text-right font-semibold">Actions</th>
            </tr>
          </thead>
          <tbody className="stagger divide-y divide-slate-100">
            {files.map((file) => (
              <tr
                key={file.id}
                className="group/row transition-colors duration-[--duration-base] ease-[--ease-settle]
                  hover:bg-navy-50/70"
              >
                <td className="max-w-[22rem] px-5 py-3">
                  <div className="flex items-center gap-3">
                    <StarButton
                      file={file}
                      busy={star.isPending && star.variables?.id === file.id}
                      onToggle={() => star.mutate(file)}
                    />
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
                  <td className="max-w-[14rem] px-5 py-3.5 text-slate-600">
                    <p className="truncate" title={file.departmentName}>
                      {file.departmentName}
                    </p>
                    <p className="truncate text-xs text-slate-400" title={file.folderName}>
                      {file.folderName}
                    </p>
                  </td>
                )}
                <td className="px-5 py-3.5 whitespace-nowrap text-slate-600">{file.uploadedByName}</td>
                <td className="px-5 py-3.5 whitespace-nowrap text-slate-600">
                  {formatDateTime(file.uploadedAt)}
                </td>
                <td className="px-3 py-3">
                  {/* Actions fade up on row hover on a pointer device, but stay permanently visible
                      on touch and for keyboard users — hover-only controls are unreachable there. */}
                  <div className="flex justify-end gap-1 sm:opacity-60 sm:transition-opacity
                    sm:duration-[--duration-base] sm:group-hover/row:opacity-100
                    sm:focus-within:opacity-100 sm:hover:opacity-100">
                    {/* Only offered when the browser can actually render it — see `previewable`.
                        A Preview button that downloaded instead would be a lie. */}
                    {file.previewable && (
                      <Link
                        to={`/files/${file.id}`}
                        className="inline-flex items-center rounded-lg px-2.5 py-1.5 text-[0.8125rem]
                          font-semibold text-navy-600 outline-none transition-all
                          duration-[--duration-quick] ease-[--ease-settle] hover:bg-navy-50
                          focus-visible:ring-2 focus-visible:ring-navy-300"
                      >
                        Preview
                      </Link>
                    )}
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => void download(file)}
                      loading={downloading === file.id}
                    >
                      Download
                    </Button>
                    {/* Hidden when the server says the caller may not change it; the server
                        re-checks anyway, so this is tidiness rather than protection. */}
                    {file.canModify && (
                      <>
                        <Button variant="ghost" size="sm" onClick={() => onReplace(file)}>
                          Replace
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
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
 * The star.
 *
 * <p>A real toggle button carrying `aria-pressed`, so a screen reader announces the state rather
 * than leaving it to a filled shape. The label says what the click will do — "Add to favorites" —
 * because that is what a user needs to hear before pressing it.
 */
function StarButton({
  file,
  busy,
  onToggle,
}: {
  file: FileItem;
  busy: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={busy}
      aria-pressed={file.favorite}
      aria-label={file.favorite ? `Remove ${file.fileName} from favorites` : `Add ${file.fileName} to favorites`}
      title={file.favorite ? 'Remove from favorites' : 'Add to favorites'}
      className={`shrink-0 rounded-md p-1 outline-none transition-all duration-[--duration-base]
        ease-[--ease-settle] hover:scale-110 focus-visible:ring-2 focus-visible:ring-navy-300
        disabled:opacity-50 ${file.favorite ? 'text-gold-500' : 'text-slate-300 hover:text-gold-400'}`}
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill={file.favorite ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-4.5 w-4.5"
      >
        <path d="m12 3 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5-5.8-3-5.8 3 1.1-6.5L2.6 9.8l6.5-.9L12 3Z" />
      </svg>
    </button>
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
