import { describe, expect, it } from 'vitest';
import { appendTranscript } from '@/lib/dictation';

/**
 * The joining rule, which is the only part of dictation that can be tested without a microphone —
 * and the part that would quietly ruin a letter, by running two sentences together or dropping a
 * paragraph break somebody put in on purpose.
 */
describe('appendTranscript', () => {
  it('starts the field with the first thing said', () => {
    expect(appendTranscript('', 'Kind attention is invited to the references cited.')).toBe(
      'Kind attention is invited to the references cited.',
    );
  });

  it('puts a space between what is there and what was said', () => {
    expect(appendTranscript('Kind attention is invited.', 'The meeting is on Friday.')).toBe(
      'Kind attention is invited. The meeting is on Friday.',
    );
  });

  it('does not add a second space where the writer already left one', () => {
    expect(appendTranscript('Kind attention is invited. ', 'The meeting is on Friday.')).toBe(
      'Kind attention is invited. The meeting is on Friday.',
    );
  });

  /* A blank line is a paragraph break in a letter: this is the case that matters most, because
     swallowing it merges two paragraphs in a document that has already been printed. */
  it('keeps a paragraph break intact', () => {
    expect(appendTranscript('First paragraph.\n\n', 'Second paragraph.')).toBe(
      'First paragraph.\n\nSecond paragraph.',
    );
  });

  it('ignores a phrase that was only silence', () => {
    expect(appendTranscript('Unchanged.', '   ')).toBe('Unchanged.');
    expect(appendTranscript('Unchanged.', '')).toBe('Unchanged.');
  });

  it('trims what the recogniser padded, so the spacing is ours rather than its', () => {
    expect(appendTranscript('One.', '  Two.  ')).toBe('One. Two.');
  });
});
