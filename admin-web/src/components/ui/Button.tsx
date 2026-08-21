import type { ButtonHTMLAttributes } from 'react';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  /**
   * `md` is a button somebody came to the screen to press. `sm` is one of several offered per row
   * in a table, where full-size padding pushes the last action off the edge and makes a dense list
   * read as a stack of buttons with some text beside them.
   */
  size?: 'sm' | 'md';
  loading?: boolean;
  fullWidth?: boolean;
}

/* A prop rather than a className: `px-2.5` and `px-4` are the same utility family, so which one
   won would depend on their order in the generated stylesheet rather than on the call site. */
const sizes = {
  sm: 'px-2.5 py-1.5 text-[0.8125rem] gap-1.5',
  md: 'px-4 py-2.5 text-sm gap-2',
};

/*
 * The filled variants use the role tokens rather than a palette step. A step means one thing per
 * theme, and in dark the navy and red ramps light up so that `text-navy-700` stays readable — which
 * is right for text and ruinous for a button, whose white label would end up on a pale blue field.
 * `bg-brand` and `bg-danger` are set per theme instead, and stay dark enough to carry white in both.
 */
const variants = {
  /* `fill-brand` carries the face, the shadow, hover, press and disabled together — see index.css
     for why hover cannot live here. */
  primary: 'fill-brand text-on-brand hover:-translate-y-px',
  secondary:
    'bg-surface text-navy-700 border border-navy-200 shadow-card hover:bg-navy-50 hover:border-navy-300 hover:shadow-lifted hover:-translate-y-px disabled:text-slate-400 disabled:border-slate-200 disabled:shadow-none',
  /* 700 rather than 600: a ghost button's hover paints navy-50 behind its own label, and 600 on
     that tint measures 4.03:1 — below AA for text this size. 700 clears it on both grounds. */
  ghost: 'text-navy-700 hover:bg-navy-50 hover:text-navy-800 disabled:text-slate-400',
  /** For the one action that removes something; never the default on a screen. */
  danger: 'fill-danger text-on-danger hover:-translate-y-px',
};

/**
 * The button, and with it most of how the application feels.
 *
 * <p>Four deliberate touches: it lifts a pixel on hover and its shadow grows with it, it presses
 * *down* on click (`active:scale-[0.97]`, and the shadow shrinks to match), the filled variants
 * cast their own colour rather than grey, and its label stays put while loading rather than being
 * replaced by a spinner — the width never jumps, so a row of buttons does not reflow mid-click.
 *
 * <p>The press is the important one. It is the only feedback that arrives before the server
 * answers, and on a slow connection it is the difference between "I clicked it" and "did I click
 * it?".
 */
export function Button({
  variant = 'primary',
  size = 'md',
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
      className={`group inline-flex items-center justify-center rounded-lg font-semibold
        transition-all duration-[--duration-quick] ease-[--ease-settle]
        focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-300 focus-visible:ring-offset-1
        active:scale-[0.97] active:translate-y-0
        disabled:cursor-not-allowed disabled:active:scale-100 disabled:hover:translate-y-0
        ${sizes[size]} ${variants[variant]} ${fullWidth ? 'w-full' : ''} ${className}`}
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
