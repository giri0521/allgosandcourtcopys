import { useState } from 'react';
import type { TextareaHTMLAttributes } from 'react';
import { TextAreaField } from '@/components/ui/Field';
import {
  DICTATION_LANGUAGES,
  appendTranscript,
  dictationSupported,
  useDictation,
  type DictationLanguage,
} from '@/lib/dictation';

/**
 * A text area that can also be dictated into.
 *
 * <p>Typing is unchanged — the microphone is an alternative to it, never a mode. What is recognised
 * is added to what is already there, so somebody can type a paragraph, dictate the next and go back
 * to typing without losing either.
 *
 * <p>On a browser with no speech recognition the control is simply a text area: the button is not
 * rendered at all rather than shown disabled, because a disabled button invites a question nobody
 * can answer from the screen.
 */
export function DictationField({
  label,
  value,
  onValueChange,
  hint,
  error,
  ...props
}: {
  label: string;
  value: string;
  onValueChange: (value: string) => void;
  hint?: string;
  error?: string;
} & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'value' | 'onChange'>) {
  const [lang, setLang] = useState<DictationLanguage>('en-IN');

  const dictation = useDictation({
    lang,
    onText: (spoken) => onValueChange(appendTranscript(value, spoken)),
  });

  return (
    <div>
      <TextAreaField
        label={label}
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        hint={hint}
        error={error}
        {...props}
      />

      {dictationSupported && (
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => (dictation.listening ? dictation.stop() : dictation.start())}
            aria-pressed={dictation.listening}
            className={`inline-flex items-center gap-2 rounded-lg px-2.5 py-1.5 text-[0.8125rem]
              font-semibold outline-none transition-all duration-[--duration-quick]
              ease-[--ease-settle] focus-visible:ring-2 focus-visible:ring-navy-300
              ${
                dictation.listening
                  ? 'bg-red-50 text-red-700 ring-1 ring-inset ring-red-500/20'
                  : 'text-navy-700 hover:bg-navy-50'
              }`}
          >
            <svg
              aria-hidden
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.8}
              strokeLinecap="round"
              strokeLinejoin="round"
              className={`h-4 w-4 ${dictation.listening ? 'animate-pulse' : ''}`}
            >
              <path d="M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3z" />
              <path d="M19 10v1a7 7 0 0 1-14 0v-1" />
              <path d="M12 18v4" />
            </svg>
            {dictation.listening ? 'Stop dictating' : 'Dictate'}
          </button>

          {/* Beside the button rather than in a settings screen: the language of the next sentence
              is a decision made while writing, and an office writing in both switches often. */}
          <label className="sr-only" htmlFor={`${label}-dictation-language`}>
            Dictation language for {label}
          </label>
          <select
            id={`${label}-dictation-language`}
            value={lang}
            onChange={(event) => setLang(event.target.value as DictationLanguage)}
            disabled={dictation.listening}
            className="rounded-lg border border-line bg-surface-sunken px-2 py-1 text-xs
              text-slate-600 outline-none focus-visible:ring-2 focus-visible:ring-navy-300
              disabled:opacity-60"
          >
            {DICTATION_LANGUAGES.map((option) => (
              <option key={option.code} value={option.code}>
                {option.label}
              </option>
            ))}
          </select>

          {dictation.listening && (
            <span className="text-xs text-slate-500" role="status">
              {dictation.interim ? `“${dictation.interim}”` : 'Listening…'}
            </span>
          )}

          {dictation.error && (
            <span className="text-xs font-medium text-red-600" role="alert">
              {dictation.error}
            </span>
          )}
        </div>
      )}
    </div>
  );
}
