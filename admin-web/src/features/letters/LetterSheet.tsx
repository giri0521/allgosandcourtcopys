import type { Letter } from '@/types/api';
import { formatLetterDate } from '@/features/letters/format';

/**
 * The letter as it appears on paper.
 *
 * <p>This is the only thing that prints. `@media print` in index.css hides the application around
 * it and lets this sheet fill the page, so what somebody sees on screen is what comes out of the
 * printer or the browser's Save as PDF — there is no second rendering to drift from the first.
 *
 * <p>The shape follows the office's own letters: the sender on the left and the recipients on the
 * right, both at the top; then the file number and date on one line; then the salutation, the
 * subject and the reference, the body, and finally the enclosure, the signature and who is copied.
 * Anything the writer left empty simply does not appear, which is why every block below is
 * conditional rather than rendered as a blank heading.
 *
 * <p>Every block is `whitespace-pre-line`: an address and a list of recipients are typed with their
 * own line breaks, and a letter that reflowed them into a paragraph would be wrong in a way the
 * writer could not correct.
 */
export function LetterSheet({ letter }: { letter: Letter }) {
  return (
    <article
      className="letter-sheet mx-auto w-full max-w-[210mm] rounded-xl border border-line bg-surface
        px-10 py-12 text-slate-900 shadow-card print:max-w-none print:rounded-none print:border-0
        print:px-0 print:py-0 print:shadow-none"
    >
      <div className="grid grid-cols-1 gap-8 sm:grid-cols-2">
        <section>
          <h2 className="font-semibold">From,</h2>
          <p className="mt-2 whitespace-pre-line leading-relaxed">{letter.fromBlock}</p>
        </section>
        <section>
          <h2 className="font-semibold">To,</h2>
          <p className="mt-2 whitespace-pre-line leading-relaxed">{letter.toBlock}</p>
        </section>
      </div>

      {(letter.referenceNo || letter.letterDate) && (
        <div className="mt-10 flex flex-wrap items-baseline justify-center gap-x-10 gap-y-1 font-semibold">
          {letter.referenceNo && <span>Lr.No.{letter.referenceNo}</span>}
          {letter.letterDate && <span>Dated: {formatLetterDate(letter.letterDate)}</span>}
        </div>
      )}

      {letter.salutation && <p className="mt-8">{letter.salutation}</p>}

      {/* Sub: and Ref: hang, the way they do on the paper originals — the label sits in the margin
          and the text lines up under itself rather than wrapping beneath the label. */}
      {letter.subject && (
        <p className="mt-6 pl-16 -indent-16 leading-relaxed sm:pl-20 sm:-indent-20">
          <span className="font-semibold">Sub:&nbsp;&nbsp;</span>
          <span className="whitespace-pre-line">{letter.subject}</span>
        </p>
      )}

      {letter.reference && (
        <p className="mt-4 pl-16 -indent-16 leading-relaxed sm:pl-20 sm:-indent-20">
          <span className="font-semibold">Ref:&nbsp;&nbsp;</span>
          <span className="whitespace-pre-line">{letter.reference}</span>
        </p>
      )}

      {(letter.subject || letter.reference) && (
        <p aria-hidden className="mt-6 text-center tracking-[0.3em]">
          *******
        </p>
      )}

      <div className="mt-6 whitespace-pre-line leading-loose">{letter.body}</div>

      {letter.enclosure && (
        <p className="mt-8">
          <span className="font-semibold">Encl:</span> {letter.enclosure}
        </p>
      )}

      {letter.signOff && (
        <div className="mt-12 whitespace-pre-line text-right leading-relaxed">{letter.signOff}</div>
      )}

      {letter.copyTo && (
        <div className="mt-12">
          <h2 className="font-semibold underline">Copy to.</h2>
          <p className="mt-2 whitespace-pre-line leading-relaxed">{letter.copyTo}</p>
        </div>
      )}
    </article>
  );
}
