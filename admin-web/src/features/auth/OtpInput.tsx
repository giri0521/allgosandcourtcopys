import { useRef } from 'react';
import type { ChangeEvent, ClipboardEvent, KeyboardEvent } from 'react';

interface Props {
  value: string;
  onChange: (value: string) => void;
  length?: number;
  disabled?: boolean;
  autoFocus?: boolean;
}

/**
 * The six OTP boxes from the wireframes: typing advances, backspace on an empty box steps back, and
 * pasting a whole code fills the row — which is what people actually do with an SMS code.
 */
export function OtpInput({
  value,
  onChange,
  length = 6,
  disabled = false,
  autoFocus = false,
}: Props) {
  const inputs = useRef<Array<HTMLInputElement | null>>([]);

  const focusBox = (index: number) => {
    inputs.current[Math.max(0, Math.min(index, length - 1))]?.focus();
  };

  const setCharAt = (index: number, char: string) => {
    const next = value.padEnd(length, ' ').split('');
    next[index] = char || ' ';
    onChange(next.join('').replace(/ /g, '').slice(0, length));
  };

  const handleChange = (index: number) => (event: ChangeEvent<HTMLInputElement>) => {
    const digit = event.target.value.replace(/\D/g, '').slice(-1);
    if (!digit) return;

    // Typing into the row appends in order, which keeps the value contiguous.
    const next = (value + digit).slice(0, length);
    onChange(index >= value.length ? next : replaceAt(value, index, digit, length));
    focusBox(Math.max(index, value.length) + 1);
  };

  const handleKeyDown = (index: number) => (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Backspace') {
      event.preventDefault();
      if (value[index]) {
        setCharAt(index, '');
        focusBox(index);
      } else {
        onChange(value.slice(0, -1));
        focusBox(index - 1);
      }
    }
    if (event.key === 'ArrowLeft') focusBox(index - 1);
    if (event.key === 'ArrowRight') focusBox(index + 1);
  };

  const handlePaste = (event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault();
    const pasted = event.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
    onChange(pasted);
    focusBox(pasted.length);
  };

  return (
    <div className="flex justify-between gap-2" onPaste={handlePaste}>
      {Array.from({ length }, (_, index) => (
        <input
          key={index}
          ref={(element) => {
            inputs.current[index] = element;
          }}
          value={value[index] ?? ''}
          onChange={handleChange(index)}
          onKeyDown={handleKeyDown(index)}
          disabled={disabled}
          autoFocus={autoFocus && index === 0}
          inputMode="numeric"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={1}
          aria-label={`OTP digit ${index + 1}`}
          className="h-12 w-full rounded-lg border border-slate-300 bg-white text-center text-lg
            font-semibold text-slate-900 outline-none transition focus:border-navy-500
            focus:ring-2 focus:ring-navy-200 disabled:bg-slate-100"
        />
      ))}
    </div>
  );
}

function replaceAt(value: string, index: number, char: string, length: number): string {
  const chars = value.split('');
  chars[index] = char;
  return chars.join('').slice(0, length);
}
