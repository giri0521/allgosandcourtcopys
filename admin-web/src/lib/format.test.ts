import { describe, expect, it } from 'vitest';
import { daysUntil } from '@/lib/format';

describe('daysUntil', () => {
  it('rounds a partial day up, so today still reads as at least one day left', () => {
    const in15Hours = new Date(Date.now() + 15 * 60 * 60 * 1000).toISOString();
    expect(daysUntil(in15Hours)).toBe(1);
  });

  it('counts a handful of full days ahead, rounded up past the last full day', () => {
    const justOverThreeDays = new Date(Date.now() + 3 * 24 * 60 * 60 * 1000 + 1000).toISOString();
    expect(daysUntil(justOverThreeDays)).toBe(4);
  });

  it('goes negative once the moment has passed', () => {
    const yesterday = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
    expect(daysUntil(yesterday)).toBeLessThan(0);
  });
});
