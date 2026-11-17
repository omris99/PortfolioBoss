/**
 * A drawer whose open item is in the address — /positions/12, /recommendations/40 — so a link can open it and a refresh
 * keeps it open. Shared by the pages that open one.
 */

/** The open item's id, from the address's last part; `null` when there is none, or it is not a number. */
export function idFromAddress(idText: string | undefined): number | null {
  if (idText === undefined) return null;
  const id = Number(idText);
  return Number.isInteger(id) ? id : null;
}

/** Set when a drawer was opened from the page's own table: closing it then goes back, instead of adding a step. */
export interface OpenedFromTable {
  openedFromTable: true;
}

export const OPENED_FROM_TABLE: OpenedFromTable = { openedFromTable: true };

export function wasOpenedFromTable(state: unknown): boolean {
  return typeof state === 'object' && state !== null && (state as Partial<OpenedFromTable>).openedFromTable === true;
}
