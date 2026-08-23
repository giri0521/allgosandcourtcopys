/**
 * The date as an office letter writes it: 22.08.2026.
 *
 * <p>Not the application's usual "22 Aug 2026" format. Every letter in the sample correspondence
 * dates itself this way, and a letter is a document that leaves the building — it should read like
 * the ones beside it in the file, not like this system.
 *
 * <p>The input is the server's ISO date (`2026-08-22`), parsed by hand rather than through `Date`:
 * `new Date('2026-08-22')` is midnight UTC, which in Asia/Kolkata is still the 22nd but in any
 * timezone behind UTC is the 21st — a letter dated a day early is a real problem.
 */
export function formatLetterDate(iso: string | null): string {
  if (!iso) return '';

  const [year, month, day] = iso.split('-');
  if (!year || !month || !day) return iso;

  return `${day}.${month}.${year}`;
}

/** Today, as the date input and the server both want it. */
export function todayIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * The From block a letter starts with, built from the account.
 *
 * <p>Only a starting point: it is an editable field on the letter, so anybody sending on behalf of
 * somebody else can correct it without their profile changing.
 */
export function defaultFromBlock(user: {
  fullName: string;
  designation: string | null;
  departmentName: string | null;
  officeAddress: string | null;
}): string {
  return [user.fullName, user.designation, user.departmentName, user.officeAddress]
    .map((line) => line?.trim())
    .filter((line): line is string => Boolean(line))
    .join(',\n');
}
