import type { ToneName } from '@/lib/tones';

/**
 * Turning an action name into words, in one place.
 *
 * <p>Separate from the row component so both the log viewer and its filter dropdown can label an
 * action without importing a component — and so the file exports only functions, which is what the
 * fast-refresh rule wants.
 *
 * <p>An action with no entry here still renders: the raw name is humanised rather than dropped, so
 * a new kind of event appears the moment the backend records it and the worst case is a slightly
 * clumsy label instead of a blank row.
 */
const LABELS: Record<string, string> = {
  register: 'Registered',
  otp_sent: 'OTP sent',
  otp_verified: 'Signed in with OTP',
  otp_failed: 'OTP rejected',
  login_password: 'Signed in with password',
  login_failed: 'Sign-in failed',
  login_blocked_status: 'Sign-in blocked — account not active',
  login_blocked_otp_required: 'Sign-in blocked — OTP required today',
  logout: 'Signed out',
  logout_all: 'Signed out everywhere',
  password_changed: 'Password changed',
  registration_approved: 'Approved a registration',
  registration_rejected: 'Rejected a registration',
  user_status_changed: 'Changed an account status',
  user_updated: 'Updated a profile',
  department_created: 'Created a department',
  department_updated: 'Updated a department',
  folder_created: 'Created a folder',
  folder_updated: 'Renamed a folder',
  folder_deleted: 'Deleted a folder',
  file_uploaded: 'Uploaded a document',
  file_previewed: 'Previewed a document',
  file_downloaded: 'Downloaded a document',
  file_replaced: 'Replaced a document',
  file_deleted: 'Deleted a document',
  file_restored: 'Restored a document',
};

export function auditLabel(action: string): string {
  if (LABELS[action]) return LABELS[action];
  // e.g. "folder_archived" → "Folder archived"
  const words = action.replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Colour by what kind of event it was, drawn from the shared palette rather than chosen here. */
export function auditTone(action: string): ToneName {
  if (action.includes('failed') || action.includes('rejected') || action.includes('blocked')) {
    return 'rose';
  }
  if (action.includes('deleted')) return 'rose';
  if (action.includes('approved') || action.includes('restored')) return 'emerald';
  if (action.includes('uploaded') || action.includes('replaced')) return 'navy';
  if (action.includes('downloaded') || action.includes('previewed')) return 'sky';
  return 'slate';
}

/**
 * The metadata the server stored, as readable pairs.
 *
 * <p>Rendered from whatever keys are present rather than from a per-action template. That is what
 * lets a reviewer see the reason attached to a deletion, or the from/to of a status change, without
 * anyone having written display code for each.
 */
export function readableMetadata(metadata: string | null): [string, string][] {
  if (!metadata) return [];
  try {
    const parsed: unknown = JSON.parse(metadata);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return [];
    return Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [
      key.replace(/([A-Z])/g, ' $1').replace(/^./, (character) => character.toUpperCase()),
      String(value),
    ]);
  } catch {
    // Stored JSON that will not parse is a bug worth seeing, not worth crashing a screen over.
    return [['Metadata', metadata]];
  }
}
