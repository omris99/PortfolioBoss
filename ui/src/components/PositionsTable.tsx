import type { KeyboardEvent, ReactNode } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import {
  ACCOUNT_CURRENCY,
  EMPTY_VALUE,
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import { TABLE_CLASS, TAG_CLASS, tableRowClass } from '../lib/styles';
import type { Holding, HoldingSignal } from '../types/portfolio';
import { TargetUpside } from './AnalystTarget';
import type { ColumnChoice } from './ColumnsMenu';
import { NextEarningsDate } from './companyInfo/TimingNote';
import { SkeletonBar } from './firstLoad/SkeletonBar';
import { HelpPopover } from './HelpPopover';
import { HoldingPeriod } from './HoldingPeriod';
import { HoldingWarningMark } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
import { MomentumMark } from './Momentum';
import { SectorCell } from './SectorCell';
import { SignalCell, SignalCellLegend } from './Signal';

// ── sorting ─────────────────────────────────────────────────────────────────────────────────────

/** Uses the same words as the `aria-sort` attribute, so it can be passed straight to it. */
type SortDirection = 'ascending' | 'descending';

/** What a column sorts by: text (dates are 'yyyy-MM-dd', which sorts correctly as text) or a number. */
type SortValue = string | number;

export interface SortState {
  columnKey: string;
  direction: SortDirection;
}

/** Largest position first, like the console report. */
export const DEFAULT_SORT: SortState = { columnKey: 'marketValue', direction: 'descending' };

function compareSortValues(firstValue: SortValue, secondValue: SortValue): number {
  if (typeof firstValue === 'number' && typeof secondValue === 'number') {
    return firstValue - secondValue;
  }
  return String(firstValue).localeCompare(String(secondValue));
}

/**
 * Empty values always sink to the bottom, whichever way the column is sorted: the direction is applied
 * only to the values that exist. Flipping it afterwards would put every "—" at the top.
 */
function compareWithEmptyLast(
  firstValue: SortValue | null,
  secondValue: SortValue | null,
  direction: SortDirection,
): number {
  if (firstValue === null && secondValue === null) return 0;
  if (firstValue === null) return 1;
  if (secondValue === null) return -1;
  const directionMultiplier = direction === 'ascending' ? 1 : -1;
  return directionMultiplier * compareSortValues(firstValue, secondValue);
}

function isClosed(holding: Holding): boolean {
  return holding.status === 'CLOSED';
}

/** The rows in the table's order: open holdings first, closed ones below them, each sorted by `sortState`. */
export function sortHoldings(holdings: Holding[], sortState: SortState): Holding[] {
  const sortColumn = columnWithKey(sortState.columnKey);
  return [...holdings].sort(
    (first, second) =>
      Number(isClosed(first)) - Number(isClosed(second)) ||
      compareWithEmptyLast(sortColumn.sortValue(first), sortColumn.sortValue(second), sortState.direction),
  );
}

function sortStateAfterClicking(currentSort: SortState, clickedColumn: ColumnDefinition): SortState {
  if (currentSort.columnKey === clickedColumn.key) {
    const flippedDirection = currentSort.direction === 'ascending' ? 'descending' : 'ascending';
    return { columnKey: clickedColumn.key, direction: flippedDirection };
  }
  return { columnKey: clickedColumn.key, direction: clickedColumn.firstSortDirection };
}

// ── columns ─────────────────────────────────────────────────────────────────────────────────────

/** Everything about one column lives here: adding a column means adding one entry to {@link POSITION_COLUMNS}. */
interface ColumnDefinition extends ColumnChoice {
  /**
   * A column of two figures, one over the other, says both in its header, each line over its figure: "Avg cost" over
   * "Last". Without it the header is the title.
   */
  headerLines?: [string, string];
  alignment: 'left' | 'right';
  /** What the column sorts by; an empty value (`null`) always sorts last. */
  sortValue: (holding: Holding) => SortValue | null;
  /** The order on the first click: A→Z for text, oldest (or nearest) first for dates, largest first for numbers. */
  firstSortDirection: SortDirection;
  renderValue: (holding: Holding) => ReactNode;
  /** Text colour classes for the cell, on an open holding; a closed one's cells are all muted. */
  valueColorClass?: (holding: Holding) => string;
  /**
   * The first load's grey bars in this column's cells, row after row, round again after the last: each row's widths in
   * px, two for a cell of two lines (the sector over the industry, a figure over the one under it).
   */
  barWidthsWhileLoading: number[][];
}

/**
 * Two figures that belong together in one column, the first over the second (round 6, DESIGN_BRIEF.md §11.6, rule 3 —
 * so that the table fits its card with every column on): the second a size smaller, in `secondColorClass`.
 */
function TwoFigures({
  first,
  second,
  secondColorClass = 'text-muted',
}: {
  first: ReactNode;
  second: ReactNode;
  secondColorClass?: string;
}) {
  return (
    <span className="inline-block leading-[18px]">
      <span className="block">{first}</span>
      <span className={`block text-caption ${secondColorClass}`}>{second}</span>
    </span>
  );
}

/** The symbol, then its warning triangle and a closed holding's tag, wrapping under it in a narrow column. */
function SymbolWithStatus({ holding }: { holding: Holding }) {
  return (
    <span className="inline-flex max-w-[92px] flex-wrap items-center gap-x-1.5 gap-y-0.5">
      <span className="font-semibold">{holding.symbol}</span>
      <HoldingWarningMark warnings={holding.warnings} />
      {isClosed(holding) && <span className={TAG_CLASS}>closed</span>}
    </span>
  );
}

/**
 * IB's quantity, and under it how it divides when two investors or more hold some — "30 · 20", the account owner's part
 * first, only the investors holding some (never "0 · 30"). Hovering names them: "Omri 30 · Dana 20".
 */
function QuantityWithSplit({ holding }: { holding: Holding }) {
  const investors = useInvestors();
  const accountOwner = accountOwnerOf(investors);
  const partsHeld = holding.investorQuantities.filter((part) => part.quantity > 0);
  if (partsHeld.length < 2) return <>{formatQuantity(holding.position)}</>;

  const partsInOrder = [
    ...partsHeld.filter((part) => part.investorId === accountOwner?.id),
    ...partsHeld.filter((part) => part.investorId !== accountOwner?.id),
  ];
  const namedSplit = partsInOrder
    .map((part) => `${investorNameOf(investors, part.investorId)} ${formatQuantity(part.quantity)}`)
    .join(' · ');
  return (
    <span title={namedSplit}>
      <TwoFigures
        first={formatQuantity(holding.position)}
        second={partsInOrder.map((part) => formatQuantity(part.quantity)).join(' · ')}
        secondColorClass="text-faint"
      />
    </span>
  );
}

/** "18,726.00": no currency in the account's dollars (round 6); another currency's code small after it — "HKD". */
function MarketValue({ holding }: { holding: Holding }) {
  if (holding.marketValue === null || holding.currency === ACCOUNT_CURRENCY) return <>{formatMoney(holding.marketValue)}</>;
  return (
    <>
      {formatMoney(holding.marketValue)}
      <span className="ml-1 text-[11px] text-faint">{holding.currency}</span>
    </>
  );
}

/** The analysts' median target, its upside under it; "—" without one. A closed holding's is no longer read. */
function AnalystTargetFigures({ holding }: { holding: Holding }) {
  if (isClosed(holding) || holding.analystTarget.medianTarget === null) {
    return isClosed(holding) ? <>{EMPTY_VALUE}</> : <TargetUpside analystTarget={holding.analystTarget} />;
  }
  return (
    <TwoFigures
      first={formatMoney(holding.analystTarget.medianTarget)}
      second={<TargetUpside analystTarget={holding.analystTarget} />}
    />
  );
}

/** How far up a colour of the dot sorts the Signal column; the momentum score (0–5) is added within a colour. */
const SIGNAL_SORT_RANKS: Record<HoldingSignal, number> = { GREEN: 30, YELLOW: 20, RED: 10 };

/**
 * The dot first, then the momentum score within a colour: green 5/5 is 35, red 0/5 is 10. A holding with no dot yet sorts
 * by its score alone (0–5), after every coloured one; with neither, it is empty and sorts last.
 */
function signalSortValue(holding: Holding): number | null {
  const momentumScore = holding.momentum?.score ?? null;
  if (holding.signal === null) return momentumScore;
  return SIGNAL_SORT_RANKS[holding.signal] + (momentumScore ?? 0);
}

/**
 * Every column, in the table's order (round 6: Sector second, a Momentum column, the unrealized P&L one column, two
 * figures that belong together in one); `isShownByDefault` are the eight shown until the Columns menu says otherwise.
 * With every column on, the table fits its card from 1280px. The dates and the holding period are derived from the
 * trades entered by hand.
 */
export const POSITION_COLUMNS: ColumnDefinition[] = [
  {
    key: 'symbol',
    barWidthsWhileLoading: [[44], [52], [40], [48]],
    title: 'Symbol',
    isShownByDefault: true,
    isAlwaysShown: true,
    alignment: 'left',
    sortValue: (holding) => holding.symbol,
    firstSortDirection: 'ascending',
    renderValue: (holding) => <SymbolWithStatus holding={holding} />,
  },
  {
    key: 'sector',
    barWidthsWhileLoading: [[96, 76], [120, 96], [84, 68], [110, 88]],
    title: 'Sector',
    isShownByDefault: true,
    alignment: 'left',
    sortValue: (holding) => holding.sector,
    firstSortDirection: 'ascending',
    // A long pair of words wraps, never cut (round 6): "Consumer, Non-cyclical".
    renderValue: (holding) => (
      <span className="block max-w-[150px] whitespace-normal leading-[18px]">
        <SectorCell holding={holding} />
      </span>
    ),
  },
  {
    // The dot and the warning signs it found (AI_ANALYSIS_TODO.md, decision 15); the momentum has its own column.
    key: 'signal',
    barWidthsWhileLoading: [[150], [120], [140], [96]],
    title: 'Signal',
    isShownByDefault: true,
    alignment: 'left',
    sortValue: signalSortValue,
    firstSortDirection: 'descending',
    renderValue: (holding) => <SignalCell holding={holding} />,
  },
  {
    key: 'momentum',
    barWidthsWhileLoading: [[60], [64]],
    title: 'Momentum',
    isShownByDefault: true,
    alignment: 'left',
    sortValue: (holding) => holding.momentum?.score ?? null,
    firstSortDirection: 'descending',
    renderValue: (holding) => <MomentumMark momentum={holding.momentum} />,
  },
  {
    key: 'position',
    barWidthsWhileLoading: [[28], [36]],
    title: 'Qty',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.position,
    firstSortDirection: 'descending',
    renderValue: (holding) => <QuantityWithSplit holding={holding} />,
  },
  {
    key: 'marketValue',
    barWidthsWhileLoading: [[80], [72], [88]],
    title: 'Market value',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.marketValue,
    firstSortDirection: 'descending',
    renderValue: (holding) => <MarketValue holding={holding} />,
  },
  {
    // One column, the amount over its percent (the user's ask, round 6).
    key: 'unrealizedPnl',
    barWidthsWhileLoading: [[64, 48], [72, 52], [56, 48]],
    title: 'Unrealized P&L',
    headerLines: ['$ Unrealized P&L', 'Unrealized P&L %'],
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.unrealizedPnl,
    firstSortDirection: 'descending',
    renderValue: (holding) => (
      <TwoFigures
        first={formatSignedMoney(holding.unrealizedPnl)}
        second={formatSignedPercent(holding.unrealizedPnlPercent)}
        secondColorClass={isClosed(holding) ? 'text-muted' : profitLossColorClass(holding.unrealizedPnlPercent)}
      />
    ),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnl),
  },
  {
    key: 'holdingDays',
    barWidthsWhileLoading: [[40], [48], [36]],
    title: 'Held',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.holdingDays,
    firstSortDirection: 'descending',
    renderValue: (holding) => <HoldingPeriod days={holding.holdingDays} />,
  },
  {
    key: 'averageCostAndLast',
    barWidthsWhileLoading: [[56, 48], [64, 56]],
    title: 'Avg cost · Last',
    headerLines: ['Avg cost', 'Last'],
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.averageCost,
    firstSortDirection: 'descending',
    renderValue: (holding) => (
      <TwoFigures first={formatMoney(holding.averageCost)} second={formatMoney(holding.marketPrice)} />
    ),
  },
  // What a scanned stock has (MY_HOLDINGS_TODO.md, session 3): an open holding's only — a closed one's is no longer read.
  {
    key: 'analystTarget',
    barWidthsWhileLoading: [[56, 44], [64, 48]],
    title: 'Analysts’ target',
    headerLines: ['Analysts’ target', 'Upside'],
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => (isClosed(holding) ? null : holding.analystTarget.upsidePercent),
    firstSortDirection: 'descending',
    renderValue: (holding) => <AnalystTargetFigures holding={holding} />,
  },
  {
    key: 'earnings',
    barWidthsWhileLoading: [[72], [64]],
    title: 'Earnings',
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => (isClosed(holding) ? null : (holding.companyInfo?.nextEarningsDate?.date ?? null)),
    firstSortDirection: 'ascending',
    renderValue: (holding) =>
      isClosed(holding) ? EMPTY_VALUE : <NextEarningsDate companyInfo={holding.companyInfo} markPlacement="below" />,
  },
  {
    key: 'boughtAndLastSold',
    barWidthsWhileLoading: [[72, 72], [72, 64]],
    title: 'Bought · Last sold',
    headerLines: ['Bought', 'Last sold'],
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.firstBuyDate,
    firstSortDirection: 'ascending',
    renderValue: (holding) => (
      <TwoFigures first={formatDate(holding.firstBuyDate)} second={formatDate(holding.lastSellDate)} />
    ),
  },
];

