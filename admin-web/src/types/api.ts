/**
 * Roles and statuses are Java enums on the wire, so they arrive uppercase. The database stores them
 * lowercase, but nothing outside the backend ever sees that form — see the AttributeConverters.
 */
export type Role = 'ADMIN' | 'MEMBER';
export type UserStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'INACTIVE';
export type RegistrationStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Uppercase for the same reason as the rest: the wire carries the Java enum name. */
export type FolderCategory =
  | 'CONTRACT'
  | 'GOVT_ORDER'
  | 'COURT_ORDER'
  | 'CIRCULAR'
  | 'ACT_RULE'
  | 'GENERAL';

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

/**
 * A department as the browsing screens see it.
 *
 * <p>The counts are absent from the public {@code /auth/departments} list, which returns only id and
 * name to an applicant who has no account yet.
 */
export interface Department {
  id: string;
  name: string;
  code?: string | null;
  description?: string | null;
  folderCount?: number;
  fileCount?: number;
}

export interface Folder {
  id: string;
  departmentId: string;
  departmentName: string;
  parentFolderId: string | null;
  name: string;
  category: FolderCategory;
  /** Live files only — a soft delete decrements it and a restore puts it back. */
  fileCount: number;
  createdAt: string;
}

export interface FileItem {
  id: string;
  folderId: string;
  folderName: string;
  departmentId: string;
  departmentName: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  uploadedById: string;
  uploadedByName: string;
  version: number;
  /**
   * Server-computed: true when the caller uploaded it or is an admin. Covers deleting and
   * replacing, which share one rule. A convenience for hiding buttons — the server re-checks on
   * every such call, so ignoring it gains nothing.
   */
  canModify: boolean;
  /** True when the browser can render these bytes in place — PDFs and images. */
  previewable: boolean;
  /** Whether *this* viewer has starred it. Another user's star is never visible. */
  favorite: boolean;
  uploadedAt: string;
}

/** A presigned URL the browser renders in place rather than saving. */
export interface PreviewLink {
  url: string;
  expiresAt: string;
  fileName: string;
  fileType: string;
}

/**
 * A row in the caller's own download history.
 *
 * <p>`available` goes false once the document is deleted. The row stays either way — a history that
 * dropped entries when something was removed would not be a history.
 */
export interface DownloadRecord {
  id: string;
  fileId: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  departmentId: string;
  departmentName: string;
  folderId: string;
  folderName: string;
  available: boolean;
  downloadedAt: string;
}

/**
 * The result of one upload request. Files are reported individually rather than the whole batch
 * failing, so eight documents with one bad file still upload seven.
 */
export interface UploadResult {
  uploaded: FileItem[];
  rejected: RejectedUpload[];
}

export interface RejectedUpload {
  fileName: string;
  /** e.g. FILE_CONTENT_MISMATCH, FILE_TYPE_NOT_ALLOWED, FILE_TOO_LARGE */
  code: string;
  message: string;
}

/** A short-lived presigned URL. The bytes never pass through the application server. */
export interface DownloadLink {
  url: string;
  expiresAt: string;
  fileName: string;
}

/** A row in the admin deletions log, carrying the reason the deleter had to give. */
export interface FileDeletion {
  id: string;
  fileId: string;
  fileName: string;
  departmentId: string;
  departmentName: string;
  folderId: string;
  folderName: string;
  deletedByName: string;
  reason: string;
  deletedAt: string;
  restoredByName: string | null;
  restoredAt: string | null;
  /** True while the file is still deleted, which is when restore is offered. */
  restorable: boolean;
}

/**
 * One notification.
 *
 * <p>`entityRef` is the server's free-form pointer back to the subject — `"file:{uuid}"` or
 * `"user:{uuid}"`. The client turns it into a route; the server deliberately does not, because URLs
 * are the web app's business.
 */
export interface Notification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  entityRef: string | null;
  read: boolean;
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
