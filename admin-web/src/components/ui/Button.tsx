import type { ButtonHTMLAttributes } from 'react';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  loading?: boolean;
  fullWidth?: boolean;
}

/*
 * The filled variants use the role tokens rather than a palette step. A step means one thing per
 * theme, and in dark the navy and red ramps light up so that `text-navy-700` stays readable — which
 * is right for text and ruinous for a button, whose white label would end up on a pale blue field.
 * `bg-brand` and `bg-danger` are set per theme instead, and stay dark enough to carry white in both.
 */
const variants = {
  primary:
    'bg-brand text-on-brand shadow-card hover:bg-brand-hover hover:shadow-lifted disabled:bg-brand-muted disabled:shadow-none',
  secondary:
    'bg-surface text-navy-700 border border-navy-300 hover:bg-navy-50 hover:border-navy-400 disabled:text-slate-400 disabled:border-slate-200',
  ghost: 'text-navy-600 hover:bg-navy-50 disabled:text-slate-400',
  /** For the one action that removes something; never the default on a screen. */
  danger:
    'bg-danger text-on-danger shadow-card hover:bg-danger-hover hover:shadow-lifted disabled:bg-danger-muted disabled:shadow-none',
};

/**
 * The button, and with it most of how the application feels.
 *
 * <p>Three deliberate touches: it lifts a little on hover, presses *down* on click
 * (`active:scale-[0.97]`), and its label stays put while loading rather than being replaced by a
 * spinner — the width never jumps, so a row of buttons does not reflow mid-click.
 *
 * <p>The press is the important one. It is the only feedback that arrives before the server
 * answers, and on a slow connection it is the difference between "I clicked it" and "did I click
 * it?".
 */
export function Button({
  variant = 'primary',
  loading = false,
  fullWidth = false,
  disabled,
  children,
  className = '',
  ...props
}: Props) {
  return (
    <button
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`group inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold
        transition-all duration-[--duration-quick] ease-[--ease-settle]
        focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-300 focus-visible:ring-offset-1
        active:scale-[0.97] disabled:cursor-not-allowed disabled:active:scale-100
        ${variants[variant]} ${fullWidth ? 'w-full' : ''} ${className}`}
      {...props}
    >
      {loading && (
        <span
          aria-hidden
          className="h-4 w-4 shrink-0 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
