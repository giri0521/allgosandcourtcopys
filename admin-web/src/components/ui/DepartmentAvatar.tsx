import { departmentIcon } from '@/lib/department-icons';
import type { DepartmentIconName } from '@/lib/department-icons';
import { departmentTone } from '@/lib/tones';

/**
 * The glyphs, drawn to one specification: a 24×24 box, no fill, `currentColor` stroke, round caps
 * and joins. That is the language every other icon in this application already speaks — the bell,
 * the upload arrow, the file — so a department mark sits beside them without announcing that it
 * came from somewhere else.
 *
 * <p>Stroke weight is set on the `<svg>` rather than per path, so the whole set stays consistent by
 * construction rather than by everyone remembering.
 */
const GLYPHS: Record<DepartmentIconName, React.ReactNode> = {
  sprout: (
    <>
      <path d="M12 20v-8" />
      <path d="M12 12c0-3 2-5 5-5 0 3-2 5-5 5z" />
      <path d="M12 15c0-3-2-5-5-5 0 3 2 5 5 5z" />
      <path d="M5 20h14" />
    </>
  ),
  fish: (
    <>
      <path d="M3 12c0-2.7 2.9-4.9 6.6-4.9s6.6 2.2 6.6 4.9-2.9 4.9-6.6 4.9S3 14.7 3 12z" />
      <path d="M16.2 12 21 8.2v7.6z" />
      <path d="M6.4 10.6h.01" />
    </>
  ),
  people: (
    <>
      <path d="M9 11a3.2 3.2 0 1 0 0-6.4 3.2 3.2 0 0 0 0 6.4z" />
      <path d="M2.5 20c0-3.3 2.9-5.5 6.5-5.5s6.5 2.2 6.5 5.5" />
      <path d="M16.5 5.2a3.2 3.2 0 0 1 0 6.2" />
      <path d="M17.5 15c2.4.7 4 2.4 4 5" />
    </>
  ),
  receipt: (
    <>
      <path d="M6 3h12v18l-2.4-1.6L13.2 21l-2.4-1.6L8.4 21 6 19.4z" />
      <path d="M9.5 8h5" />
      <path d="M9.5 12h5" />
    </>
  ),
  basket: (
    <>
      <path d="M4 9h16l-1.4 10.2a2 2 0 0 1-2 1.8H7.4a2 2 0 0 1-2-1.8z" />
      <path d="M9 9V6.5a3 3 0 0 1 6 0V9" />
    </>
  ),
  bolt: <path d="M13 2 4 14h7l-1 8 9-12h-7z" />,
  tree: (
    <>
      <path d="M12 3 8 9h8z" />
      <path d="M12 8 6 15h12z" />
      <path d="M12 15v6" />
      <path d="M9 21h6" />
    </>
  ),
  banknote: (
    <>
      <path d="M2.5 6.5h19v11h-19z" />
      <path d="M12 14.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z" />
      <path d="M6 12h.01" />
      <path d="M18 12h.01" />
    </>
  ),
  textile: (
    <>
      <path d="M8.6 3.5 4 6.2l2 3.6 2.2-1.3V20.5h7.6V8.5L18 9.8l2-3.6-4.6-2.7a3.6 3.6 0 0 1-6.8 0z" />
    </>
  ),
  heart: (
    <>
      <path d="M12 20.5C7.4 16.8 3.5 13.6 3.5 9.8A4.3 4.3 0 0 1 12 7.4a4.3 4.3 0 0 1 8.5 2.4c0 3.8-3.9 7-8.5 10.7z" />
      <path d="M4 12h3l1.5-3 2.5 6 1.5-3h4" />
    </>
  ),
  cap: (
    <>
      <path d="M22 9 12 4.5 2 9l10 4.5z" />
      <path d="M6 11.3V16c0 1.7 2.7 3 6 3s6-1.3 6-3v-4.7" />
      <path d="M22 9v5.5" />
    </>
  ),
  road: (
    <>
      <path d="M7 3 4.5 21" />
      <path d="M17 3l2.5 18" />
      <path d="M12 4.5v3" />
      <path d="M12 10.5v3" />
      <path d="M12 16.5v3" />
    </>
  ),
  shield: <path d="M12 2.5 4.5 5.8v5.7C4.5 16.8 12 21.5 12 21.5s7.5-4.7 7.5-10V5.8z" />,
  buildings: (
    <>
      <path d="M2.5 21h19" />
      <path d="M5 21V7l6-3.5V21" />
      <path d="M11 10.5h8V21" />
      <path d="M8 9h.01" />
      <path d="M8 13h.01" />
      <path d="M8 17h.01" />
      <path d="M15 14h.01" />
      <path d="M15 17.5h.01" />
    </>
  ),
  badge: (
    <>
      <path d="M4.5 3.5h15v17h-15z" />
      <path d="M12 11.5a2.4 2.4 0 1 0 0-4.8 2.4 2.4 0 0 0 0 4.8z" />
      <path d="M8 17c0-2 1.8-3.2 4-3.2s4 1.2 4 3.2" />
    </>
  ),
  factory: (
    <>
      <path d="M2.5 21V9l6 4V9l6 4V4.5h5.5V21z" />
      <path d="M2 21h20" />
      <path d="M5.5 17.5h2" />
      <path d="M11.5 17.5h2" />
      <path d="M17.5 17.5h2" />
    </>
  ),
  chip: (
    <>
      <path d="M7 7h10v10H7z" />
      <path d="M10.5 10.5h3v3h-3z" />
      <path d="M10 3v4" />
      <path d="M14 3v4" />
      <path d="M10 17v4" />
      <path d="M14 17v4" />
      <path d="M3 10h4" />
      <path d="M3 14h4" />
      <path d="M17 10h4" />
      <path d="M17 14h4" />
    </>
  ),
  briefcase: (
    <>
      <path d="M2.5 7.5h19v13h-19z" />
      <path d="M8.5 7.5V5.5a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v2" />
      <path d="M2.5 13h19" />
    </>
  ),
  scales: (
    <>
      <path d="M12 4.5V21" />
      <path d="M7 21h10" />
      <path d="M4.5 8 12 6l7.5 2" />
      <path d="M4.5 8 2 14.5a2.6 2.6 0 0 0 5 0z" />
      <path d="M19.5 8 17 14.5a2.6 2.6 0 0 0 5 0z" />
    </>
  ),
  columns: (
    <>
      <path d="M12 2.5 2.5 8h19z" />
      <path d="M5.5 8v10" />
      <path d="M9.8 8v10" />
      <path d="M14.2 8v10" />
      <path d="M18.5 8v10" />
      <path d="M2.5 21h19" />
      <path d="M4 18h16" />
    </>
  ),
  store: (
    <>
      <path d="M3 9 4.6 4h14.8L21 9" />
      <path d="M4.5 9v11h15V9" />
      <path d="M3 9h18" />
      <path d="M9.5 20v-5.5h5V20" />
    </>
  ),
  archive: (
    <>
      <path d="M3 3.5h18V8H3z" />
      <path d="M5 8v12.5h14V8" />
      <path d="M10 12h4" />
    </>
  ),
  megaphone: (
    <>
      <path d="M3 10.5v3a1 1 0 0 0 1 1h2.5l7 4V5.5l-7 4H4a1 1 0 0 0-1 1z" />
      <path d="M17 8.5a5 5 0 0 1 0 7" />
      <path d="M6.5 14.5 8 21" />
    </>
  ),
  droplet: <path d="M12 3s6.5 6.6 6.5 10.6A6.5 6.5 0 0 1 5.5 13.6C5.5 9.6 12 3 12 3z" />,
  mountain: (
    <>
      <path d="M2 20h20" />
      <path d="M2.5 20 9 8.5l3.5 6L15 11l6.5 9z" />
      <path d="M18 6.5h.01" />
    </>
  ),
  globe: (
    <>
      <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z" />
      <path d="M3 12h18" />
      <path d="M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18z" />
    </>
  ),
  compass: (
    <>
      <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z" />
      <path d="M16 8l-2.2 5.8L8 16l2.2-5.8z" />
    </>
  ),
  flag: (
    <>
      <path d="M5 21V4" />
      <path d="M5 4.5c4-2 8 2 12 0v8c-4 2-8-2-12 0" />
    </>
  ),
  ballot: (
    <>
      <path d="M4 11h16v10H4z" />
      <path d="M8 11V5.5l8-2.5V11" />
      <path d="M10.5 7.2 12 8.7l3-3.4" />
    </>
  ),
  hardhat: (
    <>
      <path d="M4.5 16.5a7.5 7.5 0 0 1 15 0z" />
      <path d="M2 16.5h20" />
      <path d="M9.5 9.4V6.5A1.5 1.5 0 0 1 11 5h2a1.5 1.5 0 0 1 1.5 1.5v2.9" />
    </>
  ),
  alert: (
    <>
      <path d="M12 3.5 2.5 20.5h19z" />
      <path d="M12 10v4.5" />
      <path d="M12 17.8h.01" />
    </>
  ),
  village: (
    <>
      <path d="M2 21h20" />
      <path d="M4 21V10l6-4.5 6 4.5v11" />
      <path d="M8 21v-5h4v5" />
      <path d="M19 21v-3.5" />
      <path d="M19 17.5a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2z" />
    </>
  ),
  book: (
    <>
      <path d="M12 7.5C10.4 6 8.3 5.3 5.5 5.3H2.5v12.4h3c2.8 0 4.9.7 6.5 2.2" />
      <path d="M12 7.5c1.6-1.5 3.7-2.2 6.5-2.2h3v12.4h-3c-2.8 0-4.9.7-6.5 2.2" />
      <path d="M12 7.5v12.4" />
    </>
  ),
  target: (
    <>
      <path d="M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z" />
      <path d="M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z" />
      <path d="M12 13.2a1.2 1.2 0 1 0 0-2.4 1.2 1.2 0 0 0 0 2.4z" />
    </>
  ),
  speech: (
    <>
      <path d="M21 12.5a7.5 7.5 0 0 1-7.5 7.5H8l-4.5 3v-10A7.5 7.5 0 0 1 11 5.5h2.5A7.5 7.5 0 0 1 21 12.5z" />
      <path d="M8.5 10.5h8" />
      <path d="M8.5 14.5h5" />
    </>
  ),
  temple: (
    <>
      <path d="M2.5 21h19" />
      <path d="M5.5 21v-7.5h13V21" />
      <path d="M7 13.5 12 6l5 7.5" />
      <path d="M12 6V3" />
      <path d="M10.2 4.2h3.6" />
      <path d="M9.8 21v-4.5h4.4V21" />
    </>
  ),
  bus: (
    <>
      <path d="M4 6.5a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2V17H4z" />
      <path d="M4 11.5h16" />
      <path d="M7.5 20.5a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z" />
      <path d="M16.5 20.5a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6z" />
    </>
  ),
  waves: (
    <>
      <path d="M2 7.5c2-2 4-2 6 0s4 2 6 0 4-2 6 0" />
      <path d="M2 12.5c2-2 4-2 6 0s4 2 6 0 4-2 6 0" />
      <path d="M2 17.5c2-2 4-2 6 0s4 2 6 0 4-2 6 0" />
    </>
  ),
  wheelchair: (
    <>
      <path d="M14.5 6a2 2 0 1 0 0-4 2 2 0 0 0 0 4z" />
      <path d="M14.5 8v5h4l2 5.5" />
      <path d="M14.5 13h-3.5" />
      <path d="M15 15.5a5 5 0 1 1-4.5-4" />
    </>
  ),
  renew: (
    <>
      <path d="M20.5 12a8.5 8.5 0 1 1-2.6-6.1" />
      <path d="M20.5 3.5V9.5H14.5" />
    </>
  ),
  trophy: (
    <>
      <path d="M7 4h10v4.5a5 5 0 0 1-10 0z" />
      <path d="M7 5.5H4.5a2.5 2.5 0 0 0 2.8 4.4" />
      <path d="M17 5.5h2.5a2.5 2.5 0 0 1-2.8 4.4" />
      <path d="M12 13.5V17" />
      <path d="M8.5 21h7l-.8-4h-5.4z" />
    </>
  ),
};

const SIZES = {
  sm: { box: 'h-7 w-7 rounded-md', icon: 'h-4 w-4' },
  md: { box: 'h-10 w-10 rounded-lg', icon: 'h-5 w-5' },
  lg: { box: 'h-12 w-12 rounded-xl', icon: 'h-6 w-6' },
};

/**
 * A department's mark: its colour, from its id, and its picture, from what it does.
 *
 * <p>`aria-hidden`, always. The department's name is written beside it in every place this appears,
 * and a screen reader announcing "tree" before "Department of Environment and Forests" would be
 * noise. The icon is for the eye that is scanning, not for the ear that is being read to.
 */
export function DepartmentAvatar({
  name,
  size = 'md',
  className = '',
}: {
  name: string;
  size?: keyof typeof SIZES;
  className?: string;
}) {
  const { box, icon } = SIZES[size];

  return (
    <span
      aria-hidden
      className={`flex shrink-0 items-center justify-center ${box} ${departmentTone(name).chip} ${className}`}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.7}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={icon}
      >
        {GLYPHS[departmentIcon(name)]}
      </svg>
    </span>
  );
}
