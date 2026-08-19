import type { ReactNode } from 'react';
import { ThemeToggle } from '@/components/ui/ThemeToggle';

/**
 * The frame around every screen a signed-out visitor sees.
 *
 * <p>The seal and the card arrive in sequence rather than together — a beat apart, so the page
 * composes itself instead of appearing. It is the first impression the office gets of the system,
 * and it costs nothing.
 */
export function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <main className="relative flex min-h-screen items-center justify-center px-4 py-10">
      {/*
        Signed out is where the theme matters most and where there is no chrome to put a control
        in — somebody signing in at night should not have to get past a white page first to reach
        the setting that would have prevented it. Pinned to the corner, out of the card's way.
      */}
      <div className="absolute right-4 top-4">
        <ThemeToggle />
      </div>

      <div className="w-full max-w-md">
        <div className="animate-rise mb-6 text-center">
          {/* The source artwork sits on a square canvas with a thin frame; clipping to a circle
              trims those corners so only the seal itself shows. */}
          <img
            src="/logo.webp"
            alt="All GOs and Court Copies"
            width={96}
            height={96}
            className="mx-auto mb-3 h-20 w-20 rounded-full object-cover shadow-card sm:h-24 sm:w-24"
          />
          <h1 className="text-lg font-bold tracking-tight text-navy-800">ALLGOSANDCOURTCOPYS</h1>
          {/* A short gold rule under the name, the way a seal is underlined on a letterhead. */}
          <span aria-hidden className="mx-auto mt-1.5 block h-0.5 w-12 rounded-full bg-gold-400" />
          <p className="mt-2 text-xs uppercase tracking-wide text-slate-500">
            Document Management System
          </p>
        </div>

        <div
          className="animate-rise rounded-2xl border border-line bg-surface p-6 shadow-lifted sm:p-7"
          style={{ animationDelay: '70ms' }}
        >
          <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-5">{children}</div>
        </div>

        {footer && <div className="mt-5 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </main>
  );
}
