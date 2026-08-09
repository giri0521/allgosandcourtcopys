import type { ButtonHTMLAttributes } from 'react';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost';
  loading?: boolean;
  fullWidth?: boolean;
}

const variants = {
  primary: 'bg-navy-600 text-white hover:bg-navy-700 disabled:bg-navy-300',
  secondary: 'bg-white text-navy-700 border border-navy-300 hover:bg-navy-50 disabled:text-slate-400',
  ghost: 'text-navy-600 hover:bg-navy-50 disabled:text-slate-400',
};

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
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold
        transition focus:outline-none focus:ring-2 focus:ring-navy-300 disabled:cursor-not-allowed
        ${variants[variant]} ${fullWidth ? 'w-full' : ''} ${className}`}
      {...props}
    >
      {loading && (
        <span
          aria-hidden
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
