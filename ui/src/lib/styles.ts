/**
 * The design system's recurring pieces as Tailwind classes — buttons, fields, tables, tags, chips — so each looks the
 * same everywhere it appears. The colors are the roles of tailwind.config.js.
 */

// ── buttons ─────────────────────────────────────────────────────────────────────────────────────

const BUTTON_BASE_CLASS =
  'inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded-md border font-semibold transition-colors ' +
  'disabled:cursor-default';

const BUTTON_SIZE_CLASS = 'h-8 gap-2 px-3 text-body';

/** A smaller button inside a drawer or a card: "Add trade", "Analyze MSFT". */
const SMALL_BUTTON_SIZE_CLASS = 'h-7 gap-1.5 px-2 text-[13px]';

const PRIMARY_COLORS_CLASS =
  'border-accent bg-accent text-on-accent hover:brightness-110 disabled:border-accent-soft disabled:bg-accent-soft ' +
  'disabled:text-accent disabled:hover:brightness-100';

const SECONDARY_COLORS_CLASS = 'border-strong bg-surface text-ink hover:bg-subtle disabled:text-muted';

/** The main action of a place: save, analyze, the first trade. */
export const PRIMARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} ${BUTTON_SIZE_CLASS} ${PRIMARY_COLORS_CLASS}`;
export const SMALL_PRIMARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} ${SMALL_BUTTON_SIZE_CLASS} ${PRIMARY_COLORS_CLASS}`;

/** Everything else: cancel, retry, add, columns. */
export const SECONDARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} ${BUTTON_SIZE_CLASS} ${SECONDARY_COLORS_CLASS}`;
export const SMALL_SECONDARY_BUTTON_CLASS = `${BUTTON_BASE_CLASS} ${SMALL_BUTTON_SIZE_CLASS} ${SECONDARY_COLORS_CLASS}`;

/** The confirm dialog's Delete: the only red fill in the app. */
export const DANGER_BUTTON_CLASS =
  `${BUTTON_BASE_CLASS} ${BUTTON_SIZE_CLASS} border-negative bg-negative text-on-negative hover:brightness-110 ` +
  'disabled:border-negative-soft disabled:bg-negative-soft disabled:text-negative';

/** A delete that still asks first, outlined in red: "Delete position". */
export const QUIET_DANGER_BUTTON_CLASS =
  `${BUTTON_BASE_CLASS} ${BUTTON_SIZE_CLASS} border-negative bg-surface text-negative hover:bg-negative-soft`;

const ICON_BUTTON_BASE_CLASS =
  'inline-flex shrink-0 items-center justify-center transition-colors disabled:cursor-default disabled:opacity-50';

/** A small round icon button: edit, rename, "?". */
export const ICON_BUTTON_CLASS =
  `${ICON_BUTTON_BASE_CLASS} h-6 w-6 rounded-full text-faint hover:bg-accent-soft hover:text-accent`;

/** The same, for deleting: red on hover. */
export const DELETE_ICON_BUTTON_CLASS =
  `${ICON_BUTTON_BASE_CLASS} h-6 w-6 rounded-full text-faint hover:bg-negative-soft hover:text-negative`;

/** A larger square icon button: a drawer's ‹ › ✕, a modal's ✕. */
export const LARGE_ICON_BUTTON_CLASS =
  `${ICON_BUTTON_BASE_CLASS} h-8 w-8 rounded-md text-muted hover:bg-accent-soft hover:text-accent`;

/** A button that reads as a link: "Details →", "Reset to default". */
export const LINK_BUTTON_CLASS =
  'inline-flex items-center gap-1 rounded-sm text-caption font-medium text-accent underline-offset-2 hover:underline';

// ── cards, tags, chips ──────────────────────────────────────────────────────────────────────────

/** A card on the page. */
export const CARD_CLASS = 'rounded-lg border bg-surface';

/** A grey tag beside a name: "closed", "manual", "account owner". */
export const TAG_CLASS =
  'inline-flex h-[18px] items-center whitespace-nowrap rounded-sm bg-subtle px-1.5 text-caption font-medium text-muted';

/** The number beside a card's title: how many items it lists. */
export const COUNT_CHIP_CLASS =
  'inline-flex h-5 min-w-[22px] items-center justify-center rounded-sm bg-subtle px-1.5 text-caption font-semibold';

/** A small word in a tone, its colors given with it: a rating, a trend, STRONG 5/5. */
export const CHIP_CLASS =
  'inline-flex h-5 items-center gap-1 whitespace-nowrap rounded-sm px-1.5 text-[11px] font-semibold leading-4 ' +
  'tracking-[0.03em]';

/** BUY and SELL, DEPOSIT and WITHDRAWAL in a list: small capitals, never green or red. */
export function sideTextClass(isSecondSide: boolean): string {
  return `text-[11px] font-semibold tracking-[0.03em] ${isSecondSide ? 'text-muted' : 'text-ink'}`;
}

/** A figure or text whose hover explains it: dotted underline, help cursor. */
export const EXPLAINED_CLASS = 'cursor-help underline decoration-line-strong decoration-dotted underline-offset-[3px]';

// ── tables ──────────────────────────────────────────────────────────────────────────────────────

/** A table on a page, in its card: a header on `subtle`, 44px rows, the outer cells padded like the card. */
export const TABLE_CLASS = 'w-full border-collapse';
export const TABLE_HEADER_CELL_CLASS =
  'h-9 whitespace-nowrap border-b bg-subtle px-3 text-left text-label text-muted first:pl-5 last:pr-5';
export const TABLE_CELL_CLASS = 'h-11 whitespace-nowrap border-b px-3 first:pl-5 last:pr-5';

/** A clickable row that opens a drawer: hover `subtle`, the open one `selected`. */
export function tableRowClass(isSelected: boolean): string {
  const fillClass = isSelected ? 'bg-selected' : 'hover:bg-subtle';
  return `cursor-pointer outline-none focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent ${fillClass}`;
}

/** A small table inside a drawer or a card, framed: trades, deposits, by investor. */
export const INNER_TABLE_FRAME_CLASS = 'overflow-hidden rounded-lg border';
export const INNER_TABLE_CLASS = 'w-full border-collapse text-[13px]';
export const INNER_HEADER_CELL_CLASS =
  'h-9 whitespace-nowrap border-b bg-subtle px-1.5 text-left text-label text-muted first:pl-3 last:pr-3';
export const INNER_CELL_CLASS = 'h-10 border-b px-1.5 first:pl-3 last:pr-3 [tr:last-child_&]:border-b-0';

// ── forms ───────────────────────────────────────────────────────────────────────────────────────

/** The two-way choice of a form — BUY or SELL, DEPOSIT or WITHDRAWAL — as one segmented control. */
export const TOGGLE_GROUP_CLASS = 'inline-flex h-8 gap-0.5 self-start rounded-md bg-subtle p-0.5';
export function toggleOptionClass(isSelected: boolean): string {
  const stateClass = isSelected
    ? 'bg-surface text-ink shadow-[inset_0_0_0_1px_var(--color-border-strong)]'
    : 'text-muted hover:text-ink';
  return `min-w-[72px] rounded-sm px-3 text-caption font-semibold tracking-[0.03em] ${stateClass}`;
}

/** A figure typed into a field: right-aligned, in tabular digits. */
export const NUMBER_INPUT_CLASS = 'text-right tabular-nums';

/** A short explanation under something. */
export const HINT_TEXT_CLASS = 'text-caption text-muted';
