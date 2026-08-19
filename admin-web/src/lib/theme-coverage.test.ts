import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

/**
 * The guard that makes "a new page gets both themes automatically" true rather than hopeful.
 *
 * <p>Dark mode here is not a set of `dark:` variants sprinkled through the screens — it is a
 * restatement of the palette in index.css, which every ordinary utility picks up for free because
 * Tailwind compiles `text-slate-500` to `var(--color-slate-500)`. One stylesheet re-tones forty
 * files.
 *
 * <p>The strength of that approach is also its weakness: it fails silently. A screen written next
 * month that reaches for a colour the dark block never restated keeps its light value on a dark
 * page — correct in light, unreadable in dark, on one screen, found by a user rather than by us.
 *
 * <p>So the rules below close every way that can happen. They are deliberately about the source as
 * it stands, not about a list somebody has to remember to update.
 */

/* fileURLToPath rather than URL.pathname: on Windows the latter yields "/E:/…", which is not a path. */
const SRC = dirname(dirname(fileURLToPath(import.meta.url)));
const ROOT = dirname(SRC);
const CSS = join(SRC, 'index.css');

/** Semantic tokens, which have no numeric step and must all be restated. */
const SEMANTIC_TOKENS = [
  'canvas', 'surface', 'surface-sunken', 'line', 'line-strong',
  'brand', 'brand-hover', 'brand-muted', 'on-brand',
  'danger', 'danger-hover', 'danger-muted', 'on-danger',
  'success', 'on-success', 'scrim',
];

/**
 * Colours that are the same in both themes, on purpose.
 *
 * <p>`white` is the label on a filled button and the tick inside a status dot — it sits on a colour
 * this application chose, not on the page, so it must not move. Anything genuinely white-as-a-
 * surface uses `bg-surface`, which does.
 */
const INTENTIONALLY_FIXED = ['white', 'black', 'transparent', 'current', 'inherit'];

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.tsx?$/.test(entry) && !/\.test\.tsx?$/.test(entry) ? [path] : [];
  });
}

const files = sourceFiles(SRC).map((path) => ({ path, text: readFileSync(path, 'utf8') }));
const css = readFileSync(CSS, 'utf8');

/** Everything declared inside `:root[data-theme='dark'] { … }`. */
const darkBlock = (() => {
  const start = css.indexOf(":root[data-theme='dark']");
  expect(start, 'index.css must contain a dark block').toBeGreaterThan(-1);
  return css.slice(css.indexOf('{', start), css.indexOf('\n}', start));
})();

const declaredInDark = new Set(
  [...darkBlock.matchAll(/--color-([a-z0-9-]+)\s*:/g)].map((match) => match[1]),
);

const relative = (path: string) => path.slice(ROOT.length + 1).replace(/\\/g, '/');

