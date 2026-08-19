import { describe, expect, it } from 'vitest';
import { departmentIcon } from '@/lib/department-icons';
import type { DepartmentIconName } from '@/lib/department-icons';
import { departmentTone, tone } from '@/lib/tones';
import type { ToneName } from '@/lib/tones';

/**
 * Every department the office actually has, pinned to the glyph it must get.
 *
 * <p>Written out in full rather than spot-checked, because the failure this guards against is
 * silent and specific: a rule that matches a word buried inside a longer one. "Transport" contains
 * *sport*, and shipped as a trophy until somebody looked at a screenshot; it would then have become
 * a road, because it also contains *port*. "Agriculture" contains *culture*, which is a temple.
 *
 * <p>None of that is visible in a passing build or a green type check. A table is.
 */
const EXPECTED: Array<[string, DepartmentIconName]> = [
  ['Department of Agriculture', 'sprout'],
  ['Department of Animal Husbandry, Dairying and Fisheries', 'fish'],
  ['Department of Backward Classes, Most Backward Classes and Minorities Welfare', 'people'],
  ['Department of Commercial Taxes and Registration', 'receipt'],
  ['Department of Co-operation, Food and Consumer Protection', 'basket'],
  ['Department of Energy', 'bolt'],
  ['Department of Environment and Forests', 'tree'],
  ['Department of Finance', 'banknote'],
  ['Department of Handlooms, Handicrafts, Textiles and Khadi', 'textile'],
  ['Department of Health and Family Welfare', 'heart'],
  ['Department of Higher Education', 'cap'],
  ['Department of Highways and Minor Ports', 'road'],
  ['Department of Home, Prohibition and Excise', 'shield'],
  ['Department of Housing and Urban Development', 'buildings'],
  ['Department of Human Resources Management', 'badge'],
  ['Department of Industries', 'factory'],
  ['Department of Information Technology and Digital Services', 'chip'],
  ['Department of Labour and Employment', 'briefcase'],
  ['Department of Law', 'scales'],
  ['Department of Legislative Assembly', 'columns'],
  ['Department of Micro, Small and Medium Enterprises', 'store'],
  ['Department of Miscellaneous Officers, Secretariat', 'archive'],
  ['Department of Mudalvarin Mugavari', 'megaphone'],
  ['Department of Municipal Administration and Water Supply', 'droplet'],
  ['Department of Natural Resources', 'mountain'],
  ['Department of Other States Government', 'globe'],
  ['Department of Planning, Development and Special Initiatives', 'compass'],
  ['Department of Public', 'flag'],
  ['Department of Public (Elections)', 'ballot'],
  ['Department of Public Works', 'hardhat'],
  ['Department of Revenue and Disaster Management', 'alert'],
  ['Department of Rural Development and Panchayat Raj', 'village'],
  ['Department of School Education', 'book'],
  ['Department of Social Justice', 'people'],
  ['Department of Social Reforms', 'renew'],
  ['Department of Social Welfare and Women Empowerment', 'people'],
  ['Department of Special Programme Implementation', 'target'],
  ['Department of Tamil Development and Information', 'speech'],
  ['Department of Tourism, Culture and Religious Endowments', 'temple'],
  ['Department of Transport', 'bus'],
  ['Department of Water Resources', 'waves'],
  ['Department of Welfare of Differently Abled Persons', 'wheelchair'],
  ['Department of Youth Welfare and Sports Development', 'trophy'],
];