function columnWithKey(columnKey: string): ColumnDefinition {
  return POSITION_COLUMNS.find((column) => column.key === columnKey) ?? POSITION_COLUMNS[0];
}

function alignmentClass(column: ColumnDefinition): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

// ── table ───────────────────────────────────────────────────────────────────────────────────────

/**
 * The cells of this table (round 6): 6px apart so that every column fits 1280px, rows of 56px for the cells of two
 * lines; the Symbol column stays put if the table must still scroll sideways — only a fallback.
 */
const HEADER_CELL_CLASS =
  'h-11 whitespace-nowrap border-b bg-subtle px-1.5 text-label font-medium text-muted first:sticky first:left-0 ' +
  'first:z-[1] first:pl-5 last:pr-5';
const CELL_CLASS = 'h-14 whitespace-nowrap border-b px-1.5 first:pl-5 last:pr-5';

/** The Symbol cell's own fill, so that the columns scrolling under it don't show through: the row's, on hover too. */
function symbolCellFillClass(isSelected: boolean): string {
  return `sticky left-0 z-[1] ${isSelected ? 'bg-selected' : 'bg-surface group-hover:bg-subtle'}`;
}

/** The title, or the two lines of a column of two figures, each line over its figure. */
function HeaderText({ column }: { column: ColumnDefinition }) {
  if (column.headerLines === undefined) return <>{column.title}</>;
  const alignItemsClass = column.alignment === 'right' ? 'items-end' : 'items-start';
  return (
    <span className={`inline-flex flex-col leading-[15px] ${alignItemsClass}`}>
      <span>{column.headerLines[0]}</span>
      <span>{column.headerLines[1]}</span>
    </span>
  );
}

