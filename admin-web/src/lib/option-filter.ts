export interface ComboboxOption {
  value: string;
  label: string;
  /** Indents the option, for a list that represents a tree. */
  depth?: number;
  /** Secondary line — a department's code, a folder's category. Searched as well as shown. */
  hint?: string;
}

/**
 * Splits a query into the words that all have to match.
 *
 * <p>Every department in this system is named "Department of Something and Something Else", so a
 * plain substring match on the whole query is close to useless: nobody types the middle of a name.
 * Typing "health family" has to find "Department of Health and Family Welfare", and it only does if
 * the words are matched independently and in any order.
 */
function tokenise(query: string): string[] {
  return query.toLowerCase().split(/\s+/).filter(Boolean);
}

/**
 * The options whose label or hint contains every word typed.
 *
 * <p>Lives away from the component so it can be tested as what it is — a pure function over strings
 * — rather than through a rendered listbox.
 */
export function filterOptions(options: ComboboxOption[], query: string): ComboboxOption[] {
  const tokens = tokenise(query);
  if (tokens.length === 0) return options;

  return options.filter((option) => {
    const haystack = `${option.label} ${option.hint ?? ''}`.toLowerCase();
    return tokens.every((token) => haystack.includes(token));
  });
}
