/*
 * Turns the seal artwork into the three assets the application ships: a transparent logo for the
 * header, and the two favicons.
 *
 * The source is a photograph of the seal on white, with a soft drop shadow to the bottom right.
 * Keying out the white would be wrong twice over — the seal's own inner disc is white, so it would
 * be punched through, and the shadow is not white enough to key cleanly. The seal is a circle, so
 * the mask is a circle: find its edge by scanning the middle row for the first and last pixel dark
 * enough to be ink, and everything outside that radius becomes transparent. The shadow falls
 * outside it and disappears with the rest of the background.
 *
 * Usage: node scripts/build-logo.mjs <source-image>
 */
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const PUBLIC = join(dirname(dirname(fileURLToPath(import.meta.url))), 'public');

/** Anything below this is ink rather than page. The background is pure white; the gold rim is not. */
const INK = 225;

/** The rendered header logo is 72px; twice that covers a 2x display without shipping a huge file. */
const LOGO = 256;
const FAVICON = 64;

const source = process.argv[2];
if (!source) {
  console.error('usage: node scripts/build-logo.mjs <source-image>');
  process.exit(1);
}

const { data, info } = await sharp(source).raw().toBuffer({ resolveWithObject: true });
const { width, height, channels } = info;

const luminance = (x, y) => {
  const i = (y * width + x) * channels;
  return 0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2];
};

const middle = Math.floor(height / 2);
let left = 0;
let right = width - 1;
while (left < width && luminance(left, middle) >= INK) left += 1;
while (right > left && luminance(right, middle) >= INK) right -= 1;

const radius = Math.round((right - left) / 2);
const cx = left + radius;

// The top edge is clean — the shadow is only ever below and to the right — so the centre's y is
// found by dropping one radius from the first ink in the middle column.
let top = 0;
while (top < height && luminance(cx, top) >= INK) top += 1;
const cy = top + radius;

console.log(`seal: centre ${cx},${cy} radius ${radius} (source ${width}x${height})`);

const size = radius * 2;
const seal = await sharp(source)
  .extract({ left: cx - radius, top: cy - radius, width: size, height: size })
  .toBuffer();

/**
 * A white disc the size of the output, used as an alpha stencil.
 *
 * <p>Built per output size rather than once at full size because sharp resizes before it
 * composites, whatever order the calls are written in — a full-size mask over a 256px image is
 * refused outright.
 *
 * <p>The circle sits a hair inside the frame so the edge lands on the rim rather than on a row of
 * shadow, and so the resize has a pixel of slack to antialias into.
 */
const disc = (px) =>
  Buffer.from(
    `<svg xmlns="http://www.w3.org/2000/svg" width="${px}" height="${px}">` +
      `<circle cx="${px / 2}" cy="${px / 2}" r="${px / 2 - px / 512 - 0.5}" fill="#fff"/></svg>`,
  );

const round = async (px) => {
  const resized = await sharp(seal).resize(px, px, { fit: 'cover' }).png().toBuffer();
  const mask = await sharp(disc(px)).resize(px, px).png().toBuffer();
  return sharp(resized).composite([{ input: mask, blend: 'dest-in' }]).png();
};

const logo = await (await round(LOGO)).toBuffer();
await sharp(logo).webp({ quality: 92, alphaQuality: 100 }).toFile(join(PUBLIC, 'logo.webp'));

const favicon = await (await round(FAVICON)).toBuffer();
writeFileSync(join(PUBLIC, 'favicon.png'), favicon);

// The SVG is the PNG inlined: one file, no second request, and it scales to whatever a browser
// asks for. No clip-path any more — the transparency is in the pixels now.
writeFileSync(
  join(PUBLIC, 'favicon.svg'),
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${FAVICON} ${FAVICON}">` +
    `<image href="data:image/png;base64,${favicon.toString('base64')}" ` +
    `width="${FAVICON}" height="${FAVICON}"/></svg>\n`,
);

console.log('wrote public/logo.webp, public/favicon.png, public/favicon.svg');
