/**
 * Roles and statuses are Java enums on the wire, so they arrive uppercase. The database stores them
 * lowercase, but nothing outside the backend ever sees that form — see the AttributeConverters.
 */
export type Role = 'ADMIN' | 'MEMBER';
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'INACTIVE';
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

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

/** A row in the admin approval queue. */
export interface RegistrationRequest {
  id: string;
  userId: string;
  fullName: string;
  mobileNumber: string;
  email: string | null;
  designation: string | null;
  departmentId: string | null;
  departmentName: string | null;
  requestedRole: Role;
  status: RegistrationStatus;
  /** The applicant's account status, which approval is what moves. */
  accountStatus: UserStatus;
  reviewNote: string | null;
  reviewedByName: string | null;
  reviewedAt: string | null;
  submittedAt: string;
}

/** A row in the admin members list. */
export interface Member {
  id: string;
  fullName: string;
  mobileNumber: string;
  email: string | null;
  designation: string | null;
  departmentId: string | null;
  departmentName: string | null;
  role: Role;
  status: UserStatus;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface MemberCounts {
  all: number;
  pending: number;
  active: number;
  inactive: number;
  rejected: number;
  /** Registration requests still awaiting review, which is not the same as pending accounts. */
  pendingRequests: number;
}

/** The wire shape of every paged list endpoint. */
export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** Shape returned by GlobalExceptionHandler for every non-2xx response. */
export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
