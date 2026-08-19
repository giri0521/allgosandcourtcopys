import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { controlClass, FieldWrapper } from '@/components/ui/Field';
import { filterOptions } from '@/lib/option-filter';
import type { ComboboxOption } from '@/lib/option-filter';

export type { ComboboxOption };

/**
 * A select you can type into.
 *
 * <p>Built rather than borrowed because the native control cannot do it: `<select>` has no filtering,
 * and forty-three departments named almost identically is a list you scroll past the one you wanted.
 * The browser's own type-ahead only matches the *start* of an option, so typing "health" in a list
 * where every entry begins "Department of" matches nothing at all.
 *
 * <p>It follows the ARIA combobox pattern rather than approximating it: the input carries
 * `role="combobox"` with `aria-expanded` and `aria-activedescendant`, the list is a real `listbox` of
 * `option`s, and the arrow keys move the active option without moving focus off the input — which is
 * what lets a screen reader announce each candidate as you go while you keep typing.
 *
 * <p>Focus stays on the input throughout. Moving it into the list would break typing, which is the
 * entire point of the control.
 */
export function Combobox({
  label,
  options,
  value,
  onChange,
  placeholder = 'Type to search…',
  emptyMessage = 'Nothing matches',
  disabled = false,
  loading = false,
  hint,
  error,
}: {
  label: string;
  options: ComboboxOption[];
  value: string | null;
  onChange: (value: string | null) => void;
  placeholder?: string;
  emptyMessage?: string;
  disabled?: boolean;
  loading?: boolean;
  hint?: string;
  error?: string;
}) {
  const id = useId();
  const listId = `${id}-list`;

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);

  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  const selected = options.find((option) => option.value === value) ?? null;
  const matches = useMemo(() => filterOptions(options, query), [options, query]);

  /*
   * The input shows the query while open and the chosen label while closed. Opening clears the box
   * and demotes the current choice to the placeholder, so the first keystroke filters the whole list
   * instead of appending to a name nobody wants to edit.
   */
  const shown = open ? query : (selected?.label ?? '');

  const close = () => {
    setOpen(false);
    setQuery('');
    setActive(0);
  };

  const commit = (option: ComboboxOption) => {
    onChange(option.value);
    close();
  };

  /* A click anywhere else is a dismissal. Pointerdown rather than click, so it fires before focus
     moves and the list is already gone by the time the other control lights up. */
  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) close();
    };
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [open]);

  /* Keep the active option in view — it is reachable by keyboard, so it has to be visible. */
  useEffect(() => {
    if (!open) return;
    listRef.current?.querySelector<HTMLElement>('[data-active="true"]')?.scrollIntoView({
      block: 'nearest',
    });
  }, [open, active, query]);

  const onKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (disabled) return;

    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) {
        setOpen(true);
        setActive(0);
        return;
      }
      if (matches.length === 0) return;
      const step = event.key === 'ArrowDown' ? 1 : -1;
      // Wraps, so holding one arrow key cannot dead-end at either edge.
      setActive((current) => (current + step + matches.length) % matches.length);
      return;
    }

    if (event.key === 'Enter') {
      if (!open) return;
      // Swallowed so the surrounding form does not submit on the keystroke that picks an option.
      event.preventDefault();
      const option = matches[active];
      if (option) commit(option);
      return;
    }

    if (event.key === 'Escape') {
      if (!open) return;
      event.preventDefault();
      event.stopPropagation(); // the dialog also listens for Escape, and would close behind us
      close();
      return;
    }

    if (event.key === 'Tab') {
      close(); // leaving without choosing keeps whatever was already chosen
      return;
    }

    if (event.key === 'Home' || event.key === 'End') {
      if (!open || matches.length === 0) return;
      event.preventDefault();
      setActive(event.key === 'Home' ? 0 : matches.length - 1);
    }
  };

  return (
    <FieldWrapper label={label} error={error} hint={hint} id={id}>
      <div ref={rootRef} className="relative">
        <input
          ref={inputRef}
          id={id}
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          disabled={disabled}
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-invalid={Boolean(error) || undefined}
          aria-activedescendant={open && matches[active] ? `${id}-${matches[active].value}` : undefined}
          value={shown}
          placeholder={loading ? 'Loading…' : open && selected ? selected.label : placeholder}
          onChange={(event) => {
            setQuery(event.target.value);
            setOpen(true);
            // Filtering shortens the list, so an index from the longer one would point past its end
            // — and the first match is what the next Enter should take anyway.
            setActive(0);
          }}
          onFocus={() => setOpen(true)}
          /*
           * Focus is not enough on its own. Choosing an option deliberately leaves focus on the
           * input, so the next click on it changes nothing and `onFocus` never fires again — the
           * list would stay shut and the control would look broken to anyone wanting to change
           * their mind.
           */
          onClick={() => setOpen(true)}
          onKeyDown={onKeyDown}
          className={`${controlClass} pr-16 ${error ? 'border-red-400' : ''}`}
        />

        <div className="absolute inset-y-0 right-2 flex items-center gap-0.5">
          {selected && !disabled && (
            <button
              type="button"
              // A chosen department is easy to change and, without this, impossible to un-choose.
              aria-label={`Clear ${label.toLowerCase()}`}
              onClick={() => {
                onChange(null);
                setQuery('');
                setActive(0);
                inputRef.current?.focus();
              }}
              className="rounded p-1 text-slate-400 outline-none transition-colors
                duration-[--duration-quick] hover:text-navy-700 focus-visible:ring-2
                focus-visible:ring-navy-300"
            >
              <svg
                aria-hidden
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                strokeLinecap="round"
                className="h-3.5 w-3.5"
              >
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          )}
          <svg
            aria-hidden
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={2}
            strokeLinecap="round"
            strokeLinejoin="round"
            className={`h-4 w-4 text-slate-400 transition-transform duration-[--duration-base]
              ease-[--ease-settle] ${open ? '-rotate-180' : ''}`}
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        </div>

        {/*
          Always rendered, so `aria-controls` never points at a missing element — assistive
          technology resolves that reference whether or not the list is on screen. Hidden from both
          the eye and the accessibility tree while closed.
        */}
        <ul
          ref={listRef}
          id={listId}
          role="listbox"
          aria-label={label}
          hidden={!open}
          className="animate-fade absolute z-50 mt-1 max-h-60 w-full overflow-y-auto rounded-lg border
            border-line bg-surface py-1 shadow-lifted"
        >
          {matches.length === 0 && (
            <li className="px-3 py-2 text-sm text-slate-500">
              {loading ? 'Loading…' : `${emptyMessage}${query ? ` “${query}”` : ''}`}
            </li>
          )}

          {matches.map((option, index) => {
            const isActive = index === active;
            const isSelected = option.value === value;
            return (
              <li
                key={option.value}
                id={`${id}-${option.value}`}
                role="option"
                aria-selected={isSelected}
                data-active={isActive}
                /* Pointerdown, not click: the dismissal listener above runs on pointerdown, and a
                   click handler would fire after the list had already been torn down. */
                onPointerDown={(event) => {
                  event.preventDefault(); // keeps focus on the input
                  commit(option);
                }}
                onPointerEnter={() => setActive(index)}
                className={`cursor-pointer px-3 py-2 text-sm transition-colors
                  duration-[--duration-quick] ${
                    isActive ? 'bg-navy-50 text-navy-800' : 'text-slate-700'
                  }`}
                style={option.depth ? { paddingLeft: `${0.75 + option.depth * 1}rem` } : undefined}
              >
                <span className="flex items-center gap-2">
                  {option.depth ? (
                    <span aria-hidden className="text-slate-300">
                      └
                    </span>
                  ) : null}
                  {/* Wraps rather than truncates. These names run to eighty characters and differ
                      only near the end — "Most Backward Classes and Minorities Welfare" — so an
                      ellipsis would hide the very part that tells them apart. */}
                  <span className="min-w-0 flex-1">{option.label}</span>
                  {isSelected && (
                    <svg
                      aria-hidden
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth={2.5}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      className="h-3.5 w-3.5 shrink-0 text-navy-600"
                    >
                      <path d="M20 6 9 17l-5-5" />
                    </svg>
                  )}
                </span>
                {option.hint && (
                  <span className="mt-0.5 block truncate text-xs text-slate-500">{option.hint}</span>
                )}
              </li>
            );
          })}
        </ul>
      </div>
    </FieldWrapper>
  );
}
