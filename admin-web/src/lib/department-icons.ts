/**
 * Which glyph stands for which department.
 *
 * <p>Forty-three departments were being drawn as two-letter squares, and the letters were the
 * problem: nearly every name begins "Department of", so the initials come from whatever follows —
 * and "Social Justice", "Social Reforms" and "Social Welfare and Women Empowerment" all reduce to
 * SJ, SR, SW. Three grey squares of near-identical lettering is not something you can find a
 * department in at a glance, which is the only job the avatar has.
 *
 * <p>A picture is findable in a way two letters are not. The colour already varies by department;
 * the shape now varies by what the department *does*, so the eye has two things to lock onto
 * instead of a word it has to stop and read.
 *
 * <p>The name is still spelled out beside every icon, everywhere. Nothing here is the only carrier
 * of meaning — an icon for "Environment and Forests" is a tree, which is a hint, not a label.
 */

export type DepartmentIconName =
  | 'sprout'
  | 'fish'
  | 'people'
  | 'receipt'
  | 'basket'
  | 'bolt'
  | 'tree'
  | 'banknote'
  | 'textile'
  | 'heart'
  | 'cap'
  | 'road'
  | 'shield'
  | 'buildings'
  | 'badge'
  | 'factory'
  | 'chip'
  | 'briefcase'
  | 'scales'
  | 'columns'
  | 'store'
  | 'archive'
  | 'megaphone'
  | 'droplet'
  | 'mountain'
  | 'globe'
  | 'compass'
  | 'flag'
  | 'ballot'
  | 'hardhat'
  | 'alert'
  | 'village'
  | 'book'
  | 'target'
  | 'speech'
  | 'temple'
  | 'bus'
  | 'waves'
  | 'wheelchair'
  | 'trophy'
  | 'renew';

/**
 * Matched in order, first hit wins — which is the whole reason this is a list and not a map.
 *
 * <p>Two things bite here, and both did.
 *
 * <p>The first is containment: "Transport" contains *sport* and *port*, so it was drawn as a trophy
 * until somebody looked at it, and would have become a road the moment that was half-fixed.
 * "Agriculture" contains *culture*, which is a temple. Every short word that could sit inside a
 * longer one is anchored with \b for that reason; the long, unambiguous stems are left alone.
 *
 * <p>The second is precedence: "Public Works" must be tested before "Public", and "Water Supply"
 * belongs to Municipal Administration and must not be caught by the water that means Water
 * Resources. Compounds go above the plain words they contain.
 */
const RULES: Array<[RegExp, DepartmentIconName]> = [
  // Words that hide inside other words. These have to be settled before anything else runs.
  [/\btransport\b|\bvehicle/i, 'bus'],
  [/\bagricultur|\bfarmer/i, 'sprout'],

  // Compounds, above the plain words they contain.
  [/\bpublic\s*\(?\s*election/i, 'ballot'],
  [/\bpublic\s+works\b/i, 'hardhat'],
  [/\bmunicipal|\bwater\s+supply\b/i, 'droplet'],
  [/\bwater\s+resource/i, 'waves'],
  [/\bschool\s+education\b/i, 'book'],
  [/\bhigher\s+education\b/i, 'cap'],
  [/differently\s+abled|\bdisab/i, 'wheelchair'],
  [/\byouth\b|\bsports?\b/i, 'trophy'],
  [/special\s+programme/i, 'target'],
  [/\bplanning\b|\binitiative/i, 'compass'],
  [/\blegislative\b|\bassembly\b/i, 'columns'],
  [/miscellaneous|secretariat/i, 'archive'],
  [/mudalvar|mugavari|grievance/i, 'megaphone'],
  [/other\s+states\b/i, 'globe'],
  [/natural\s+resource/i, 'mountain'],

  // The plain subjects.
  [/\banimal\b|fisher|dairy/i, 'fish'],
  // Reform is change, and a cycle says that where a third crowd of people would not. Three
  // departments beginning "Social" is the very collision the initials had — SJ, SR, SW — so at
  // least one of them has to look like something other than the other two.
  [/\breform/i, 'renew'],
  [/backward|minorit|\bsocial\b|\bwomen\b|tribal|adi\s*dravidar/i, 'people'],
  [/\btax|registration/i, 'receipt'],
  [/co-?operation|\bfood\b|consumer/i, 'basket'],
  [/\benergy\b|electric|\bpower\b/i, 'bolt'],
  [/environment|\bforest/i, 'tree'],
  [/\bfinance\b|treasur/i, 'banknote'],
  [/handloom|handicraft|textile|khadi/i, 'textile'],
  [/\bhealth\b|medical|family\s+welfare\b/i, 'heart'],
  [/highway|\bports?\b|\broads?\b/i, 'road'],
  [/\bhome\b|prohibition|excise|police/i, 'shield'],
  [/housing|\burban\b/i, 'buildings'],
  [/human\s+resource|personnel/i, 'badge'],
  [/\bindustr/i, 'factory'],
  [/information\s+technology|\bdigital\b|electronic/i, 'chip'],
  [/\blabour\b|employment|\bskill/i, 'briefcase'],
  [/\blaw\b|\blegal\b|justice|\bcourt/i, 'scales'],
  [/enterprise|\bmsme\b|\bmicro\b/i, 'store'],
  [/\brural\b|panchayat|\bvillage\b/i, 'village'],
  [/\brevenue\b|disaster/i, 'alert'],
  [/\btamil\b|language/i, 'speech'],
  [/\btouris|\bculture\b|religio|endowment|\btemple\b/i, 'temple'],
  [/\beducation\b|universit/i, 'cap'],
  [/\bpublic\b/i, 'flag'],
];

/**
 * The glyph for a department, by name.
 *
 * <p>Falls back to an archive box, which is what an unclassified department in a records system is:
 * a place documents are kept. A department the office adds next year gets that until somebody adds
 * a rule for it, which is a dull icon rather than a broken screen.
 */
export function departmentIcon(name: string): DepartmentIconName {
  return RULES.find(([pattern]) => pattern.test(name))?.[1] ?? 'archive';
}
