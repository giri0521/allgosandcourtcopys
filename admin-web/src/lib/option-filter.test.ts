import { describe, expect, it } from 'vitest';
import { filterOptions } from '@/lib/option-filter';
import type { ComboboxOption } from '@/lib/option-filter';

/** A slice of the real department list, which is what makes the matching rules necessary. */
const DEPARTMENTS: ComboboxOption[] = [
  { value: '1', label: 'Department of Agriculture and Farmers Welfare', hint: 'AGR' },
  { value: '2', label: 'Department of Health and Family Welfare', hint: 'HFW' },
  { value: '3', label: 'Department of Higher Education', hint: 'HED' },
  { value: '4', label: 'Department of Highways and Minor Ports', hint: 'HMP' },
  {
    value: '5',
    label: 'Department of Backward Classes, Most Backward Classes and Minorities Welfare',
    hint: 'BCW',
  },
];

const labels = (options: ComboboxOption[]) => options.map((option) => option.label);

describe('filterOptions', () => {
  it('returns everything for an empty or blank query', () => {
    expect(filterOptions(DEPARTMENTS, '')).toHaveLength(5);
    expect(filterOptions(DEPARTMENTS, '   ')).toHaveLength(5);
  });

  it('matches anywhere in the name, not just the start', () => {
    // The whole reason this exists: every option begins "Department of", so a native select's
    // type-ahead — which only matches from the beginning — finds nothing useful.
    expect(labels(filterOptions(DEPARTMENTS, 'health'))).toEqual([
      'Department of Health and Family Welfare',
    ]);
  });

  it('ignores case', () => {
    expect(filterOptions(DEPARTMENTS, 'HEALTH')).toHaveLength(1);
    expect(filterOptions(DEPARTMENTS, 'HeAlTh')).toHaveLength(1);
  });

  it('requires every word, in any order', () => {
    expect(labels(filterOptions(DEPARTMENTS, 'health family'))).toEqual([
      'Department of Health and Family Welfare',
    ]);
    // Order must not matter — people type the distinctive word first.
    expect(labels(filterOptions(DEPARTMENTS, 'family health'))).toEqual([
      'Department of Health and Family Welfare',
    ]);
  });

  it('narrows as more is typed', () => {
    // Health, Higher, Highways — Agriculture and Backward Classes have no 'h' at all.
    expect(filterOptions(DEPARTMENTS, 'h')).toHaveLength(3);
    expect(filterOptions(DEPARTMENTS, 'high')).toHaveLength(2); // Higher Education, Highways
    expect(filterOptions(DEPARTMENTS, 'highw')).toHaveLength(1);
  });

  it('searches the hint too, so a known code finds its department', () => {
    expect(labels(filterOptions(DEPARTMENTS, 'BCW'))).toEqual([
      'Department of Backward Classes, Most Backward Classes and Minorities Welfare',
    ]);
  });

  it('collapses extra whitespace rather than failing to match', () => {
    expect(filterOptions(DEPARTMENTS, '  health   family  ')).toHaveLength(1);
  });

  it('returns nothing when nothing matches', () => {
    expect(filterOptions(DEPARTMENTS, 'fisheries welfare')).toEqual([]);
  });

  it('copes with options that have no hint', () => {
    const options: ComboboxOption[] = [{ value: '1', label: 'Circulars 2026' }];
    expect(filterOptions(options, 'circ')).toHaveLength(1);
    expect(filterOptions(options, 'undefined')).toHaveLength(0);
  });
});
