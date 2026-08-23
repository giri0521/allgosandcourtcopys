import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { fetchDownloadLink, fetchFile, fetchPreviewLink } from '@/features/documents/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime, formatFileSize, formatFileType } from '@/lib/format';
import { useState } from 'react';

/**
 * A document, on screen, without downloading it.
 *
 * <p>The bytes never pass through this application: the server hands out a presigned URL valid for
 * minutes and the browser fetches storage directly. That is why the URL is requested here, at the
 * moment of viewing, rather than being embedded in a list — a link that leaks from a page the user
 * left open stops working almost immediately.
 *
 * <p>PDFs render in an {@code <object>} and images in an {@code <img>}. Anything else never reaches
 * this screen with a preview: the server refuses, and the file table only offers Preview when the
 * document says it is previewable.
 */
export function FilePreviewPage() {
  const { fileId = '' } = useParams();
  const navigate = useNavigate();
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  const file = useQuery({ queryKey: ['file', fileId], queryFn: () => fetchFile(fileId) });

  const preview = useQuery({
    queryKey: ['preview', fileId],
    queryFn: () => fetchPreviewLink(fileId),
    enabled: file.data?.previewable === true,
    // A presigned URL expires; re-reading a cached one would show a broken frame.
    staleTime: 0,
    gcTime: 0,
  });

  const download = async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      const link = await fetchDownloadLink(fileId);
      window.location.assign(link.url);
    } catch (cause) {
      setDownloadError(toApiError(cause).message);
    } finally {
      setDownloading(false);
    }
  };

  if (file.isError) {
    return (
      <AppShell title="Document">
        <Alert tone="error">{toApiError(file.error).message}</Alert>
      </AppShell>
    );
  }

  const document = file.data;

  return (
    <AppShell
      title={document?.fileName ?? 'Document'}
      subtitle={
        document
          ? `${formatFileType(document.fileType)} · ${formatFileSize(document.sizeBytes)} · uploaded by ${document.uploadedByName}`
          : undefined
      }
      actions={
        <div className="flex gap-2">
          <Button
            variant="secondary"
            className="border-red-300! bg-red-50! text-red-600! hover:border-red-400! hover:bg-red-100!"
            onClick={() =>
              // A direct link (bookmark, shared URL) has no in-app page to go back to — fall back
              // to the folder, or Home if even that is unknown.
              window.history.length > 1
                ? navigate(-1)
                : navigate(document ? `/folders/${document.folderId}` : '/home')
            }
          >
            <svg
              aria-hidden
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={2}
              strokeLinecap="round"
              strokeLinejoin="round"
              className="h-4 w-4"
            >
              <path d="M18 6 6 18" />
              <path d="M6 6l12 12" />
            </svg>
            Close
          </Button>
          {document && (
            <Link
              to={`/folders/${document.folderId}`}
              className="inline-flex items-center rounded-lg border border-navy-300 bg-surface px-4 py-2.5
                text-sm font-semibold text-navy-700 transition-colors duration-[--duration-base]
                hover:bg-navy-50"
            >
              Open folder
            </Link>
          )}
          <Button loading={downloading} onClick={() => void download()}>
            Download
          </Button>
        </div>
      }
    >
      <div className="space-y-4">
        {downloadError && <Alert tone="error">{downloadError}</Alert>}

        {document && (
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-slate-600">
            <span>{document.departmentName}</span>
            <span aria-hidden>·</span>
            <span>{document.folderName}</span>
            <span aria-hidden>·</span>
            <span>Filed {formatDateTime(document.uploadedAt)}</span>
            {document.version > 1 && (
              <span className="rounded-full bg-navy-50 px-2 py-0.5 text-xs font-semibold text-navy-700">
                Version {document.version}
              </span>
            )}
          </div>
        )}

        {document && !document.previewable ? (
          <NotPreviewable type={document.fileType} onDownload={() => void download()} busy={downloading} />
        ) : preview.isError ? (
          <Alert tone="error">{toApiError(preview.error).message}</Alert>
        ) : (
          <div className="animate-fade overflow-hidden rounded-xl border border-line bg-surface shadow-card">
            {preview.isPending || !preview.data ? (
              <div className="skeleton h-[70vh] w-full" aria-hidden />
            ) : preview.data.fileType.startsWith('image/') ? (
              <img
                src={preview.data.url}
                alt={preview.data.fileName}
                className="mx-auto max-h-[75vh] w-auto max-w-full"
              />
            ) : (
              // <object> rather than <iframe>: it degrades to its children when the browser has no
              // PDF viewer, which is exactly the fallback needed here.
              <object
                data={preview.data.url}
                type="application/pdf"
                title={preview.data.fileName}
                className="h-[75vh] w-full"
              >
                <div className="p-8 text-center text-sm text-slate-600">
                  <p>This browser cannot display PDFs in the page.</p>
                  <Button className="mt-4" loading={downloading} onClick={() => void download()}>
                    Download instead
                  </Button>
                </div>
              </object>
            )}
          </div>
        )}
      </div>
    </AppShell>
  );
}

function NotPreviewable({
  type,
  onDownload,
  busy,
}: {
  type: string;
  onDownload: () => void;
  busy: boolean;
}) {
  return (
    <div
      className="animate-rise flex flex-col items-center rounded-xl border border-dashed
        border-line-strong bg-surface px-6 py-16 text-center"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-12 w-12 text-slate-300"
      >
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
        <path d="M14 2v6h6" />
      </svg>
      <h2 className="mt-4 font-semibold text-slate-900">
        {formatFileType(type)} documents cannot be shown in the browser
      </h2>
      <p className="mt-1 max-w-md text-sm text-slate-500">
        Only PDFs and images can be previewed here. Download it to open it in the application it
        belongs to.
      </p>
      <Button className="mt-5" loading={busy} onClick={onDownload}>
        Download
      </Button>
    </div>
  );
}
