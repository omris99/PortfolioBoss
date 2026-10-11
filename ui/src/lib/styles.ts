/**
 * The design system's recurring pieces as Tailwind classes — buttons, fields, panels, tables, tags — so each looks the
 * same everywhere it appears. The colors are the roles of tailwind.config.js.
 */

const BUTTON_BASE_CLASS =
  'inline-flex h-8 items-center justify-center gap-2 whitespace-nowrap rounded-md border px-3 text-body font-semibold ' +
  'transition-colors disabled:cursor-default disabled:opacity-60';

/** The main action of a place: save, add, analyze. */
export const PRIMARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} border-accent bg-accent text-on-accent hover:brightness-110`;

/** Everything else: cancel, retry, show and hide. */
export const SECONDARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} border-strong bg-surface text-ink hover:bg-subtle`;

/** A button that deletes. */
export const DANGER_BUTTON_CLASS = `${BUTTON_BASE_CLASS} border-strong bg-surface text-negative hover:bg-negative-soft`;

const ICON_BUTTON_BASE_CLASS =
  'inline-flex h-6 w-6 items-center justify-center rounded-full text-faint transition-colors disabled:opacity-50';

/** A small round icon button: edit, rename, open a row. */
export const ICON_BUTTON_CLASS = `${ICON_BUTTON_BASE_CLASS} hover:bg-accent-soft hover:text-accent`;

/** The same, for deleting. */
export const DELETE_ICON_BUTTON_CLASS = `${ICON_BUTTON_BASE_CLASS} hover:bg-negative-soft hover:text-negative`;

/** One of two choices side by side — BUY or SELL, deposit or withdrawal — and whether it is the one chosen. */
export function toggleButtonClass(isSelected: boolean): string {
  const stateClass = isSelected ? 'border-accent bg-accent-soft text-accent' : 'border-strong bg-surface text-muted hover:text-ink';
  return `h-8 rounded-md border px-3 text-label font-semibold transition-colors ${stateClass}`;
}

/** A form field's label, with the input under it. */
export const FIELD_LABEL_CLASS = 'flex flex-col gap-1 text-label text-muted';

/** A box inside an expanded row: the momentum, the analysis, the trades, a form. */
export const PANEL_CLASS = 'flex flex-col gap-3 rounded-lg border bg-page p-4';

/** The heading of such a box. */
export const PANEL_TITLE_CLASS = 'text-body font-semibold text-ink';

/** A card on the page: the positions, the closed positions, an investor. */
export const CARD_CLASS = 'rounded-lg border bg-surface';

/**
 * A table of figures. A little under the design's 14px body for now, until the tables get their own design: at 14px the
 * positions table's twelve columns no longer fit the page.
 */
export const TABLE_CLASS = 'w-full border-collapse text-[13px] leading-5';

/** A table's header row. */
export const TABLE_HEAD_ROW_CLASS = 'border-b bg-subtle text-label text-muted';

/** An ordinary row: a hairline under it, and a fill on hover. */
export const TABLE_ROW_CLASS = 'border-b transition-colors last:border-b-0 hover:bg-subtle';

/** A grey tag beside a name: "closed", "manual", "account owner". */
export const TAG_CLASS =
  'inline-flex h-[18px] items-center whitespace-nowrap rounded-sm bg-subtle px-1.5 text-caption font-medium text-muted';

/** A small colored label, its colors given with it: a rating, a trend, BUY or SELL. */
export const CHIP_CLASS = 'inline-flex h-[18px] items-center whitespace-nowrap rounded-sm px-1.5 text-caption font-semibold';

/** What went wrong, under the form or list it belongs to. */
export const ERROR_TEXT_CLASS = 'text-caption text-negative';

/** A short explanation under something. */
export const HINT_TEXT_CLASS = 'text-caption text-muted';
