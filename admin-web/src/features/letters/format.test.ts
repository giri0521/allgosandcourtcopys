import { describe, expect, it } from 'vitest';
import { defaultFromBlock, formatLetterDate } from '@/features/letters/format';

describe('formatLetterDate', () => {
  it('writes the date the way the office does', () => {
    expect(formatLetterDate('2026-08-22')).toBe('22.08.2026');
  });

  /*
   * The reason this is parsed by hand rather than through Date: `new Date('2026-08-22')` is
   * midnight UTC, and in any timezone behind UTC that renders as the 21st. A letter dated a day
   * before it was written is the kind of error somebody notices in a file six months later.
   */
  it('does not shift the day, whatever the reader\'s timezone', () => {
    expect(formatLetterDate('2026-01-01')).toBe('01.01.2026');
    expect(formatLetterDate('2026-12-31')).toBe('31.12.2026');
  });

  it('has nothing to say about a letter with no date', () => {
    expect(formatLetterDate(null)).toBe('');
    expect(formatLetterDate('')).toBe('');
  });
});

describe('defaultFromBlock', () => {
  it('stacks the account into an address block', () => {
    expect(
      defaultFromBlock({
        fullName: 'Meena Rajan',
        designation: 'Section Officer',
        departmentName: 'Backward Classes Welfare',
        officeAddress: 'Chepauk, Chennai - 600 005.',
      }),
    ).toBe('Meena Rajan,\nSection Officer,\nBackward Classes Welfare,\nChepauk, Chennai - 600 005.');
  });

  it('leaves out what the account does not have, rather than printing empty lines', () => {
    expect(
      defaultFromBlock({
        fullName: 'Meena Rajan',
        designation: null,
        departmentName: '   ',
        officeAddress: null,
      }),
    ).toBe('Meena Rajan');
  });
});
