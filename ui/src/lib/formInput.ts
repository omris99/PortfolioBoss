/** What the forms that write to the API share: today as the API sees it, reading what was typed, and the input style. */

/**
 * Today as 'yyyy-MM-dd' in the local time zone — the same "today" the API checks dates against. Not
 * `toISOString()`, which is UTC: just after midnight in Israel it is still yesterday there.
 */
export function localTodayIsoDate(): string {
  const today = new Date();
  const month = String(today.getMonth() + 1).padStart(2, '0');
  const day = String(today.getDate()).padStart(2, '0');
  return `${today.getFullYear()}-${month}-${day}`;
}

/** A date typed as the API takes it, 'yyyy-MM-dd', and a real day of the calendar. */
export function isIsoDate(typedText: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(typedText)) return false;
  const [year, month, day] = typedText.split('-').map(Number);
  // 2026-02-30 becomes March 2nd: a day that doesn't exist comes back as another.
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

/** A number input gives text, and an empty one means "not entered". */
export function isBlank(typedText: string): boolean {
  return typedText.trim() === '';
}

/** A quantity of shares: a whole number greater than 0, as the API requires. "2.5", "0" and "" are not. */
export function isPositiveWholeNumber(typedText: string): boolean {
  const typedNumber = Number(typedText);
  return !isBlank(typedText) && Number.isInteger(typedNumber) && typedNumber > 0;
}

/** A price: required, and 0 or more. "" and "-1" are not. */
export function isZeroOrMore(typedText: string): boolean {
  return !isBlank(typedText) && Number(typedText) >= 0;
}

/** The number typed, or `null` when the input was left empty. */
export function numberOrNull(typedText: string): number | null {
  return isBlank(typedText) ? null : Number(typedText);
}

/** "  Technology " → "Technology"; nothing but spaces → `null`: nothing was entered. */
export function trimmedOrNull(typedText: string): string | null {
  const trimmedText = typedText.trim();
  return trimmedText === '' ? null : trimmedText;
}

export const INPUT_CLASS =
  'h-8 rounded-md border border-strong bg-surface px-2 text-body text-ink placeholder:text-faint';
