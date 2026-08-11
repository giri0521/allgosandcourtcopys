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
}

const TONES = {
  navy: { chip: 'bg-navy-50 text-navy-700', text: 'text-navy-600', edge: 'bg-navy-500' },
  gold: { chip: 'bg-gold-50 text-gold-800', text: 'text-gold-600', edge: 'bg-gold-400' },
  emerald: { chip: 'bg-emerald-50 text-emerald-800', text: 'text-emerald-600', edge: 'bg-emerald-500' },
  sky: { chip: 'bg-sky-50 text-sky-800', text: 'text-sky-600', edge: 'bg-sky-500' },
  violet: { chip: 'bg-violet-50 text-violet-800', text: 'text-violet-600', edge: 'bg-violet-500' },
  rose: { chip: 'bg-rose-50 text-rose-800', text: 'text-rose-600', edge: 'bg-rose-500' },
  teal: { chip: 'bg-teal-50 text-teal-800', text: 'text-teal-600', edge: 'bg-teal-500' },
  indigo: { chip: 'bg-indigo-50 text-indigo-800', text: 'text-indigo-600', edge: 'bg-indigo-500' },
  slate: { chip: 'bg-slate-100 text-slate-700', text: 'text-slate-500', edge: 'bg-slate-400' },
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
 * A stable colour for a department, derived from its id.
 *
 * <p>Forty-three departments in one grid is a wall of identical white cards. Giving each a colour
 * makes the one you use every day findable by shape rather than by reading — and deriving it from
 * the id rather than the row's position means it is the same colour tomorrow, on every screen, for
 * every user.
 *
 * <p>Drawn from a fixed set rather than a generated hue, so nothing lands on an unreadable colour
 * or clashes with the status palette.
 */
const DEPARTMENT_TONES: ToneName[] = ['navy', 'teal', 'indigo', 'emerald', 'sky', 'violet', 'gold', 'rose'];

export function departmentTone(id: string): Tone {
  let hash = 0;
  for (let index = 0; index < id.length; index += 1) {
    hash = (hash * 31 + id.charCodeAt(index)) >>> 0;
  }
  return TONES[DEPARTMENT_TONES[hash % DEPARTMENT_TONES.length]];
}

/** The first letters of a name, for an avatar. */
export function initials(name: string, max = 2): string {
  return name
    .split(/\s+/)
    .filter((part) => /[a-z0-9]/i.test(part))
    .map((part) => part[0])
    .join('')
    .slice(0, max)
    .toUpperCase();
}
