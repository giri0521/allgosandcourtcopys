/**
 * Placeholders shaped like the content that is coming.
 *
 * <p>They exist because "Loading…" makes a screen appear empty and then jump; a skeleton holds the
 * layout still, so the arriving content lands where the eye is already looking. The shimmer is what
 * distinguishes waiting from broken.
 *
 * <p>Every skeleton is {@code aria-hidden} with a polite live message alongside: a screen reader
 * should hear "Loading departments", not a description of grey rectangles.
 */
export function SkeletonCards({ count = 6, label }: { count?: number; label: string }) {
  return (
    <>
      <span className="sr-only" role="status">
        {label}
      </span>
      <div aria-hidden className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: count }).map((_, index) => (
          <div key={index} className="rounded-xl border border-line bg-surface p-5">
            <div className="skeleton h-4 w-2/3 rounded" />
            <div className="skeleton mt-3 h-3 w-1/3 rounded" />
            <div className="skeleton mt-5 h-3 w-1/2 rounded" />
          </div>
        ))}
      </div>
    </>
  );
}

export function SkeletonRows({ count = 4, label }: { count?: number; label: string }) {
  return (
    <>
      <span className="sr-only" role="status">
        {label}
      </span>
      <div aria-hidden className="overflow-hidden rounded-xl border border-line bg-surface">
        {Array.from({ length: count }).map((_, index) => (
          <div
            key={index}
            className="flex items-center gap-4 border-b border-slate-100 px-5 py-4 last:border-b-0"
          >
            <div className="skeleton h-9 w-9 shrink-0 rounded-lg" />
            <div className="min-w-0 flex-1">
              <div className="skeleton h-3.5 w-1/3 rounded" />
              <div className="skeleton mt-2 h-3 w-1/5 rounded" />
            </div>
            <div className="skeleton hidden h-3 w-24 rounded sm:block" />
          </div>
        ))}
      </div>
    </>
  );
}
