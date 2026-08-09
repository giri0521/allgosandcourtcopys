import type { ReactNode } from 'react';

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
        <div className="mb-6 text-center">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-xl bg-navy-600 text-lg font-bold text-white">
            AG
          </div>
          <h1 className="text-lg font-bold tracking-tight text-navy-800">ALLGOSANDCOURTCOPYS</h1>
          <p className="text-xs uppercase tracking-wide text-slate-500">
            Document Management System
          </p>
        </div>

        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-7">
          <h2 className="text-xl font-semibold text-slate-900">{title}</h2>
          {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
          <div className="mt-5">{children}</div>
        </div>

        {footer && <div className="mt-5 text-center text-sm text-slate-600">{footer}</div>}
      </div>
    </main>
  );
}
