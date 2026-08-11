import type { ReactNode } from 'react';

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
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="animate-rise mb-6 text-center">
          {/* The source artwork sits on a square canvas with a thin frame; clipping to a circle
              trims those corners so only the seal itself shows. */}
          <img
            src="/logo.webp"
            alt="All GOs and Court Copies"
            width={96}
            height={96}
            className="mx-auto mb-3 h-20 w-20 rounded-full object-cover shadow-sm sm:h-24 sm:w-24"
          />
          <h1 className="text-lg font-bold tracking-tight text-navy-800">ALLGOSANDCOURTCOPYS</h1>
          <p className="text-xs uppercase tracking-wide text-slate-500">
            Document Management System
          </p>
        </div>

        <div
          className="animate-rise rounded-2xl border border-slate-200 bg-white p-6 shadow-sm
            shadow-slate-900/5 sm:p-7"
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
