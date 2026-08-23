import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Dictation, through the browser's own speech recognition.
 *
 * <p><b>Where the audio goes.</b> Chrome and Edge do not recognise speech on the machine — they
 * stream the microphone to the browser vendor's servers and send back the text. For a letter about
 * a meeting that is unremarkable; for one about a person it is worth knowing, and it is why the
 * button says nothing about being "offline" or "private". Nothing is sent anywhere by this
 * application: the recognised text arrives in the field and is saved only when the letter is.
 *
 * <p>Firefox implements none of this, so the button is absent there rather than broken.
 */

/** The two languages this office writes in. Tamil first is deliberate on a Tamil Nadu system. */
export const DICTATION_LANGUAGES = [
  { code: 'ta-IN', label: 'தமிழ் (Tamil)' },
  { code: 'en-IN', label: 'English (India)' },
] as const;

export type DictationLanguage = (typeof DICTATION_LANGUAGES)[number]['code'];

/**
 * Adds recognised speech to what is already in the field.
 *
 * <p>Dictation adds to a letter rather than replacing it: somebody types a paragraph, dictates the
 * next, and types again. The joining is the fiddly part and the reason this is a function of its
 * own rather than a line inside the hook — it is the only piece here that can be tested without a
 * microphone.
 *
 * <p>A space goes in between, except where the existing text already ends in one, ends in a line
 * break the writer put there on purpose, or is empty.
 */
export function appendTranscript(current: string, addition: string): string {
  const spoken = addition.trim();
  if (!spoken) return current;
  if (!current) return spoken;

  const endsWithSpace = /\s$/.test(current);
  return endsWithSpace ? current + spoken : `${current} ${spoken}`;
}

/* The API is not in TypeScript's DOM library, and the two vendor spellings differ. Only the parts
   actually used are declared — a full transcription of the spec would be a fiction nobody checks. */
interface RecognitionAlternative {
  transcript: string;
}
interface RecognitionResult {
  0: RecognitionAlternative;
  isFinal: boolean;
}
interface RecognitionEvent {
  resultIndex: number;
  results: { length: number; [index: number]: RecognitionResult };
}
interface RecognitionErrorEvent {
  error: string;
}
interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  start: () => void;
  stop: () => void;
  abort: () => void;
  onresult: ((event: RecognitionEvent) => void) | null;
  onerror: ((event: RecognitionErrorEvent) => void) | null;
  onend: (() => void) | null;
}
type RecognitionConstructor = new () => SpeechRecognitionLike;

function recognitionConstructor(): RecognitionConstructor | null {
  if (typeof window === 'undefined') return null;
  const candidate =
    (window as unknown as { SpeechRecognition?: RecognitionConstructor }).SpeechRecognition ??
    (window as unknown as { webkitSpeechRecognition?: RecognitionConstructor })
      .webkitSpeechRecognition;
  return candidate ?? null;
}

/** Whether this browser can dictate at all. Checked once, at module scope, since it cannot change. */
export const dictationSupported = recognitionConstructor() !== null;

const MESSAGES: Record<string, string> = {
  'not-allowed': 'The microphone is blocked. Allow it for this site in the address bar, then try again.',
  'service-not-allowed': 'The microphone is blocked for this site.',
  'no-speech': 'Nothing was heard. Try again, closer to the microphone.',
  network: 'Speech recognition needs a connection, and could not reach it.',
  aborted: '',
};

/**
 * Starts and stops dictation, handing finished phrases to `onText`.
 *
 * <p>Only *final* phrases are committed. The interim guesses are returned separately for the
 * button to show, because they change with every syllable — writing them into the field would put
 * text in a letter and then take it away again while somebody is reading it.
 */
export function useDictation({
  lang,
  onText,
}: {
  lang: DictationLanguage;
  onText: (text: string) => void;
}) {
  const [listening, setListening] = useState(false);
  const [interim, setInterim] = useState('');
  const [error, setError] = useState<string | null>(null);

  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  /* The callback changes on every render of the form it belongs to; the recognition object is set
     up once and would otherwise close over the first one and write into a stale draft. Kept current
     in an effect rather than assigned during render, which React reserves for values it can see. */
  const onTextRef = useRef(onText);
  useEffect(() => {
    onTextRef.current = onText;
  }, [onText]);

  const stop = useCallback(() => {
    recognitionRef.current?.stop();
    setListening(false);
    setInterim('');
  }, []);

  const start = useCallback(() => {
    const Recognition = recognitionConstructor();
    if (!Recognition) return;

    recognitionRef.current?.abort();
    setError(null);
    setInterim('');

    const recognition = new Recognition();
    recognition.lang = lang;
    // Keeps going between sentences rather than stopping at the first pause, which is what a
    // paragraph of dictation needs.
    recognition.continuous = true;
    recognition.interimResults = true;

    recognition.onresult = (event) => {
      let settled = '';
      let pending = '';

      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index];
        if (result.isFinal) {
          settled += result[0].transcript;
        } else {
          pending += result[0].transcript;
        }
      }

      if (settled.trim()) onTextRef.current(settled.trim());
      setInterim(pending);
    };

    recognition.onerror = (event) => {
      const message = MESSAGES[event.error];
      // An empty string is a deliberate silence: `aborted` fires whenever we stop it ourselves.
      setError(message === undefined ? 'Dictation stopped unexpectedly.' : message || null);
      setListening(false);
      setInterim('');
    };

    recognition.onend = () => {
      setListening(false);
      setInterim('');
    };

    recognitionRef.current = recognition;
    recognition.start();
    setListening(true);
  }, [lang]);

  /* Leaving the screen with the microphone still open is the one failure worth guarding: the tab
     goes on listening with nowhere to put what it hears. */
  useEffect(() => () => recognitionRef.current?.abort(), []);

  return { supported: dictationSupported, listening, interim, error, start, stop };
}
