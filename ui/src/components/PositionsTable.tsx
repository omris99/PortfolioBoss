import type { KeyboardEvent, ReactNode } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import {
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import {
  TABLE_CELL_CLASS,
  TABLE_CLASS,
  TABLE_HEADER_CELL_CLASS,
  TAG_CLASS,
  tableRowClass,
} from '../lib/styles';
import type { Holding, HoldingSignal } from '../types/portfolio';
import type { ColumnChoice } from './ColumnsMenu';
import { HelpPopover } from './HelpPopover';
import { HoldingPeriod } from './HoldingPeriod';
import { HoldingWarningMark } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
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

/** What a cell can do beyond showing a value: an editable cell reloads the portfolio after saving. */
interface TableActions {
  onDataChanged: () => Promise<void>;
}

/** Everything about one column lives here: adding a column means adding one entry to {@link POSITION_COLUMNS}. */
interface ColumnDefinition extends ColumnChoice {
  alignment: 'left' | 'right';
  /** What the column sorts by; an empty value (`null`) always sorts last. */
  sortValue: (holding: Holding) => SortValue | null;
  /** The order on the first click: A→Z for text, oldest first for dates, largest first for numbers. */
  firstSortDirection: SortDirection;
  renderValue: (holding: Holding, tableActions: TableActions) => ReactNode;
  /** Text colour classes for the cell, on an open holding; a closed one's cells are all muted. */
  valueColorClass?: (holding: Holding) => string;
}

function SymbolWithStatus({ holding }: { holding: Holding }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="font-semibold">{holding.symbol}</span>
      <HoldingWarningMark warnings={holding.warnings} />
      {isClosed(holding) && <span className={TAG_CLASS}>closed</span>}
    </span>
  );
}

/**
 * "39 (15 · 24)": IB's quantity, and how it divides — the account owner's part first, then each other investor's.
 * Hovering names them: "Me 15 · Avi 24". Just "39" while nobody but the account owner holds any of it.
 */
function QuantityWithSplit({ holding }: { holding: Holding }) {
  const investors = useInvestors();
  const accountOwner = accountOwnerOf(investors);
  const otherInvestorsParts = holding.investorQuantities.filter((part) => part.investorId !== accountOwner?.id);
  if (accountOwner === null || otherInvestorsParts.length === 0) return <>{formatQuantity(holding.position)}</>;

  // 0 when the others hold all of it: the account owner's part is still shown, so the split reads the same way.
  const accountOwnerQuantity =
    holding.investorQuantities.find((part) => part.investorId === accountOwner.id)?.quantity ?? 0;
  const parts = [{ investorId: accountOwner.id, quantity: accountOwnerQuantity }, ...otherInvestorsParts];
  const split = parts.map((part) => formatQuantity(part.quantity)).join(' · ');
  const namedSplit = parts
    .map((part) => `${investorNameOf(investors, part.investorId)} ${formatQuantity(part.quantity)}`)
    .join(' · ');
  return (
    <span title={namedSplit}>
      {formatQuantity(holding.position)} <span className="text-faint">({split})</span>
    </span>
  );
}

