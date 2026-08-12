import { api } from '@/lib/api';
import type {
  Department,
  DownloadLink,
  DownloadRecord,
  FileDeletion,
  FileItem,
  Folder,
  FolderCategory,
  PageResponse,
  PreviewLink,
  UploadResult,
} from '@/types/api';

/**
 * Every document call in one place, the way `features/admin/api.ts` holds every admin call.
 *
 * <p>Departments, folders and files are one API surface in practice — a folder screen needs all
 * three — so splitting them across three modules would only mean three imports on every page.
 */

export async function fetchDepartments(): Promise<Department[]> {
  const { data } = await api.get<Department[]>('/departments');
  return data;
}

/** Omit `parentFolderId` for the folders at the department's top level. */
export async function fetchFolders(
  departmentId: string,
  parentFolderId?: string,
): Promise<Folder[]> {
  const { data } = await api.get<Folder[]>(`/departments/${departmentId}/folders`, {
    params: { parentFolderId },
  });
  return data;
}

export async function fetchFolder(folderId: string): Promise<Folder> {
  const { data } = await api.get<Folder>(`/folders/${folderId}`);
  return data;
}

/** Root-first, ready to render straight into a breadcrumb. */
export async function fetchBreadcrumb(folderId: string): Promise<Folder[]> {
  const { data } = await api.get<Folder[]>(`/folders/${folderId}/breadcrumb`);
  return data;
}

export async function fetchSubfolders(folderId: string): Promise<Folder[]> {
  const { data } = await api.get<Folder[]>(`/folders/${folderId}/folders`);
  return data;
}

export async function fetchFolderFiles(
  folderId: string,
  page = 0,
): Promise<PageResponse<FileItem>> {
  const { data } = await api.get<PageResponse<FileItem>>(`/folders/${folderId}/files`, {
    params: { page },
  });
  return data;
}

/** One document. 404 once it has been deleted — see the deletions log for those. */
export async function fetchFile(fileId: string): Promise<FileItem> {
  const { data } = await api.get<FileItem>(`/files/${fileId}`);
  return data;
}

export async function fetchMyUploads(page = 0): Promise<PageResponse<FileItem>> {
  const { data } = await api.get<PageResponse<FileItem>>('/files/my-uploads', {
    params: { page },
  });
  return data;
}

export async function createFolder(
  departmentId: string,
  name: string,
  category: FolderCategory,
  parentFolderId?: string,
): Promise<Folder> {
  const { data } = await api.post<Folder>(`/departments/${departmentId}/folders`, {
    name,
    category,
    parentFolderId,
  });
  return data;
}

/**
 * Uploads into any department's folder — uploads are deliberately not restricted to the uploader's
 * own department.
 *
 * @param onProgress receives 0–100 for the request as a whole. The browser reports bytes sent for
 *   the whole body, so per-file progress means one request per file; the caller decides.
 */
export async function uploadFiles(
  folderId: string,
  files: File[],
  onProgress?: (percent: number) => void,
): Promise<UploadResult> {
  const form = new FormData();
  files.forEach((file) => form.append('files', file));

  const { data } = await api.post<UploadResult>('/files', form, {
    params: { folderId },
    onUploadProgress: (event) => {
      if (!onProgress) return;
      // `total` is absent on some proxies; leaving the bar where it is beats showing NaN.
      if (event.total) onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
  return data;
}

/**
 * Replaces a document with a corrected version, keeping its id and bumping its version.
 *
 * <p>Unlike upload, a refusal here is a failed request rather than an entry in a `rejected` list:
 * there is only one file, so its rejection is the request's.
 */
export async function replaceFile(
  fileId: string,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<FileItem> {
  const form = new FormData();
  form.append('file', file);

  const { data } = await api.post<FileItem>(`/files/${fileId}/replace`, form, {
    onUploadProgress: (event) => {
      if (!onProgress) return;
      if (event.total) onProgress(Math.round((event.loaded / event.total) * 100));
    },
  });
  return data;
}

/** A fresh presigned URL, valid for minutes. Never cache one. */
export async function fetchDownloadLink(fileId: string): Promise<DownloadLink> {
  const { data } = await api.get<DownloadLink>(`/files/${fileId}/download-link`);
  return data;
}

/**
 * Soft-deletes a file. The reason is required by the server and is sent to every admin, so there is
 * no way to call this without one.
 */
export async function deleteFile(fileId: string, reason: string): Promise<void> {
  await api.delete(`/files/${fileId}`, { data: { reason } });
}

// ------------------------------------------------------------------- find and revisit

export interface SearchFilters {
  q: string;
  departmentId?: string;
  category?: FolderCategory;
  /** ISO instants. The server treats both bounds as inclusive. */
  from?: string;
  to?: string;
}

/**
 * Global search by part of a document's name, across every department.
 *
 * <p>The server refuses anything shorter than two characters with `SEARCH_TOO_SHORT`, so callers
 * should not fire a request for a single keystroke.
 */
export async function searchFiles(
  filters: SearchFilters,
  page = 0,
): Promise<PageResponse<FileItem>> {
  const { data } = await api.get<PageResponse<FileItem>>('/files/search', {
    params: {
      q: filters.q,
      departmentId: filters.departmentId || undefined,
      category: filters.category || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page,
    },
  });
  return data;
}

/** Newest documents across every department, for the home dashboard. */
export async function fetchRecentFiles(size = 5): Promise<PageResponse<FileItem>> {
  const { data } = await api.get<PageResponse<FileItem>>('/files/recent', { params: { size } });
  return data;
}

/**
 * An inline presigned URL. Only for documents whose `previewable` flag is true — anything else is
 * refused with `PREVIEW_UNSUPPORTED` rather than quietly downloading.
 */
export async function fetchPreviewLink(fileId: string): Promise<PreviewLink> {
  const { data } = await api.get<PreviewLink>(`/files/${fileId}/preview-link`);
  return data;
}

/** Both directions are idempotent, so the UI can toggle without tracking what the server thinks. */
export async function setFavorite(fileId: string, favorite: boolean): Promise<FileItem> {
  const { data } = favorite
    ? await api.post<FileItem>(`/files/${fileId}/favorite`)
    : await api.delete<FileItem>(`/files/${fileId}/favorite`);
  return data;
}

export async function fetchFavorites(page = 0): Promise<PageResponse<FileItem>> {
  const { data } = await api.get<PageResponse<FileItem>>('/favorites', { params: { page } });
  return data;
}

export async function fetchDownloadHistory(page = 0): Promise<PageResponse<DownloadRecord>> {
  const { data } = await api.get<PageResponse<DownloadRecord>>('/downloads', { params: { page } });
  return data;
}

// ------------------------------------------------------------------------ admin only

export async function fetchDeletions(
  status: 'deleted' | 'all',
  page = 0,
): Promise<PageResponse<FileDeletion>> {
  const { data } = await api.get<PageResponse<FileDeletion>>('/admin/deletions', {
    params: { status, page },
  });
  return data;
}

export async function restoreFile(fileId: string): Promise<FileItem> {
  const { data } = await api.post<FileItem>(`/admin/files/${fileId}/restore`);
  return data;
}
