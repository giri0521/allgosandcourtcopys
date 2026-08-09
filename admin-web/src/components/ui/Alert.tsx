import type { ReactNode } from 'react';

const tones = {
  error: 'border-red-200 bg-red-50 text-red-800',
  info: 'border-navy-200 bg-navy-50 text-navy-800',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
};

export function Alert({
  tone = 'info',
  children,
}: {
  tone?: keyof typeof tones;
  children: ReactNode;
}) {
  return (
    <div role="alert" className={`rounded-lg border px-3.5 py-3 text-sm ${tones[tone]}`}>
      {children}
    </div>
  );
}
