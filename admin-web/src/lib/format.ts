/**
 * Dates are always shown in Asia/Kolkata, the same zone the server uses to decide the daily-OTP
 * rule. An admin in another timezone must not see a different day from the one the server enforced.
 */
export function formatDateTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('en-IN', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Kolkata',
  });
}

/** File sizes in the units a clerk reads, not bytes. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** The five document classes the office files under, plus the plain container. */
const CATEGORY_LABELS: Record<string, string> = {
  CONTRACT: 'Contract',
  GOVT_ORDER: 'Government Order',
  COURT_ORDER: 'Court Order',
  CIRCULAR: 'Circular',
  ACT_RULE: 'Act / Rule',
  GENERAL: 'General',
};

export function formatCategory(category: string): string {
  return CATEGORY_LABELS[category] ?? category;
}

/** A short label for the file-type column; the full MIME type means nothing to a user. */
export function formatFileType(contentType: string): string {
  if (contentType === 'application/pdf') return 'PDF';
  if (contentType.startsWith('image/')) return contentType.slice(6).toUpperCase();
  if (contentType.includes('wordprocessingml') || contentType === 'application/msword') return 'Word';
  if (contentType.includes('spreadsheetml') || contentType === 'application/vnd.ms-excel') return 'Excel';
  return 'File';
}
