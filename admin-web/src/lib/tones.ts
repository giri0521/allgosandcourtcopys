import { departmentIcon } from '@/lib/department-icons';
import type { DepartmentIconName } from '@/lib/department-icons';
import type { FolderCategory } from '@/types/api';

/**
 * Where colour meaning is decided, once.
 *
 * <p>Screens never pick a colour for a category, a department or a file type — they ask here. That
 * is what stops a Court Order being amber on one screen and violet on the next, and it means a
 * screen built later inherits the scheme without its author having to know it exists.
 *
 * <p>Every tone below is a light tint with a dark foreground, chosen so the text clears WCAG AA at
 * normal size. Nothing here is ever the *only* signal — a category also carries its name, a file
 * type its label. Colour is for recognition at a glance, never for meaning.
 */
export interface Tone {
  /** Background + foreground, for a badge or a glyph. */
  chip: string;
  /** Just the foreground, for an icon sitting on white. */
  text: string;
  /** A 3px marker down the side of a card. */
  edge: string;
  /**
   * A wash across a whole card, and the border that goes with it.
   *
   * <p>Deliberately weak, and deliberately only used on hover. Forty-three cards each tinted at
   * rest is a paint chart, not a directory — the eye has nowhere to settle. Held back until the
   * pointer arrives, the same colour becomes the card acknowledging you rather than shouting.
   *
   * <p>`hover:` and not `group-hover:`. The card is itself the group, and a group-hover variant
   * only ever reaches the group's descendants — applied to the group it silently does nothing,
   * which is exactly what it did until somebody read the computed background back off the page.
   */
  wash: string;
}

const TONES = {
  navy: { chip: 'bg-navy-50 text-navy-700', text: 'text-navy-600', edge: 'bg-navy-500', wash: 'hover:bg-navy-50/60 hover:border-navy-300' },
  gold: { chip: 'bg-gold-50 text-gold-800', text: 'text-gold-600', edge: 'bg-gold-400', wash: 'hover:bg-gold-50/60 hover:border-gold-300' },
  emerald: { chip: 'bg-emerald-50 text-emerald-800', text: 'text-emerald-600', edge: 'bg-emerald-500', wash: 'hover:bg-emerald-50/60 hover:border-emerald-300' },
  sky: { chip: 'bg-sky-50 text-sky-800', text: 'text-sky-600', edge: 'bg-sky-500', wash: 'hover:bg-sky-50/60 hover:border-sky-300' },
  violet: { chip: 'bg-violet-50 text-violet-800', text: 'text-violet-600', edge: 'bg-violet-500', wash: 'hover:bg-violet-50/60 hover:border-violet-300' },
  rose: { chip: 'bg-rose-50 text-rose-800', text: 'text-rose-600', edge: 'bg-rose-500', wash: 'hover:bg-rose-50/60 hover:border-rose-300' },
  teal: { chip: 'bg-teal-50 text-teal-800', text: 'text-teal-600', edge: 'bg-teal-500', wash: 'hover:bg-teal-50/60 hover:border-teal-300' },
  indigo: { chip: 'bg-indigo-50 text-indigo-800', text: 'text-indigo-600', edge: 'bg-indigo-500', wash: 'hover:bg-indigo-50/60 hover:border-indigo-300' },
  slate: { chip: 'bg-slate-100 text-slate-700', text: 'text-slate-500', edge: 'bg-slate-400', wash: 'hover:bg-slate-50/60 hover:border-slate-300' },

  /*
   * The second rank, added when departments stopped taking a colour at random and started taking
   * one that agrees with their icon. Nine hues could not do that: a tree, a droplet and a bolt each
   * want a particular colour, and there are forty-three departments to place.
   *
   * Every family here has values in both themes — see the dark block in index.css — so these tint
   * and re-tone exactly like the nine above, with no further work.
   */
  green: { chip: 'bg-green-50 text-green-800', text: 'text-green-600', edge: 'bg-green-500', wash: 'hover:bg-green-50/60 hover:border-green-300' },
  lime: { chip: 'bg-lime-50 text-lime-800', text: 'text-lime-600', edge: 'bg-lime-500', wash: 'hover:bg-lime-50/60 hover:border-lime-300' },
  amber: { chip: 'bg-amber-50 text-amber-800', text: 'text-amber-600', edge: 'bg-amber-500', wash: 'hover:bg-amber-50/60 hover:border-amber-300' },
  orange: { chip: 'bg-orange-50 text-orange-800', text: 'text-orange-600', edge: 'bg-orange-500', wash: 'hover:bg-orange-50/60 hover:border-orange-300' },
  cyan: { chip: 'bg-cyan-50 text-cyan-800', text: 'text-cyan-600', edge: 'bg-cyan-500', wash: 'hover:bg-cyan-50/60 hover:border-cyan-300' },
  blue: { chip: 'bg-blue-50 text-blue-800', text: 'text-blue-600', edge: 'bg-blue-500', wash: 'hover:bg-blue-50/60 hover:border-blue-300' },
  purple: { chip: 'bg-purple-50 text-purple-800', text: 'text-purple-600', edge: 'bg-purple-500', wash: 'hover:bg-purple-50/60 hover:border-purple-300' },
  pink: { chip: 'bg-pink-50 text-pink-800', text: 'text-pink-600', edge: 'bg-pink-500', wash: 'hover:bg-pink-50/60 hover:border-pink-300' },
  stone: { chip: 'bg-stone-100 text-stone-700', text: 'text-stone-500', edge: 'bg-stone-400', wash: 'hover:bg-stone-50/60 hover:border-stone-300' },
} satisfies Record<string, Tone>;