describe('departmentIcon', () => {
  it('covers the whole department list', () => {
    expect(EXPECTED).toHaveLength(43);
  });

  it.each(EXPECTED)('%s → %s', (name, icon) => {
    expect(departmentIcon(name)).toBe(icon);
  });

  describe('the words that hide inside other words', () => {
    // Each of these was, or would have been, wrong.
    it('does not read Transport as a sport, or a port', () => {
      expect(departmentIcon('Department of Transport')).toBe('bus');
      expect(departmentIcon('Department of Youth Welfare and Sports Development')).toBe('trophy');
      expect(departmentIcon('Department of Highways and Minor Ports')).toBe('road');
    });

    it('does not read Agriculture as culture', () => {
      expect(departmentIcon('Department of Agriculture')).toBe('sprout');
      expect(departmentIcon('Department of Tourism, Culture and Religious Endowments')).toBe(
        'temple',
      );
    });

    it('keeps the three Public departments apart', () => {
      expect(departmentIcon('Department of Public')).toBe('flag');
      expect(departmentIcon('Department of Public (Elections)')).toBe('ballot');
      expect(departmentIcon('Department of Public Works')).toBe('hardhat');
    });

    it('gives Water Supply to Municipal and the waves to Water Resources', () => {
      expect(departmentIcon('Department of Municipal Administration and Water Supply')).toBe(
        'droplet',
      );
      expect(departmentIcon('Department of Water Resources')).toBe('waves');
    });

    it('separates school from higher education', () => {
      expect(departmentIcon('Department of School Education')).toBe('book');
      expect(departmentIcon('Department of Higher Education')).toBe('cap');
    });
  });

  it('falls back to an archive rather than nothing for a department added later', () => {
    expect(departmentIcon('Department of Something Nobody Foresaw')).toBe('archive');
    expect(departmentIcon('')).toBe('archive');
  });

  it('is case-insensitive, since the API is not the only source of these names', () => {
    expect(departmentIcon('DEPARTMENT OF TRANSPORT')).toBe('bus');
    expect(departmentIcon('department of finance')).toBe('banknote');
  });
});

describe('the colour that goes with the glyph', () => {
  /*
   * That every glyph *has* a colour is enforced by the compiler — ICON_TONES is typed
   * Record<DepartmentIconName, ToneName>, so a new glyph without one will not build. What a test
   * can add is whether the colour chosen is the right one, which no type can know.
   */
  const EXPECTED_TONES: Array<[string, ToneName]> = [
    ['Department of Agriculture', 'green'], // a sprout
    ['Department of Environment and Forests', 'emerald'], // a tree
    ['Department of Rural Development and Panchayat Raj', 'lime'], // a village
    ['Department of Animal Husbandry, Dairying and Fisheries', 'cyan'], // a fish
    ['Department of Municipal Administration and Water Supply', 'sky'], // a droplet
    ['Department of Water Resources', 'blue'], // waves
    ['Department of Energy', 'amber'], // a bolt
    ['Department of Public Works', 'orange'], // a hard hat, in high-visibility orange
    ['Department of Health and Family Welfare', 'rose'], // a heart
    ['Department of Finance', 'emerald'], // money
    ['Department of Handlooms, Handicrafts, Textiles and Khadi', 'pink'], // cloth
    ['Department of Tourism, Culture and Religious Endowments', 'gold'], // the seal's own colour
    ['Department of Law', 'navy'],
    ['Department of Welfare of Differently Abled Persons', 'purple'],
  ];

  it.each(EXPECTED_TONES)('%s is %s, the colour of its picture', (name, expected) => {
    expect(departmentTone(name)).toEqual(tone(expected));
  });

  it('gives departments that share a glyph the same colour', () => {
    // The three "Social" departments are the ones the initials could not tell apart. They share a
    // crowd of people and therefore share a violet, which is honest: they are the same kind of
    // thing, and the names beside them do the distinguishing.
    expect(departmentTone('Department of Social Justice')).toEqual(
      departmentTone('Department of Social Welfare and Women Empowerment'),
    );
  });

  it('falls back to slate for a department nobody has classified', () => {
    expect(departmentTone('Department of Something Nobody Foresaw')).toEqual(tone('slate'));
  });

  it('is keyed on the name, so two screens showing one department agree', () => {
    expect(departmentTone('Department of Energy')).toEqual(departmentTone('Department of Energy'));
  });
});
