import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { useId } from 'react';

export interface BaseProps {
  label: string;
  error?: string;
  hint?: string;
}

/**
 * The look of every control on every form: border, padding, focus ring, disabled state.
 *
 * <p>Exported so a control that is not a bare input — the combobox, which is an input wearing a
 * listbox — can look identical to one without copying six utilities and drifting from them.
 */
export const controlClass =
  'w-full rounded-lg border border-line-strong bg-surface px-3 py-2.5 text-slate-900 outline-none ' +
  'transition focus:border-navy-500 focus:ring-2 focus:ring-navy-200 disabled:bg-slate-100';

/** Label, hint and error around a control. Exported for the same reason as `controlClass`. */
export function FieldWrapper({
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
    <FieldWrapper label={label} error={error} hint={hint} id={id}>
      <input
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} ${error ? 'border-red-400' : ''}`}
        {...props}
      />
    </FieldWrapper>
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
    <FieldWrapper label={label} error={error} hint={hint} id={id}>
      <textarea
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} resize-y ${error ? 'border-red-400' : ''}`}
        {...props}
      />
    </FieldWrapper>
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
    <FieldWrapper label={label} error={error} hint={hint} id={id}>
      <select
        id={id}
        aria-invalid={Boolean(error)}
        className={`${controlClass} ${error ? 'border-red-400' : ''}`}
        {...props}
      >
        {children}
      </select>
    </FieldWrapper>
  );
}
