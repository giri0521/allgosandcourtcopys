import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { useId } from 'react';

interface BaseProps {
  label: string;
  error?: string;
  hint?: string;
}

const controlClass =
  'w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-slate-900 outline-none ' +
  'transition focus:border-navy-500 focus:ring-2 focus:ring-navy-200 disabled:bg-slate-100';

function Wrapper({
  label,
  error,
  hint,
  id,
  children,
}: BaseProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-slate-500">{hint}</p>}
      {error && (
        <p role="alert" className="text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  );
}

export function TextField({
  label,
  error,
  hint,
  ...props
}: BaseProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Wrapper label={label} error={error} hint={hint} id={id}>
      <input
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} ${error ? 'border-red-400' : ''}`}
        {...props}
      />
    </Wrapper>
  );
}

export function TextAreaField({
  label,
  error,
  hint,
  ...props
}: BaseProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <Wrapper label={label} error={error} hint={hint} id={id}>
      <textarea
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} resize-y ${error ? 'border-red-400' : ''}`}
        {...props}
      />
    </Wrapper>
  );
}

export function SelectField({
  label,
  error,
  hint,
  children,
  ...props
}: BaseProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <Wrapper label={label} error={error} hint={hint} id={id}>
      <select
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} ${error ? 'border-red-400' : ''}`}
        {...props}
      >
        {children}
      </select>
    </Wrapper>
  );
}
