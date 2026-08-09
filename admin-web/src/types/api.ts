export type Role = 'admin' | 'member';
export type UserStatus = 'pending' | 'active' | 'rejected' | 'inactive';

export type FolderCategory =
  | 'contract'
  | 'govt_order'
  | 'court_order'
  | 'circular'
  | 'act_rule'
  | 'general';

export interface CurrentUser {
  id: string;
  fullName: string;
  mobileNumber: string;
  email: string | null;
  role: Role;
  status: UserStatus;
  departmentId: string | null;
  departmentName: string | null;
  designation: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface Department {
  id: string;
  name: string;
  code: string | null;
  isActive: boolean;
  folderCount?: number;
  fileCount?: number;
}

export interface Folder {
  id: string;
  departmentId: string;
  parentFolderId: string | null;
  name: string;
  category: FolderCategory;
  fileCount: number;
}

export interface FileItem {
  id: string;
  folderId: string;
  departmentId: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  version: number;
  uploadedBy: string;
  uploadedByName: string;
  createdAt: string;
  /** Server-computed: true when the caller is the uploader or an admin. */
  canModify: boolean;
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  entityRef: string | null;
  isRead: boolean;
  createdAt: string;
}

/** Shape returned by GlobalExceptionHandler for every non-2xx response. */
export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
