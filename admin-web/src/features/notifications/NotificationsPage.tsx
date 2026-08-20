import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { SkeletonRows } from '@/components/ui/Skeleton';
import {
  fetchNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationLink,
} from '@/features/notifications/api';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import { tone } from '@/lib/tones';
import type { ToneName } from '@/lib/tones';
import type { Notification } from '@/types/api';

/**
 * What the office has been told: approvals, rejections, deletions with their reasons, restores.
 *
 * <p>The rows have been written since Phase 1 and nothing ever read them, which meant an admin was
 * notified of a deletion they had no way to see. This screen and the bell are the read side.
 */
export function NotificationsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [unreadOnly, setUnreadOnly] = useState(false);

  const notifications = useQuery({
    queryKey: ['notifications', 'list', unreadOnly],
    queryFn: () => fetchNotifications(unreadOnly),
  });

  /** Both the list and the bell's count are stale after any change here. */
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });

  const markRead = useMutation({
    mutationFn: (notification: Notification) => markNotificationRead(notification.id),
    onSuccess: () => void refresh(),
  });

  const markAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => void refresh(),
  });

  const items = notifications.data?.items ?? [];
  const unreadShowing = items.filter((item) => !item.read).length;
  const error = notifications.error ?? markRead.error ?? markAll.error;

  /**
   * Opening a notification marks it read and follows it to its subject, if it has one. Doing both
   * from one click is the point — a notification you have acted on should not still be waiting.
   */
  const open = (notification: Notification) => {
    if (!notification.read) markRead.mutate(notification);

    const destination = notificationLink(notification.entityRef);
    if (destination) navigate(destination);
  };

  return (
    <AppShell
      title="Notifications"
      subtitle="Approvals, deletions and restores — everything the system has told you"
      actions={
        unreadShowing > 0 ? (
          <Button variant="secondary" loading={markAll.isPending} onClick={() => markAll.mutate()}>
            Mark all as read
          </Button>
        ) : undefined
      }
    >
      <div className="mb-5 inline-flex flex-wrap gap-1 rounded-lg bg-surface-sunken p-1 ring-1 ring-line">
        {[
          { value: false, label: 'All' },
          { value: true, label: 'Unread' },
        ].map((option) => (
          <button
            key={option.label}
            type="button"
            onClick={() => setUnreadOnly(option.value)}
            className={`rounded-md px-4 py-2 text-sm font-semibold transition-colors
              duration-[--duration-base] ease-[--ease-settle] ${
                unreadOnly === option.value
                  ? 'bg-brand text-on-brand shadow-card'
                  : 'text-slate-600 hover:bg-navy-50/70 hover:text-navy-700'
              }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      {error && <Alert tone="error">{toApiError(error).message}</Alert>}

      {notifications.isPending ? (
        <SkeletonRows count={4} label="Loading notifications" />
      ) : items.length === 0 ? (
        <EmptyState unreadOnly={unreadOnly} />
      ) : (
        <ul className="stagger space-y-2">
          {items.map((notification) => (
            <li key={notification.id}>
              <NotificationRow notification={notification} onOpen={() => open(notification)} />
            </li>
          ))}
        </ul>
      )}
    </AppShell>
  );
}

/**
 * One notification.
 *
 * <p>A button rather than a link even when it has a destination: opening it also marks it read, so
 * the click is an action with a navigation attached rather than plain navigation.
 */
function NotificationRow({
  notification,
  onOpen,
}: {
  notification: Notification;
  onOpen: () => void;
}) {
  const { chip, edge } = tone(toneFor(notification.type));

  return (
    <button
      type="button"
      onClick={onOpen}
      className={`group relative flex w-full gap-4 overflow-hidden rounded-xl border border-line
        bg-surface p-4 text-left shadow-card outline-none transition-all duration-[--duration-base]
        ease-[--ease-settle] hover:-translate-y-0.5 hover:border-navy-300 hover:shadow-lifted
        focus-visible:ring-2 focus-visible:ring-navy-300 ${notification.read ? 'opacity-75' : ''}`}
    >
      {/* Unread carries a coloured edge as well as the dot, so the state survives a greyscale print
          and does not depend on noticing one small circle. */}
      {!notification.read && <span aria-hidden className={`absolute inset-y-0 left-0 w-1 ${edge}`} />}

      <span className={`mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${chip}`}>
        <Glyph type={notification.type} />
      </span>

      <span className="min-w-0 flex-1">
        <span className="flex flex-wrap items-baseline gap-x-2">
          <span className={`text-sm ${notification.read ? 'font-medium' : 'font-semibold'} text-slate-900`}>
            {notification.title}
          </span>
          {!notification.read && (
            <span className="rounded-full bg-navy-50 px-2 py-0.5 text-[11px] font-semibold text-navy-700">
              New
            </span>
          )}
        </span>
        {notification.body && (
          <span className="mt-0.5 block text-sm text-slate-600">{notification.body}</span>
        )}
        <span className="mt-1 block text-xs text-slate-400">
          {formatDateTime(notification.createdAt)}
        </span>
      </span>
    </button>
  );
}

function EmptyState({ unreadOnly }: { unreadOnly: boolean }) {
  return (
    <div
      className="animate-rise flex flex-col items-center rounded-xl border border-dashed
        border-line-strong bg-surface px-6 py-12 text-center"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-10 w-10 text-slate-300"
      >
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>
      <p className="mt-3 text-sm text-slate-500">
        {unreadOnly ? 'Nothing unread. You are up to date.' : 'No notifications yet.'}
      </p>
    </div>
  );
}

/**
 * Colour by what happened, borrowed from the shared palette rather than chosen here: a rejection and
 * a deletion read as the same kind of event wherever they appear.
 */
function toneFor(type: string): ToneName {
  if (type.includes('rejected') || type.includes('deleted') || type.includes('disabled')) return 'rose';
  if (type.includes('approved') || type.includes('restored')) return 'emerald';
  if (type.includes('submitted')) return 'gold';
  return 'navy';
}

function Glyph({ type }: { type: string }) {
  const paths =
    type.includes('file') || type.includes('deleted') || type.includes('restored')
      ? ['M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z', 'M14 2v6h6']
      : ['M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2', 'M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z'];

  return (
    <svg
      aria-hidden
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-4.5 w-4.5"
    >
      {paths.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}
