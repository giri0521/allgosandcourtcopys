import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  CANVAS,
  darkBlockOf,
  generateRamp,
  parseRamps,
  STEPS,
  SURFACE,
  validateRamp,
} from './dark-palette.mjs';

/**
 * Audits the dark palette, and prints the CSS for anything missing from it.
 *
 *   node scripts/generate-dark-palette.mjs           # audit, and print what is missing
 *   node scripts/generate-dark-palette.mjs --check   # audit only; exits 1 on a problem
 *
 * <p>Dark mode here is a restatement of the palette in index.css rather than a set of `dark:`
 * variants, so a family with no dark values does not fail loudly — it simply keeps its light colour
 * on a dark page, on whichever screen happened to use it. This is how you find that before a user
 * does.
 *
 * <p>Nothing is written automatically. It prints, you paste, you look at it. A script that edits the
 * stylesheet unattended is a script that will one day quietly restyle the whole application.
 */

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const css = readFileSync(join(root, 'src/index.css'), 'utf8');
const tailwind = readFileSync(join(root, 'node_modules/tailwindcss/theme.css'), 'utf8');

// Tailwind's own light ramps, in OKLCH, which is what it ships.
const light = {};
for (const [, family, step, L, C, H] of tailwind.matchAll(
  /--color-([a-z]+)-(\d+):\s*oklch\(([\d.]+)%\s+([\d.]+)\s+([\d.]+)\)/g,
)) {
  (light[family] ??= {})[Number(step)] = { L: Number(L) / 100, C: Number(C), H: Number(H) };
}

const dark = parseRamps(darkBlockOf(css));

// ------------------------------------------------------------------------- audit
console.log(`checking ${Object.keys(dark).length} dark ramps against the card (${SURFACE}) and the page (${CANVAS})\n`);

const failures = [];
for (const [family, ramp] of Object.entries(dark)) {
  const missing = STEPS.filter((step) => !ramp[step]);
  if (missing.length) failures.push(`${family}: no value for step ${missing.join(', ')}`);
  failures.push(...validateRamp(family, ramp));
}

const uncovered = Object.keys(light)
  .filter((family) => !dark[family])
  .sort();

for (const line of failures) console.log('  ✗ ' + line);
if (!failures.length) console.log('  every ramp meets its targets');

// --------------------------------------------------------------- what is missing
if (uncovered.length) {
  const referenceChroma =
    Object.values(light).reduce((sum, ramp) => sum + (ramp[500]?.C ?? 0), 0) /
    Object.keys(light).length;

  console.log(`\n${uncovered.length} family/families have no dark values. Paste into the generated`);
  console.log("section of :root[data-theme='dark'] in src/index.css:\n");

  for (const family of uncovered) {
    const ramp = generateRamp(light[family], referenceChroma);
    const problems = validateRamp(family, ramp);
    if (problems.length) {
      console.log(`  /* ${family}: GENERATED VALUES FAIL — needs tuning by hand */`);
      for (const problem of problems) console.log(`  /*   ${problem} */`);
    }
    for (let i = 0; i < STEPS.length; i += 4) {
      console.log(
        '  ' +
          STEPS.slice(i, i + 4)
            .map((step) => `--color-${family}-${step}: ${ramp[step]};`)
            .join(' '),
      );
    }
    console.log('');
  }
} else {
  console.log('\nevery family Tailwind ships has dark values');
}

/*
 * An uncovered family is a failure, not a note. It is the exact situation this script exists to
 * prevent: the colour still works — in light, on whichever screen has just started using it — so
 * nothing complains until somebody switches to dark and finds the text has gone.
 */
const problems = failures.length + uncovered.length;
if (problems) {
  console.log(`\n${problems} PROBLEM(S)`);
  process.exit(1);
}
process.exit(0);
