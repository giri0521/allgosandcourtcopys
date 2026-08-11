import { api } from '@/lib/api';
import type {
  Department,
  DownloadLink,
  FileDeletion,
  FileItem,
  Folder,
  FolderCategory,
  PageResponse,
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
