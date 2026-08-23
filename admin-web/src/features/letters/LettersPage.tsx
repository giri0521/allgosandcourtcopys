import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { SkeletonRows } from '@/components/ui/Skeleton';
import { deleteLetter, fetchMyLetters, fetchTemplates } from '@/features/letters/api';
import { formatLetterDate } from '@/features/letters/format';
import { toApiError } from '@/lib/errors';
import { formatDateTime } from '@/lib/format';
import type { LetterSummary, LetterTemplate } from '@/types/api';

/**
 * The letters this person has written, and the way into a new one.
 *
 * <p>A letter belongs to whoever wrote it: this list is scoped to the caller by the server, and
 * there is deliberately no screen anywhere that shows somebody else's drafts.
 */
export function LettersPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [choosing, setChoosing] = useState(false);
  const [confirming, setConfirming] = useState<LetterSummary | null>(null);

  const letters = useQuery({ queryKey: ['letters', 'mine'], queryFn: () => fetchMyLetters() });
  const templates = useQuery({
    queryKey: ['letters', 'templates'],
    queryFn: fetchTemplates,
    // Only wanted once the chooser is open — a list of templates is not worth fetching to draw a
    // list of letters.
    enabled: choosing,
  });

  const remove = useMutation({
    mutationFn: (letter: LetterSummary) => deleteLetter(letter.id),
    onSuccess: () => {
      setConfirming(null);
      void queryClient.invalidateQueries({ queryKey: ['letters'] });
    },
  });

  const items = letters.data?.items ?? [];
  const error = letters.error ?? remove.error;

  const start = (template: LetterTemplate | null) => {
    setChoosing(false);
    navigate(template ? `/letters/new?template=${template.id}` : '/letters/new');
  };

  return (
    <AppShell
      title="Letters"
      subtitle="Write from a template, save it, and print or save as PDF"
      actions={<Button onClick={() => setChoosing(true)}>New letter</Button>}
    >
      {error && <Alert tone="error">{toApiError(error).message}</Alert>}

      {letters.isPending ? (
        <SkeletonRows count={4} label="Loading your letters" />
      ) : items.length === 0 ? (
        <section className="rounded-xl border border-line bg-surface p-8 text-center shadow-card">
          <h2 className="font-semibold text-slate-900">No letters yet</h2>
          <p className="mx-auto mt-1 max-w-md text-sm text-slate-500">
            Start from one of the office's templates — the standing wording is filled in, and your
            own details go into the From block.
          </p>
          <Button className="mt-5" onClick={() => setChoosing(true)}>
            Write your first letter
          </Button>
        </section>
      ) : (
        <ul className="stagger space-y-3">
          {items.map((letter) => (
            <li
              key={letter.id}
              className="group/row flex flex-wrap items-center gap-4 rounded-xl border border-line
                bg-surface p-5 shadow-card transition-[border-color,box-shadow,background-color]
                duration-[--duration-quick] ease-[--ease-settle] hover:border-navy-400
                hover:bg-navy-50/40 hover:shadow-lifted"
            >
              <div className="min-w-0 flex-1">
                <Link
                  to={`/letters/${letter.id}`}
                  className="block truncate font-medium text-slate-900 outline-none transition-colors
                    duration-[--duration-base] group-hover/row:text-navy-800
                    focus-visible:text-navy-800"
                >
                  {letter.subject}
                </Link>
                <p className="mt-0.5 text-xs text-slate-500">
                  {letter.referenceNo && <>Lr.No.{letter.referenceNo} · </>}
                  {letter.letterDate && <>{formatLetterDate(letter.letterDate)} · </>}
                  {letter.templateName ?? 'No template'} · edited {formatDateTime(letter.updatedAt)}
                </p>
              </div>

              <div className="flex items-center gap-1">
                <Button variant="ghost" size="sm" onClick={() => navigate(`/letters/${letter.id}`)}>
                  Open
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="text-red-600! hover:bg-red-50!"
                  onClick={() => setConfirming(letter)}
                >
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={choosing} onClose={() => setChoosing(false)} title="Choose a template">
        {templates.isPending ? (
          <SkeletonRows count={3} label="Loading templates" />
        ) : (
          <div className="space-y-2">
            {(templates.data ?? []).map((template) => (
              <button
                key={template.id}
                type="button"
                onClick={() => start(template)}
                className="w-full rounded-lg border border-line bg-surface px-4 py-3 text-left
                  outline-none transition-[border-color,background-color] duration-[--duration-quick]
                  hover:border-navy-400 hover:bg-navy-50/40 focus-visible:ring-2
                  focus-visible:ring-navy-300"
              >
                <span className="block font-medium text-slate-900">{template.name}</span>
                {template.description && (
                  <span className="mt-0.5 block text-sm text-slate-500">{template.description}</span>
                )}
              </button>
            ))}

            {/* Always offered, even with no templates at all — an office that has not set any up
                yet still needs to be able to write a letter. */}
            <button
              type="button"
              onClick={() => start(null)}
              className="w-full rounded-lg border border-dashed border-line-strong px-4 py-3
                text-left outline-none transition-colors duration-[--duration-quick]
                hover:border-navy-400 hover:bg-navy-50/40 focus-visible:ring-2
                focus-visible:ring-navy-300"
            >
              <span className="block font-medium text-slate-900">Start from a blank letter</span>
              <span className="mt-0.5 block text-sm text-slate-500">
                The same shape, with nothing filled in but your own details.
              </span>
            </button>
          </div>
        )}
      </Modal>

      <Modal open={confirming !== null} onClose={() => setConfirming(null)} title="Delete this letter?">
        <p className="text-sm text-slate-600">
          “{confirming?.subject}” will be removed. Nothing else in the system refers to it, so this
          cannot be undone.
        </p>
        <div className="flex justify-end gap-2 pt-4">
          <Button variant="secondary" onClick={() => setConfirming(null)}>
            Keep it
          </Button>
          <Button
            variant="danger"
            loading={remove.isPending}
            onClick={() => confirming && remove.mutate(confirming)}
          >
            Delete
          </Button>
        </div>
      </Modal>
    </AppShell>
  );
}