/** "18,726.00 USD", the currency small: holdings in other currencies don't add up with these. */
function MarketValue({ holding }: { holding: Holding }) {
  if (holding.marketValue === null) return <>{formatMoney(null)}</>;
  return (
    <>
      {formatMoney(holding.marketValue)}
      <span className="ml-1 text-[11px] text-faint">{holding.currency}</span>
    </>
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
 * Every column, in the table's order; `isShownByDefault` are the eight shown until the Columns menu says otherwise.
 * The dates and the holding period are derived from the trades entered by hand.
 */
export const POSITION_COLUMNS: ColumnDefinition[] = [
  {
    key: 'symbol',
    title: 'Symbol',
    isShownByDefault: true,
    isAlwaysShown: true,
    alignment: 'left',
    sortValue: (holding) => holding.symbol,
    firstSortDirection: 'ascending',
    renderValue: (holding) => <SymbolWithStatus holding={holding} />,
  },
  {
    // The dot, the warning signs and the momentum score in one column (AI_ANALYSIS_TODO.md, decision 15).
    key: 'signal',
    title: 'Signal',
    isShownByDefault: true,
    alignment: 'left',
    sortValue: signalSortValue,
    firstSortDirection: 'descending',
    renderValue: (holding) => <SignalCell holding={holding} />,
  },
  {
    key: 'sector',
    title: 'Sector',
    isShownByDefault: true,
    alignment: 'left',
    sortValue: (holding) => holding.sector,
    firstSortDirection: 'ascending',
    renderValue: (holding, tableActions) => (
      <SectorCell holding={holding} onDataChanged={tableActions.onDataChanged} />
    ),
  },
  {
    key: 'position',
    title: 'Qty',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.position,
    firstSortDirection: 'descending',
    renderValue: (holding) => <QuantityWithSplit holding={holding} />,
  },
  {
    key: 'averageCost',
    title: 'Avg cost',
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.averageCost,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatMoney(holding.averageCost),
  },
  {
    key: 'marketPrice',
    title: 'Last',
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.marketPrice,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatMoney(holding.marketPrice),
  },
  {
    key: 'marketValue',
    title: 'Market value',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.marketValue,
    firstSortDirection: 'descending',
    renderValue: (holding) => <MarketValue holding={holding} />,
  },
  {
    key: 'unrealizedPnl',
    title: 'Unrealized P&L',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.unrealizedPnl,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatSignedMoney(holding.unrealizedPnl),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnl),
  },
  {
    key: 'unrealizedPnlPercent',
    title: '%',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.unrealizedPnlPercent,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatSignedPercent(holding.unrealizedPnlPercent),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnlPercent),
  },
  {
    key: 'holdingDays',
    title: 'Held',
    isShownByDefault: true,
    alignment: 'right',
    sortValue: (holding) => holding.holdingDays,
    firstSortDirection: 'descending',
    renderValue: (holding) => <HoldingPeriod days={holding.holdingDays} />,
  },
  {
    key: 'firstBuyDate',
    title: 'Bought',
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.firstBuyDate,
    firstSortDirection: 'ascending',
    renderValue: (holding) => formatDate(holding.firstBuyDate),
  },
  {
    key: 'lastSellDate',
    title: 'Last sold',
    isShownByDefault: false,
    alignment: 'right',
    sortValue: (holding) => holding.lastSellDate,
    firstSortDirection: 'ascending',
    renderValue: (holding) => formatDate(holding.lastSellDate),
  },
];

function columnWithKey(columnKey: string): ColumnDefinition {
  return POSITION_COLUMNS.find((column) => column.key === columnKey) ?? POSITION_COLUMNS[0];
}

function alignmentClass(column: ColumnDefinition): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

// ── table ───────────────────────────────────────────────────────────────────────────────────────

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
      className={`${TABLE_HEADER_CELL_CLASS} font-medium ${alignmentClass(column)}`}
    >
      <span className="inline-flex items-center gap-0.5">
        <button
          type="button"
          onClick={() => onSort(sortStateAfterClicking(sortState, column))}
          className={`-mx-1 inline-flex h-7 items-center gap-1 rounded-sm px-1 transition-colors hover:text-ink ${
            isSortedByThisColumn ? 'text-ink' : ''
          }`}
        >
          {column.title}
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
  tableActions,
}: {
  holding: Holding;
  columns: ColumnDefinition[];
  isSelected: boolean;
  onOpen: () => void;
  tableActions: TableActions;
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
      className={tableRowClass(isSelected)}
    >
      {columns.map((column) => {
        // A closed holding: every cell muted, not faded, so it still reads well; its figures are "—" anyway.
        const colorClass = isClosed(holding) ? 'text-muted' : (column.valueColorClass?.(holding) ?? '');
        return (
          <td key={column.key} className={`${TABLE_CELL_CLASS} ${alignmentClass(column)} ${colorClass}`}>
            {column.renderValue(holding, tableActions)}
          </td>
        );
      })}
    </tr>
  );
}

/**
 * One row per holding, in the order the page sorted them (`rows`); clicking a row, or Enter on it, opens the holding's
 * drawer (`onOpen`), and the open one is `selectedHoldingId`. Only `visibleColumnKeys` are shown, in the table's own
 * order. `onDataChanged` reloads the portfolio after a sector is saved.
 */
export function PositionsTable({
  rows,
  visibleColumnKeys,
  sortState,
  onSort,
  selectedHoldingId,
  onOpen,
  onDataChanged,
}: {
  rows: Holding[];
  visibleColumnKeys: string[];
  sortState: SortState;
  onSort: (sortState: SortState) => void;
  selectedHoldingId: number | null;
  onOpen: (holdingId: number) => void;
  onDataChanged: () => Promise<void>;
}) {
  const columns = POSITION_COLUMNS.filter((column) => visibleColumnKeys.includes(column.key));
  const tableActions: TableActions = { onDataChanged };

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
        {/* id, not symbol: two holdings can share a symbol, and React needs a unique key per row. */}
        <tbody className="[&>tr:last-child>td]:border-b-0">
          {rows.map((holding) => (
            <HoldingRow
              key={holding.id}
              holding={holding}
              columns={columns}
              isSelected={holding.id === selectedHoldingId}
              onOpen={() => onOpen(holding.id)}
              tableActions={tableActions}
            />
          ))}
        </tbody>
      </table>
    </div>
  );
}