function SortableHeaderCell({
  column,
  sortState,
  onSort,
}: {
  column: ColumnDefinition;
  sortState: SortState;
  onSort: (sortState: SortState) => void;
}) {
  const isSortedByThisColumn = sortState.columnKey === column.key;
  return (
    <th
      aria-sort={isSortedByThisColumn ? sortState.direction : 'none'}
      className={`${HEADER_CELL_CLASS} ${alignmentClass(column)}`}
    >
      <span className="inline-flex items-center gap-0.5">
        <button
          type="button"
          onClick={() => onSort(sortStateAfterClicking(sortState, column))}
          className={`-mx-1 inline-flex min-h-7 items-center gap-1 rounded-sm px-1 transition-colors hover:text-ink ${
            isSortedByThisColumn ? 'text-ink' : ''
          }`}
        >
          <HeaderText column={column} />
          {isSortedByThisColumn &&
            (sortState.direction === 'ascending' ? (
              <ArrowUp size={12} strokeWidth={2.5} />
            ) : (
              <ArrowDown size={12} strokeWidth={2.5} />
            ))}
        </button>
        {column.key === 'signal' && (
          <HelpPopover label="How to read the Signal column" align="start">
            <SignalCellLegend />
          </HelpPopover>
        )}
      </span>
    </th>
  );
}