export type ToneName = keyof typeof TONES;

export function tone(name: ToneName): Tone {
  return TONES[name];
}

/**
 * The five document classes the office files under, plus the plain container.
 *
 * <p>Assigned by meaning rather than by taste: court orders take the gravity of indigo, government
 * orders the navy of the seal, circulars the lighter sky of something merely circulated, contracts
 * the emerald of an agreement in force, acts and rules the permanence of gold. General is
 * deliberately grey — it is the absence of a classification, and should not compete with the five
 * that mean something.
 */
const CATEGORY_TONES: Record<FolderCategory, ToneName> = {
  COURT_ORDER: 'indigo',
  GOVT_ORDER: 'navy',
  CIRCULAR: 'sky',
  CONTRACT: 'emerald',
  ACT_RULE: 'gold',
  GENERAL: 'slate',
};

export function categoryTone(category: FolderCategory): Tone {
  return TONES[CATEGORY_TONES[category] ?? 'slate'];
}

/** File types, so a PDF looks like a PDF wherever it appears. */
export function fileTypeTone(contentType: string): Tone {
  if (contentType === 'application/pdf') return TONES.rose;
  if (contentType.startsWith('image/')) return TONES.violet;
  if (contentType.includes('spreadsheet') || contentType.includes('ms-excel')) return TONES.emerald;
  if (contentType.includes('word') || contentType.includes('msword')) return TONES.sky;
  return TONES.slate;
}

/**
 * The colour that goes with each department glyph.
 *
 * <p>This used to be a hash of the department's id, which gave every department a stable colour and
 * no reason for it: Environment and Forests came out rose, Energy came out teal, and the tree and
 * the lightning bolt sat in tints that argued with them. A hash spreads colour evenly, which is a
 * property nobody asked for; what the eye wants is for the colour and the picture to say the same
 * thing.
 *
 * <p>So the colour follows the icon, and the icon follows the name. A tree is green, a droplet is
 * sky, a bolt is amber, money is emerald, a hard hat is the orange of high-visibility clothing, and
 * the temple takes the gold of the seal. The two signals reinforce each other instead of competing,
 * and a department keeps its colour for as long as it keeps its name — which is longer than it
 * keeps its id.
 *
 * <p>Departments that share an icon share a colour. That is the honest outcome: the three that
 * reduce to a crowd of people are the three about the welfare of communities, and pretending
 * otherwise with a different hue each would be decoration rather than meaning.
 */
const ICON_TONES: Record<DepartmentIconName, ToneName> = {
  // Land and living things.
  sprout: 'green',
  tree: 'emerald',
  village: 'lime',
  mountain: 'stone',
  fish: 'cyan',

  // Water.
  droplet: 'sky',
  waves: 'blue',

  // Power, industry and work.
  bolt: 'amber',
  factory: 'slate',
  hardhat: 'orange',
  chip: 'indigo',
  briefcase: 'stone',

  // Money and trade.
  banknote: 'emerald',
  receipt: 'teal',
  store: 'amber',
  basket: 'orange',
  textile: 'pink',

  // People.
  people: 'violet',
  wheelchair: 'purple',
  heart: 'rose',
  badge: 'teal',
  renew: 'cyan',

  // Learning, language and culture.
  cap: 'indigo',
  book: 'blue',
  speech: 'violet',
  temple: 'gold',

  // The state, and its instruments.
  scales: 'navy',
  shield: 'navy',
  columns: 'gold',
  flag: 'navy',
  ballot: 'indigo',
  alert: 'orange',
  megaphone: 'rose',
  archive: 'slate',

  // Places and movement.
  buildings: 'slate',
  road: 'stone',
  bus: 'blue',
  globe: 'sky',
  compass: 'teal',
  target: 'rose',
  trophy: 'gold',
};

/**
 * A department's colour, taken from what it does.
 *
 * <p>Keyed on the name rather than the id so that the colour and the glyph cannot disagree — they
 * are now two readings of the same fact. A department the office adds later gets the archive glyph
 * and its slate, which is dull but never wrong.
 */
export function departmentTone(name: string): Tone {
  return TONES[ICON_TONES[departmentIcon(name)] ?? 'slate'];
}

/**
 * Words that carry no identity, and would otherwise produce the same initials for everything.
 *
 * <p>Every one of the 43 departments is named "Department of …", so first-letter-of-each-word gives
 * all of them "DO". Dropping these leaves the part that actually distinguishes them.
 */
const NOISE_WORDS = new Set([
  'department', 'dept', 'of', 'and', 'the', 'for', 'to', 'in', 'on', '&',
]);

/**
 * Initials for an avatar, taken from the words that identify the thing.
 *
 * <p>"Department of Agriculture" gives AG rather than DO; "Department of Backward Classes, Most
 * Backward Classes and Minorities Welfare" gives BC. Two significant words give a letter each; a
 * single one gives its first two letters, which reads better than a lone character in a square.
 */
export function initials(name: string, max = 2): string {
  const words = name
    .split(/[\s,./-]+/)
    .map((part) => part.replace(/[^a-z0-9]/gi, ''))
    .filter((part) => part.length > 0 && !NOISE_WORDS.has(part.toLowerCase()));

  if (words.length === 0) {
    // Nothing significant survived — fall back to the raw name rather than an empty square.
    return name.replace(/[^a-z0-9]/gi, '').slice(0, max).toUpperCase() || '—';
  }

  if (words.length === 1) {
    return words[0].slice(0, max).toUpperCase();
  }

  return words
    .slice(0, max)
    .map((part) => part[0])
    .join('')
    .toUpperCase();
}
