import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { DictationField } from '@/components/ui/DictationField';
import { TextField } from '@/components/ui/Field';
import { LetterSheet } from '@/features/letters/LetterSheet';
import {
  createLetter,
  fetchLetter,
  fetchTemplates,
  updateLetter,
  type LetterDraft,
} from '@/features/letters/api';
import { defaultFromBlock, todayIso } from '@/features/letters/format';
import { useAuth } from '@/lib/auth-context';
import { toApiError } from '@/lib/errors';
import type { Letter } from '@/types/api';

const EMPTY: LetterDraft = {
  templateId: null,
  referenceNo: '',
  letterDate: '',
  fromBlock: '',
  toBlock: '',
  salutation: '',
  subject: '',
  reference: '',
  body: '',
  enclosure: '',
  copyTo: '',
  signOff: '',
};

/**
 * Writing a letter: the form on one side, the letter itself on the other.
 *
 * <p>The preview is the same component that prints, drawn from the same draft the form is editing,
 * so what somebody approves on screen is exactly what comes out of the printer. A separate print
 * rendering would be a second thing to keep in step, and the first time it drifted nobody would
 * notice until a letter had already gone out.
 *
 * <p>Serves both a new letter and an existing one. Which it is depends on the route: `/letters/new`
 * starts from a template and the author's own details, `/letters/:id` loads what was saved.
 */
