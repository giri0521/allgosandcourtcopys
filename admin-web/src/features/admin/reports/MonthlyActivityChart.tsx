import { useState } from 'react';
import type { MonthlyActivity } from '@/types/api';

/**
 * Uploads and downloads per month, as grouped columns.
 *
 * <p><b>One axis, deliberately.</b> Both series are counts of events, so they share a scale and can
 * be compared by height. A second y-axis would let any two series be made to look correlated by
 * choosing the scales, which is the most common way a chart lies.
 *
 * <p><b>Navy and gold</b> are the brand's two colours, and the pair was checked rather than chosen
 * by eye: worst-case colour-blind separation ΔE 33.6, well clear of the floor. Blue against yellow
 * is the one hue pair that survives every common form of colour blindness.
 *
 * <p>Gold does not reach 3:1 against white, so colour alone is never the carrier here: there is a
 * legend, and every number is also available in the table beneath the chart.
 */
/*
 * The series colours are the theme's own variables rather than literals, so the bars re-tone with
 * everything else — navy-500 lifts to a legible blue against the dark canvas, while gold is fixed
 * at the seal's colour in both themes and does not move.
 *
 * They go through `var()` because SVG `fill` is an attribute, and an attribute cannot carry a
 * utility class. That is also why the gridlines and the tooltip below are written this way.
 */
const SERIES = {
  uploads: { label: 'Uploads', color: 'var(--color-navy-500)' },
  downloads: { label: 'Downloads', color: 'var(--color-gold-400)' },
};

// A fixed viewBox scaled by CSS: the text scales with the chart rather than needing measurement.
const WIDTH = 720;
const HEIGHT = 260;
const PADDING = { top: 16, right: 12, bottom: 34, left: 48 };

const PLOT_WIDTH = WIDTH - PADDING.left - PADDING.right;
const PLOT_HEIGHT = HEIGHT - PADDING.top - PADDING.bottom;

/** ≤ 24px, and the band's leftover is left as air rather than filled. */
const MAX_BAR = 24;
const BAR_GAP = 2; // the surface gap that separates the pair, instead of a stroke
const CORNER = 4;