describe('dark theme coverage', () => {
  it('covers every palette family and step, so a new screen cannot reach an untoned colour', () => {
    // The audit script owns this: it walks every family Tailwind ships, checks the dark block has
    // all eleven steps, and checks each ramp against the contrast targets the hand-tuned families
    // meet. Running it here means a palette regression fails the test suite rather than waiting to
    // be noticed on screen.
    let output: string;
    let failed = false;
    try {
      output = execFileSync('node', ['scripts/generate-dark-palette.mjs', '--check'], {
        cwd: ROOT,
        encoding: 'utf8',
      });
    } catch (error) {
      failed = true;
      // The script reports on stdout and signals with its exit code, so the detail is in stdout
      // rather than in the thrown error's message.
      output = String((error as { stdout?: string }).stdout ?? error);
    }
    expect(failed, `scripts/generate-dark-palette.mjs reported:\n${output}`).toBe(false);
  });

  it('restates every semantic token', () => {
    const missing = SEMANTIC_TOKENS.filter((token) => !declaredInDark.has(token));
    expect(missing, `semantic tokens with no dark value: ${missing}`).toEqual([]);
  });

  it('keeps the shadow tints indirect, since Tailwind inlines a shadow colour at build time', () => {
    // If someone writes a literal colour back into --shadow-card, shadows silently stop being
    // theme-aware — they would keep the light theme's near-invisible navy on a black page.
    for (const shadow of ['--shadow-card', '--shadow-lifted', '--shadow-dialog']) {
      const line = css.split('\n').find((row) => row.trim().startsWith(`${shadow}:`));
      expect(line, `${shadow} should be declared`).toBeDefined();
      expect(line, `${shadow} must reference variables, not literal colours`).toMatch(
        /var\(--shadow-/,
      );
    }
    for (const tint of ['--shadow-contact', '--shadow-ambient']) {
      expect(darkBlock, `${tint} needs a dark value`).toContain(`${tint}:`);
    }
  });

  it('points the dark: variant at the toggle rather than the operating system', () => {
    /*
     * Out of the box `dark:bg-slate-800` compiles to `@media (prefers-color-scheme: dark)`, which
     * asks the machine and ignores this application entirely — so it would fire for someone who has
     * explicitly chosen light on a dark laptop. Almost nothing should need `dark:`, but when
     * somebody reaches for it, it has to agree with the rest of the interface.
     */
    expect(css, 'index.css must rebind the dark variant').toMatch(
      /@custom-variant\s+dark\s*\(&:where\(\[data-theme='dark'\][^)]*\)\);/,
    );
  });

  it('leaves white and black alone, and keeps bg-white out of the screens', () => {
    for (const fixed of INTENTIONALLY_FIXED) {
      expect(declaredInDark.has(fixed), `${fixed} must not be redefined per theme`).toBe(false);
    }

    // `text-white` is correct — it labels a filled button. `bg-white` is a surface, and a surface
    // that cannot change is a white card on a dark page.
    const offenders = files
      .filter((file) => /\bbg-white\b/.test(file.text))
      .map((file) => relative(file.path));
    expect(offenders, `use bg-surface rather than bg-white in: ${offenders.join(', ')}`).toEqual([]);
  });

  it('has no hard-coded colours, which are the one thing a theme cannot reach', () => {
    /*
     * A literal in a className, a style prop or an SVG attribute is outside the variable system
     * altogether: it looks right in whichever theme it was written in and is stuck there forever.
     * The chart is the cautionary tale — it carried six hex literals, and every one of them had to
     * become a variable before dark mode worked on the reports screen.
     *
     * Arbitrary *values* are fine when they reference a variable: `bg-[var(--color-scrim)]` is not a
     * hard-coded colour. Hex, rgb() and hsl() are.
     */
    const patterns: Array<[string, RegExp]> = [
      ['hex literal', /#[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?\b/g],
      ['rgb()/hsl() literal', /\b(?:rgba?|hsla?)\(/g],
    ];

    const offenders: string[] = [];
    for (const file of files) {
      // Only the places a colour can actually be applied: class strings, style props and the SVG
      // presentation attributes. Prose in a comment is not a styling decision.
      const styling = [
        ...file.text.matchAll(/className=(?:"([^"]*)"|\{`([^`]*)`\}|\{'([^']*)'\})/g),
        ...file.text.matchAll(/\bstyle=\{\{([^}]*)\}\}/g),
        ...file.text.matchAll(/\b(?:fill|stroke|stopColor|backgroundColor|color)=["'{]([^"'}]*)/g),
      ]
        .map((match) => match.slice(1).filter(Boolean).join(' '))
        .join('\n');

      for (const [label, pattern] of patterns) {
        for (const hit of styling.matchAll(pattern)) {
          offenders.push(`${relative(file.path)}: ${label} ${hit[0]}`);
        }
      }
    }

    expect(
      offenders,
      `hard-coded colours cannot be themed — use a palette step or a token:\n${offenders.join('\n')}`,
    ).toEqual([]);
  });
});
