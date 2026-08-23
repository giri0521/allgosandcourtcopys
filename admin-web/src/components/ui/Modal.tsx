import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

/**
 * A small dialog for decisions that need a second thought — rejecting a registration, deleting a
 * document, replacing one.
 *
 * <p>Escape and a click on the backdrop both cancel, and focus moves inside on open so the dialog
 * is usable from the keyboard alone. Focus is also *returned* on close, to whatever was focused
 * before — without that, dismissing a dialog drops a keyboard user back at the top of the page.
 *
 * <p>Focus is <b>trapped</b> while it is open: Tab past the last control wraps to the first, and
 * Shift+Tab before the first wraps to the last. {@code aria-modal} already tells a screen reader to
 * ignore the page behind, but it does nothing for a sighted keyboard user — without the trap, Tab
 * walks out of the dialog and into content the dialog is supposed to be blocking, and the next
 * Enter presses a button nobody can see.
 *
 * <p>It fades its backdrop and rises slightly as it opens, which is what makes it read as a layer
 * over the page rather than a repaint of it. Background scrolling is locked while it is open.
 */
export function Modal({
  open,
  title,
  description,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const panelRef = useRef<HTMLDivElement>(null);
  const previouslyFocused = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!open) return;

    previouslyFocused.current = document.activeElement as HTMLElement | null;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key !== 'Tab') return;

      // Queried on each press rather than cached: a dialog's controls change as it is used — the
      // reject dialog's submit button is disabled until a reason is typed, and a disabled button
      // is not tabbable.
      const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable || focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;

      // Also catches focus having escaped already — anything outside the panel is sent back in.
      if (event.shiftKey && (active === first || !panelRef.current?.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !panelRef.current?.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener('keydown', onKeyDown);

    // The page behind must not scroll under the dialog.
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    panelRef.current?.querySelector<HTMLElement>('input, textarea, button')?.focus();

    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
      previouslyFocused.current?.focus?.();
    };
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div
        className="animate-fade absolute inset-0 bg-scrim backdrop-blur-[2px]"
        onClick={onClose}
        aria-hidden
      />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="animate-pop relative w-full max-w-md rounded-2xl border border-line bg-surface
          p-6 shadow-dialog"
      >
        <h2 className="pr-8 text-lg font-semibold text-slate-900">{title}</h2>
        {description && <p className="mt-1 pr-8 text-sm leading-relaxed text-slate-500">{description}</p>}
        <div className="mt-4 space-y-4">{children}</div>
        {/* Last in the DOM on purpose: Modal auto-focuses the first input/textarea/button inside
            the panel on open, and that should land on the dialog's real first control — typing a
            reason, say — rather than on this. Absolute positioning keeps it pinned top-right
            regardless of where it sits in the markup. */}
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="absolute right-3 top-3 rounded-md p-1.5 text-slate-400 outline-none
            transition-colors duration-[--duration-quick] ease-[--ease-settle] hover:bg-navy-50
            hover:text-slate-600 focus-visible:ring-2 focus-visible:ring-navy-300"
        >
          <svg
            aria-hidden
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            className="h-4 w-4"
          >
            <path d="M18 6 6 18" />
            <path d="M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  );
}