function HoldingRow({
  holding,
  columns,
  isSelected,
  onOpen,
}: {
  holding: Holding;
  columns: ColumnDefinition[];
  isSelected: boolean;
  onOpen: () => void;
}) {
  const openOnEnter = (event: KeyboardEvent<HTMLTableRowElement>) => {
    if (event.key === 'Enter') onOpen();
  };
  return (
    <tr
      tabIndex={0}
      aria-label={`${holding.symbol}, open details`}
      onClick={onOpen}
      onKeyDown={openOnEnter}
      className={`group ${tableRowClass(isSelected)}`}
    >
      {columns.map((column, columnIndex) => {
        // A closed holding: every cell muted, not faded, so it still reads well; its figures are "—" anyway.
        const colorClass = isClosed(holding) ? 'text-muted' : (column.valueColorClass?.(holding) ?? '');
        const fillClass = columnIndex === 0 ? symbolCellFillClass(isSelected) : '';
        return (
          <td key={column.key} className={`${CELL_CLASS} ${alignmentClass(column)} ${colorClass} ${fillClass}`}>
            {column.renderValue(holding)}
          </td>
        );
      })}
    </tr>
  );
}

/** The columns the Columns menu shows, in the table's own order. */
function visibleColumnsOf(visibleColumnKeys: string[]): ColumnDefinition[] {
  return POSITION_COLUMNS.filter((column) => visibleColumnKeys.includes(column.key));
}

