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