export function LetterEditorPage() {
  const { letterId } = useParams<{ letterId: string }>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const isNew = letterId === undefined;
  const templateId = searchParams.get('template');

  /**
   * Only what the writer has actually changed; everything else is derived below. Keeping the whole
   * draft in state would mean copying the template and the saved letter into it, which is the copy
   * an effect then has to keep in step.
   */
  const [edited, setEdited] = useState<LetterDraft | null>(null);
  const [saved, setSaved] = useState(false);

  const existing = useQuery({
    queryKey: ['letters', letterId],
    queryFn: () => fetchLetter(letterId!),
    enabled: !isNew,
  });

  const templates = useQuery({
    queryKey: ['letters', 'templates'],
    queryFn: fetchTemplates,
    enabled: isNew && templateId !== null,
  });

  /**
   * Where a letter starts before anybody types: a saved one is itself, and a new one is the chosen
   * template's wording, the author's own details in the From block, and today's date.
   *
   * <p>Derived rather than copied into state. Copying would need an effect to keep it in step with
   * two queries that arrive whenever they arrive, and the effect that seeds a form is the one that
   * eventually overwrites something somebody typed.
   */
  const seeded: LetterDraft = useMemo(() => {
    const letter = existing.data;
    if (letter) {
      return {
        templateId: letter.templateId,
        referenceNo: letter.referenceNo ?? '',
        letterDate: letter.letterDate ?? '',
        fromBlock: letter.fromBlock,
        toBlock: letter.toBlock,
        salutation: letter.salutation ?? '',
        subject: letter.subject,
        reference: letter.reference ?? '',
        body: letter.body,
        enclosure: letter.enclosure ?? '',
        copyTo: letter.copyTo ?? '',
        signOff: letter.signOff ?? '',
      };
    }

    if (!isNew || !user) return EMPTY;

    const template = (templates.data ?? []).find((candidate) => candidate.id === templateId);
    return {
      ...EMPTY,
      templateId: template?.id ?? null,
      letterDate: todayIso(),
      fromBlock: defaultFromBlock(user),
      salutation: template?.salutation ?? 'Sir/Madam,',
      subject: template?.defaultSubject ?? '',
      body: template?.body ?? '',
      signOff: [user.fullName, user.designation].filter(Boolean).join('\n'),
    };
  }, [existing.data, isNew, user, templateId, templates.data]);

  /** What the form shows: the writer's edits if there are any, otherwise the seed. */
  const draft = edited ?? seeded;

  const save = useMutation({
    mutationFn: () => (isNew ? createLetter(draft) : updateLetter(letterId!, draft)),
    onSuccess: (letter) => {
      setSaved(true);
      void queryClient.invalidateQueries({ queryKey: ['letters'] });
      if (isNew) navigate(`/letters/${letter.id}`, { replace: true });
    },
  });

  const set = (field: keyof LetterDraft) => (value: string) => {
    setEdited({ ...draft, [field]: value });
    setSaved(false);
  };

  const complete =
    draft.fromBlock.trim() && draft.toBlock.trim() && draft.subject.trim() && draft.body.trim();

  /** What the preview and the printer render: the draft as it stands, not the last saved copy. */
  const preview: Letter = {
    id: letterId ?? 'preview',
    templateId: draft.templateId,
    templateName: null,
    referenceNo: draft.referenceNo || null,
    letterDate: draft.letterDate || null,
    fromBlock: draft.fromBlock,
    toBlock: draft.toBlock,
    salutation: draft.salutation || null,
    subject: draft.subject,
    reference: draft.reference || null,
    body: draft.body,
    enclosure: draft.enclosure || null,
    copyTo: draft.copyTo || null,
    signOff: draft.signOff || null,
    createdAt: existing.data?.createdAt ?? '',
    updatedAt: existing.data?.updatedAt ?? '',
  };

  const error = existing.error ?? save.error;

  return (
    <AppShell
      title={isNew ? 'New letter' : 'Letter'}
      subtitle="Your details fill the From block; everything here can be edited before it prints"
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="secondary" onClick={() => navigate('/letters')}>
            Back
          </Button>
          {/* Printing is the browser's own dialogue, which is also where Save as PDF lives. Nothing
              is generated on the server, so what prints is exactly what is on screen. */}
          <Button variant="secondary" onClick={() => window.print()} disabled={!complete}>
            Print / Save as PDF
          </Button>
          <Button loading={save.isPending} disabled={!complete} onClick={() => save.mutate()}>
            {isNew ? 'Save letter' : 'Save changes'}
          </Button>
        </div>
      }
    >
      {error && <Alert tone="error">{toApiError(error).message}</Alert>}
      {saved && <Alert tone="success">Saved. Print it now, or come back to it later.</Alert>}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)] print:block">
        {/* The form is hidden when printing; only the sheet goes on the paper. */}
        <section className="space-y-4 print:hidden">
          <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
            <h2 className="font-semibold text-slate-900">Heading</h2>
            <div className="mt-4 space-y-4">
              <TextField
                label="File number"
                value={draft.referenceNo}
                onChange={(event) => set('referenceNo')(event.target.value)}
                placeholder="DBC/52/2026-D3"
                hint="Printed as Lr.No."
              />
              <TextField
                label="Date"
                type="date"
                value={draft.letterDate}
                onChange={(event) => set('letterDate')(event.target.value)}
              />
              <DictationField
                label="From"
                rows={5}
                value={draft.fromBlock}
                onValueChange={set('fromBlock')}
                hint="Filled from your profile. Edit it here for this letter only."
              />
              <DictationField
                label="To"
                rows={6}
                value={draft.toBlock}
                onValueChange={set('toBlock')}
                placeholder={'1. The Commissioner of MBC & DNC, Ch-5.\n2. The Commissioner of MW, Ch-05.'}
                hint="One recipient per line; number them if there are several."
              />
            </div>
          </div>

          <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
            <h2 className="font-semibold text-slate-900">The letter</h2>
            <div className="mt-4 space-y-4">
              <TextField
                label="Salutation"
                value={draft.salutation}
                onChange={(event) => set('salutation')(event.target.value)}
                placeholder="Sir/Madam,"
              />
              <DictationField
                label="Subject"
                rows={3}
                value={draft.subject}
                onValueChange={set('subject')}
                hint="Printed after Sub:"
              />
              <DictationField
                label="Reference"
                rows={2}
                value={draft.reference}
                onValueChange={set('reference')}
                hint="Printed after Ref: — the order or letter this one answers"
              />
              <DictationField
                label="Body"
                rows={12}
                value={draft.body}
                onValueChange={set('body')}
                hint="Blank lines separate paragraphs, exactly as they will print"
              />
            </div>
          </div>

          <div className="rounded-xl border border-line bg-surface p-5 shadow-card">
            <h2 className="font-semibold text-slate-900">Closing</h2>
            <div className="mt-4 space-y-4">
              <DictationField
                label="Enclosure"
                singleLine
                value={draft.enclosure}
                onValueChange={set('enclosure')}
                placeholder="G.O Copy."
              />
              <DictationField
                label="Signature"
                rows={4}
                value={draft.signOff}
                onValueChange={set('signOff')}
                hint="Sits above the line, on the right"
              />
              <DictationField
                label="Copy to"
                rows={4}
                value={draft.copyTo}
                onValueChange={set('copyTo')}
                hint="Left off the letter entirely when empty"
              />
            </div>
          </div>
        </section>

        <section className="min-w-0 print:w-full">
          <p className="mb-3 text-xs uppercase tracking-wide text-slate-500 print:hidden">
            Preview — this is what prints
          </p>
          <LetterSheet letter={preview} />
        </section>
      </div>
    </AppShell>
  );
}