/** The table's frame — it scrolls sideways only as a fallback —, its head, and `body`. */
function TableWithHead({
  columns,
  sortState,
  onSort,
  body,
}: {
  columns: ColumnDefinition[];
  sortState: SortState;
  onSort: (sortState: SortState) => void;
  body: ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table className={TABLE_CLASS}>
        <thead>
          <tr>
            {columns.map((column) => (
              <SortableHeaderCell key={column.key} column={column} sortState={sortState} onSort={onSort} />
            ))}
          </tr>
        </thead>
        {body}
      </table>
    </div>
  );
}

/**
 * One row per holding, in the order the page sorted them (`rows`); clicking a row, or Enter on it, opens the holding's
 * drawer (`onOpen`), and the open one is `selectedHoldingId`. Only `visibleColumnKeys` are shown, in the table's own
 * order.
 */
export function PositionsTable({
  rows,
  visibleColumnKeys,
  sortState,
  onSort,
  selectedHoldingId,
  onOpen,
}: {
  rows: Holding[];
  visibleColumnKeys: string[];
  sortState: SortState;
  onSort: (sortState: SortState) => void;
  selectedHoldingId: number | null;
  onOpen: (holdingId: number) => void;
}) {
  const columns = visibleColumnsOf(visibleColumnKeys);
  return (
    <TableWithHead
      columns={columns}
      sortState={sortState}
      onSort={onSort}
      body={
        // id, not symbol: two holdings can share a symbol, and React needs a unique key per row.
        <tbody className="[&>tr:last-child>td]:border-b-0">
          {rows.map((holding) => (
            <HoldingRow
              key={holding.id}
              holding={holding}
              columns={columns}
              isSelected={holding.id === selectedHoldingId}
              onOpen={() => onOpen(holding.id)}
            />
          ))}
        </tbody>
      }
    />
  );
}

/** How many grey rows the table shows during the first load. */
const ROWS_WHILE_LOADING = 8;

/** A cell's grey bars in `rowIndex`'s row: one, or two one over the other, on the column's side. */
function CellBarsWhileLoading({ column, rowIndex }: { column: ColumnDefinition; rowIndex: number }) {
  const [firstLineWidth, secondLineWidth] =
    column.barWidthsWhileLoading[rowIndex % column.barWidthsWhileLoading.length];
  if (secondLineWidth === undefined) return <SkeletonBar width={firstLineWidth} height={12} />;
  const alignItemsClass = column.alignment === 'right' ? 'items-end' : 'items-start';
  return (
    <span className={`inline-flex flex-col gap-1.5 align-middle ${alignItemsClass}`}>
      <SkeletonBar width={firstLineWidth} height={12} />
      <SkeletonBar width={secondLineWidth} height={10} />
    </span>
  );
}

/**
 * The same table during the first load (round 6): its head as the Columns menu has it, then eight rows of grey bars in
 * the rows' own cells, so they stand as tall as the rows that replace them.
 */
export function PositionsTableSkeleton({
  visibleColumnKeys,
  sortState,
  onSort,
}: {
  visibleColumnKeys: string[];
  sortState: SortState;
  onSort: (sortState: SortState) => void;
}) {
  const columns = visibleColumnsOf(visibleColumnKeys);
  const rowIndexes = Array.from({ length: ROWS_WHILE_LOADING }, (_, rowIndex) => rowIndex);
  return (
    <TableWithHead
      columns={columns}
      sortState={sortState}
      onSort={onSort}
      body={
        <tbody aria-busy="true" className="[&>tr:last-child>td]:border-b-0">
          {rowIndexes.map((rowIndex) => (
            <tr key={rowIndex}>
              {columns.map((column, columnIndex) => (
                <td
                  key={column.key}
                  className={`${CELL_CLASS} ${alignmentClass(column)} ${columnIndex === 0 ? symbolCellFillClass(false) : ''}`}
                >
                  <CellBarsWhileLoading column={column} rowIndex={rowIndex} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      }
    />
  );
}
