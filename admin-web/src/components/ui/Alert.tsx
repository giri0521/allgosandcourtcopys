import type { ReactNode } from 'react';

const tones = {
  error: 'border-red-200 bg-red-50 text-red-800',
  info: 'border-navy-200 bg-navy-50 text-navy-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
};

const icons = {
  error: 'M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z',
  info: 'M12 16v-4m0-4h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  success: 'M9 12l2 2 4-4m6 2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
};

/**
 * A message about what just happened, almost always the server's own wording.
 *
 * <p>It rises into place rather than appearing, because an error that blinks into existence is easy
 * to miss — particularly when it lands below a form the user is still looking at. The icon carries
 * the tone for anyone who cannot rely on the colour alone.
 */
export function Alert({
  tone = 'info',
  children,
}: {
  tone?: keyof typeof tones;
  children: ReactNode;
}) {
  return (
    <div
      role="alert"
      className={`animate-rise flex items-start gap-2.5 rounded-lg border px-3.5 py-3 text-sm ${tones[tone]}`}
    >
      <svg
        aria-hidden
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className="mt-0.5 h-4 w-4 shrink-0"
      >
        <path d={icons[tone]} />
      </svg>
      <div className="min-w-0">{children}</div>
    </div>
  );
}
