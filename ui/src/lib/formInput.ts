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

/** A number input gives text, and an empty one means "not entered". */
export function isBlank(typedText: string): boolean {
  return typedText.trim() === '';
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
  'rounded-md border border-slate-700 bg-slate-900 px-2 py-1 text-xs text-slate-100 focus:border-emerald-500 focus:outline-none';
