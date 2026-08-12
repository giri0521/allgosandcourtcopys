import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchUnreadCount } from '@/features/notifications/api';

/**
 * The unread badge, on every signed-in screen.
 *
 * <p>Polled on an interval rather than pushed: the events that produce a notification — an approval,
 * a deletion — happen minutes apart at most, and a websocket for that would be a standing connection
 * per user in exchange for nothing anybody would notice. The count is one indexed query.
 *
 * <p>A failure is silent. The bell is ambient, and an error banner over the header because a
 * background poll missed would be worse than a stale number.
 */
export function NotificationBell() {
  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: fetchUnreadCount,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  });

  const count = unread.data ?? 0;
  const label = count === 0 ? 'Notifications' : `Notifications, ${count} unread`;

  return (
    <Link
      to="/notifications"
      aria-label={label}
      title={label}
      className="relative rounded-md p-2 text-slate-600 outline-none transition-colors
        duration-[--duration-base] ease-[--ease-settle] hover:bg-navy-50 hover:text-navy-700
        focus-visible:ring-2 focus-visible:ring-navy-300"
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.8}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="h-5 w-5"
      >
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
        <path d="M13.73 21a2 2 0 0 1-3.46 0" />
      </svg>

      {count > 0 && (
        // The number is the signal; the colour only reinforces it, so this still reads without it.
        <span
          className="animate-pop absolute -right-0.5 -top-0.5 flex h-4.5 min-w-4.5 items-center
            justify-center rounded-full bg-red-600 px-1 text-[10px] font-bold tabular-nums text-white"
        >
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}
