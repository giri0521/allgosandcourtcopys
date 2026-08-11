import { formatCategory } from '@/lib/format';
import { categoryTone } from '@/lib/tones';
import type { FolderCategory } from '@/types/api';

/**
 * A folder's document class, coloured consistently wherever it appears.
 *
 * <p>The name is always spelled out beside the colour, so the classification survives being printed
 * in black and white or read by someone who cannot distinguish the hues.
 */
export function CategoryBadge({ category }: { category: FolderCategory }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold
        ${categoryTone(category).chip}`}
    >
      <span aria-hidden className="h-1.5 w-1.5 rounded-full bg-current opacity-70" />
      {formatCategory(category)}
    </span>
  );
}