export function MonthlyActivityChart({ data }: { data: MonthlyActivity[] }) {
  const [hovered, setHovered] = useState<number | null>(null);

  if (data.length === 0) {
    return (
      <p className="px-5 py-10 text-center text-sm text-slate-500">
        No activity in this period.
      </p>
    );
  }

  const peak = Math.max(1, ...data.flatMap((point) => [point.uploads, point.downloads]));
  const ceiling = niceCeiling(peak);
  const ticks = [0, ceiling / 4, ceiling / 2, (ceiling * 3) / 4, ceiling];

  const band = PLOT_WIDTH / data.length;
  const barWidth = Math.min(MAX_BAR, (band * 0.62 - BAR_GAP) / 2);
  const groupWidth = barWidth * 2 + BAR_GAP;

  const y = (value: number) => PADDING.top + PLOT_HEIGHT - (value / ceiling) * PLOT_HEIGHT;
  const bandStart = (index: number) => PADDING.left + index * band;

  // A month is worth naming on the axis only if the labels will not collide; past eight, every
  // other one carries the sequence and the rest are read from the tooltip.
  const labelEvery = data.length > 8 ? 2 : 1;

  return (
    <figure className="m-0">
      <figcaption className="flex flex-wrap items-center gap-4 px-5 pb-3">
        {Object.values(SERIES).map((series) => (
          <span key={series.label} className="flex items-center gap-2 text-sm text-slate-600">
            <span
              aria-hidden
              className="h-2.5 w-2.5 rounded-sm"
              style={{ backgroundColor: series.color }}
            />
            {series.label}
          </span>
        ))}
      </figcaption>

      <svg
        viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
        className="h-auto w-full"
        role="img"
        aria-label={`Uploads and downloads per month from ${data[0].month} to ${data[data.length - 1].month}. The table below has every value.`}
      >
        {/* Gridlines: hairline, solid, one step off the surface — present but recessive. */}
        {ticks.map((tick) => (
          <g key={tick}>
            <line
              x1={PADDING.left}
              x2={WIDTH - PADDING.right}
              y1={y(tick)}
              y2={y(tick)}
              stroke="var(--color-chart-grid)"
              strokeWidth={1}
            />
            <text
              x={PADDING.left - 8}
              y={y(tick) + 4}
              textAnchor="end"
              className="fill-slate-400 text-[11px] tabular-nums"
            >
              {formatTick(tick)}
            </text>
          </g>
        ))}

        {data.map((point, index) => {
          const groupLeft = bandStart(index) + (band - groupWidth) / 2;
          const isHovered = hovered === index;

          return (
            <g key={point.month}>
              {/* One hit target per month, the full band — far easier to hit than a thin column. */}
              <rect
                x={bandStart(index)}
                y={PADDING.top}
                width={band}
                height={PLOT_HEIGHT}
                fill={isHovered ? 'var(--color-chart-hover)' : 'transparent'}
                onMouseEnter={() => setHovered(index)}
                onMouseLeave={() => setHovered(null)}
              />

              <path
                d={columnPath(groupLeft, y(point.uploads), barWidth, PADDING.top + PLOT_HEIGHT - y(point.uploads))}
                fill={SERIES.uploads.color}
                pointerEvents="none"
              />
              <path
                d={columnPath(
                  groupLeft + barWidth + BAR_GAP,
                  y(point.downloads),
                  barWidth,
                  PADDING.top + PLOT_HEIGHT - y(point.downloads),
                )}
                fill={SERIES.downloads.color}
                pointerEvents="none"
              />

              {index % labelEvery === 0 && (
                <text
                  x={bandStart(index) + band / 2}
                  y={HEIGHT - 12}
                  textAnchor="middle"
                  className="fill-slate-500 text-[11px]"
                >
                  {shortMonth(point.month)}
                </text>
              )}
            </g>
          );
        })}

        {/* The baseline is the only axis line drawn: the columns grow from it. */}
        <line
          x1={PADDING.left}
          x2={WIDTH - PADDING.right}
          y1={PADDING.top + PLOT_HEIGHT}
          y2={PADDING.top + PLOT_HEIGHT}
          stroke="var(--color-chart-axis)"
          strokeWidth={1}
        />

        {hovered !== null && <Tooltip point={data[hovered]} x={bandStart(hovered) + band / 2} />}
      </svg>

      {/* The relief the palette check requires, and the way anyone using a screen reader or a
          greyscale printout gets the numbers. */}
      <details className="mt-2 px-5 pb-4">
        <summary className="cursor-pointer text-sm font-medium text-navy-600 hover:underline">
          View these figures as a table
        </summary>
        <table className="mt-3 w-full text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-slate-500">
            <tr>
              <th scope="col" className="py-2 font-semibold">Month</th>
              <th scope="col" className="py-2 text-right font-semibold">Uploads</th>
              <th scope="col" className="py-2 text-right font-semibold">Downloads</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {data.map((point) => (
              <tr key={point.month}>
                <td className="py-1.5 text-slate-700">{point.month}</td>
                <td className="py-1.5 text-right tabular-nums text-slate-700">{point.uploads}</td>
                <td className="py-1.5 text-right tabular-nums text-slate-700">{point.downloads}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}

/** The hovered month's numbers, drawn in the SVG so it scales with the chart. */
function Tooltip({ point, x }: { point: MonthlyActivity; x: number }) {
  const width = 132;
  const height = 58;
  // Keep the box inside the plot when the hovered month is at either edge.
  const left = Math.min(Math.max(x - width / 2, PADDING.left), WIDTH - PADDING.right - width);

  return (
    <g pointerEvents="none">
      <rect
        x={left}
        y={PADDING.top + 4}
        width={width}
        height={height}
        rx={8}
        fill="var(--color-tooltip)"
        opacity={0.94}
      />
      <text
        x={left + 10}
        y={PADDING.top + 22}
        fill="var(--color-tooltip-fg)"
        className="text-[11px] font-semibold"
      >
        {point.month}
      </text>
      <text
        x={left + 10}
        y={PADDING.top + 38}
        fill="var(--color-tooltip-fg-muted)"
        className="text-[11px]"
      >
        Uploads: {point.uploads}
      </text>
      <text
        x={left + 10}
        y={PADDING.top + 52}
        fill="var(--color-tooltip-fg-muted)"
        className="text-[11px]"
      >
        Downloads: {point.downloads}
      </text>
    </g>
  );
}

/**
 * A column with a rounded cap and a square foot.
 *
 * <p>Rounding both ends would lift the mark off its baseline and make short columns look as though
 * they float; the data-end is the only end that gets a radius.
 */
function columnPath(x: number, y: number, width: number, height: number): string {
  if (height <= 0) return '';

  const radius = Math.min(CORNER, height, width / 2);
  const bottom = y + height;

  return [
    `M ${x} ${bottom}`,
    `L ${x} ${y + radius}`,
    `Q ${x} ${y} ${x + radius} ${y}`,
    `L ${x + width - radius} ${y}`,
    `Q ${x + width} ${y} ${x + width} ${y + radius}`,
    `L ${x + width} ${bottom}`,
    'Z',
  ].join(' ');
}

/** Rounds the axis top to 1, 2 or 5 × a power of ten, so the ticks are numbers people read. */
function niceCeiling(peak: number): number {
  const magnitude = 10 ** Math.floor(Math.log10(peak));
  const normalised = peak / magnitude;
  const step = normalised <= 1 ? 1 : normalised <= 2 ? 2 : normalised <= 5 ? 5 : 10;
  return Math.max(4, step * magnitude);
}

function formatTick(value: number): string {
  return Number.isInteger(value) ? value.toLocaleString('en-IN') : value.toFixed(1);
}

/** "2026-03" → "Mar", with the year shown when January starts a new one. */
function shortMonth(month: string): string {
  const [year, monthNumber] = month.split('-');
  const name = new Date(Number(year), Number(monthNumber) - 1, 1).toLocaleString('en-IN', {
    month: 'short',
  });
  return monthNumber === '01' ? `${name} ${year.slice(2)}` : name;
}
