import { api } from '@/lib/api';
import type { Notification, PageResponse } from '@/types/api';

/**
 * The caller's own notifications. There is no endpoint for anybody else's — an admin who wants to
 * know what a member was told reads the audit log, which records what happened rather than copying
 * someone's inbox.
 */

export async function fetchNotifications(
  unreadOnly: boolean,
  page = 0,
): Promise<PageResponse<Notification>> {
  const { data } = await api.get<PageResponse<Notification>>('/notifications', {
    params: { unread: unreadOnly, page },
  });
  return data;
}

/**
 * Just the badge number.
 *
 * <p>Separate from the list because the bell asks on every screen while the list is opened
 * occasionally — one count is a far cheaper question than a page of rows.
 */
export async function fetchUnreadCount(): Promise<number> {
  const { data } = await api.get<{ unread: number }>('/notifications/unread-count');
  return data.unread;
}

export async function markNotificationRead(id: string): Promise<Notification> {
  const { data } = await api.post<Notification>(`/notifications/${id}/read`);
  return data;
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.post('/notifications/read-all');
}

/**
 * Sends a message to everyone else with an account.
 *
 * @returns how many people were told — the client has no way of knowing how many accounts are
 *   active, and "sent to 42 people" is what tells the sender it actually went somewhere
 */
export async function sendAnnouncement(message: string): Promise<number> {
  const { data } = await api.post<{ recipients: number }>('/notifications/announcements', { message });
  return data.recipients;
}

/**
 * Turns the server's `entityRef` into a route.
 *
 * <p>The server writes `"file:{uuid}"`, `"folder:{uuid}"` and `"user:{uuid}"` and deliberately stops
 * there, so the mapping from a subject to a URL lives here — where the routes are actually defined.
 * An unrecognised kind yields no link rather than a broken one.
 *
 * <p>`user:` is the one that depends on who is reading. To an admin it means the account in the
 * members list; to everyone else it means their own account. Sending every reader to
 * `/admin/members` is what put a newly approved member on the Access Restricted screen the moment
 * they opened the notification telling them they had been approved — the route guard did exactly
 * its job, on a link that should never have pointed there.
 */
export function notificationLink(entityRef: string | null, isAdmin = false): string | null {
  if (!entityRef) return null;

  const [kind, id] = entityRef.split(':');
  if (kind === 'file' && id) return `/files/${id}`;
  if (kind === 'folder' && id) return `/folders/${id}`;
  if (kind === 'user' && id) return isAdmin ? '/admin/members' : '/profile';
  return null;
}
