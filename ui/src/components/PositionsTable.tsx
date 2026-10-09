import { Fragment, useMemo, useState, type ReactNode } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import {
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { ClosedPosition, Holding } from '../types/portfolio';
import { ExpandRowButton } from './ExpandRowButton';
import { HoldingPeriod } from './HoldingPeriod';
import { HoldingWarningIcon } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable } from './InvestorSplitTable';
import { MomentumDetails, MomentumScore } from './Momentum';
import { SECTOR_OPTIONS_LIST_ID, SectorCell } from './SectorCell';
import { TradesPanel } from './TradesPanel';

// ── sorting ─────────────────────────────────────────────────────────────────────────────────────

/** Uses the same words as the `aria-sort` attribute, so it can be passed straight to it. */
type SortDirection = 'ascending' | 'descending';

/** What a column sorts by: text (dates are 'yyyy-MM-dd', which sorts correctly as text) or a number. */
type SortValue = string | number;

interface SortState {
  columnKey: string;
  direction: SortDirection;
}

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

function sortHoldings(holdings: Holding[], sortState: SortState): Holding[] {
  const sortColumn = columnWithKey(sortState.columnKey);
  return [...holdings].sort((first, second) =>
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

/** Everything about one column lives here: adding a column means adding one entry to {@link COLUMNS}. */
interface ColumnDefinition {
  key: string;
  title: string;
  alignment: 'left' | 'right';
  /** What the column sorts by; an empty value (`null`) always sorts last. */
  sortValue: (holding: Holding) => SortValue | null;
  /** The order on the first click: A→Z for text, oldest first for dates, largest first for numbers. */
  firstSortDirection: SortDirection;
  renderValue: (holding: Holding, tableActions: TableActions) => ReactNode;
  /** Text colour classes for the cell; a neutral grey when omitted. */
  valueColorClass?: (holding: Holding) => string;
}

function SymbolWithStatus({ holding }: { holding: Holding }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {holding.symbol}
      {holding.status === 'CLOSED' && (
        <span className="rounded bg-slate-800 px-1 py-0.5 font-sans text-[9px] font-medium uppercase text-slate-400">
          closed
        </span>
      )}
      <HoldingWarningIcon warnings={holding.warnings} />
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
      {formatQuantity(holding.position)} <span className="text-slate-500">({split})</span>
    </span>
  );
}

// The console report's columns in its order, with the signal and the sector after the symbol and the dates and
// holding period, which are derived from the trades entered by hand, at the end.
const COLUMNS: ColumnDefinition[] = [
  {
    key: 'symbol',
    title: 'Symbol',
    alignment: 'left',
    sortValue: (holding) => holding.symbol,
    firstSortDirection: 'ascending',
    renderValue: (holding) => <SymbolWithStatus holding={holding} />,
    valueColorClass: () => 'font-semibold text-slate-100',
  },
  {
    // The momentum score for now; the analysts and the news join it in AI_ANALYSIS_TODO.md's session 3.
    key: 'signal',
    title: 'Signal',
    alignment: 'left',
    sortValue: (holding) => holding.momentum?.score ?? null,
    firstSortDirection: 'descending',
    renderValue: (holding) => <MomentumScore momentum={holding.momentum} />,
  },
  {
    key: 'sector',
    title: 'Sector',
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
    alignment: 'right',
    sortValue: (holding) => holding.position,
    firstSortDirection: 'descending',
    renderValue: (holding) => <QuantityWithSplit holding={holding} />,
  },
  {
    key: 'averageCost',
    title: 'Avg cost',
    alignment: 'right',
    sortValue: (holding) => holding.averageCost,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatMoney(holding.averageCost),
  },
  {
    key: 'marketPrice',
    title: 'Last',
    alignment: 'right',
    sortValue: (holding) => holding.marketPrice,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatMoney(holding.marketPrice),
  },
  {
    key: 'marketValue',
    title: 'Market value',
    alignment: 'right',
    sortValue: (holding) => holding.marketValue,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatMoney(holding.marketValue),
  },
  {
    key: 'unrealizedPnl',
    title: 'Unrealized P&L',
    alignment: 'right',
    sortValue: (holding) => holding.unrealizedPnl,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatSignedMoney(holding.unrealizedPnl),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnl),
  },
  {
    key: 'unrealizedPnlPercent',
    title: '%',
    alignment: 'right',
    sortValue: (holding) => holding.unrealizedPnlPercent,
    firstSortDirection: 'descending',
    renderValue: (holding) => formatSignedPercent(holding.unrealizedPnlPercent),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnlPercent),
  },
  {
    key: 'firstBuyDate',
    title: 'Bought',
    alignment: 'right',
    sortValue: (holding) => holding.firstBuyDate,
    firstSortDirection: 'ascending',
    renderValue: (holding) => formatDate(holding.firstBuyDate),
  },
  {
    key: 'lastSellDate',
    title: 'Last sold',
    alignment: 'right',
    sortValue: (holding) => holding.lastSellDate,
    firstSortDirection: 'ascending',
    renderValue: (holding) => formatDate(holding.lastSellDate),
  },
  {
    key: 'holdingDays',
    title: 'Held',
    alignment: 'right',
    sortValue: (holding) => holding.holdingDays,
    firstSortDirection: 'descending',
    renderValue: (holding) => <HoldingPeriod days={holding.holdingDays} />,
  },
];

function columnWithKey(columnKey: string): ColumnDefinition {
  return COLUMNS.find((column) => column.key === columnKey) ?? COLUMNS[0];
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
  onSort: (column: ColumnDefinition) => void;
}) {
  const isSortedByThisColumn = sortState.columnKey === column.key;

  return (
    <th
      aria-sort={isSortedByThisColumn ? sortState.direction : 'none'}
      className={`px-3 py-2 font-medium ${alignmentClass(column)}`}
    >
      <button
        type="button"
        onClick={() => onSort(column)}
        className={`inline-flex items-center gap-1 uppercase tracking-wide transition-colors hover:text-slate-300 ${
          isSortedByThisColumn ? 'text-slate-200' : ''
        }`}
      >
        {column.title}
        {isSortedByThisColumn &&
          (sortState.direction === 'ascending' ? <ArrowUp size={10} /> : <ArrowDown size={10} />)}
      </button>
    </th>
  );
}

function HoldingRow({
  holding,
  isExpanded,
  onToggleExpanded,
  tableActions,
}: {
  holding: Holding;
  isExpanded: boolean;
  onToggleExpanded: () => void;
  tableActions: TableActions;
}) {
  // A closed holding is shown dimmed; its trades stay editable, which is where its history gets completed.
  const closedClass = holding.status === 'CLOSED' ? 'opacity-60' : '';
  return (
    <tr
      className={`border-b border-slate-800/60 transition-colors last:border-b-0 hover:bg-slate-800/30 ${closedClass}`}
    >
      <td className="w-6 py-2 pl-2">
        <ExpandRowButton
          isExpanded={isExpanded}
          onToggle={onToggleExpanded}
          subject={`the momentum and trades of ${holding.symbol}`}
          expandHint="Show the momentum, and show and enter trades"
        />
      </td>
      {COLUMNS.map((column) => {
        const colorClass = column.valueColorClass?.(holding) ?? 'text-slate-300';
        return (
          <td
            key={column.key}
            className={`whitespace-nowrap px-3 py-2 font-mono text-xs ${alignmentClass(column)} ${colorClass}`}
          >
            {column.renderValue(holding, tableActions)}
          </td>
        );
      })}
    </tr>
  );
}

/** Each sector once, A→Z: offered while typing a sector, so the same one isn't spelled two ways. */
function distinctSectorsOf(holdings: Holding[]): string[] {
  const sectors = holdings.map((holding) => holding.sector).filter((sector) => sector !== null);
  return [...new Set(sectors)].sort((firstSector, secondSector) => firstSector.localeCompare(secondSector));
}

/** A holding's own closed positions, of every investor — what its "By investor" table splits. */
function closedPositionsOf(holding: Holding, closedPositions: ClosedPosition[]): ClosedPosition[] {
  return closedPositions.filter((closedPosition) => closedPosition.holdingId === holding.id);
}

/**
 * `onDataChanged` reloads the portfolio; the sector cells and the trades panel call it after every save.
 * `closedPositions` are every closed position of the portfolio: each holding's open row shows its own, split by investor.
 */
export function PositionsTable({
  holdings,
  closedPositions,
  onDataChanged,
}: {
  holdings: Holding[];
  closedPositions: ClosedPosition[];
  onDataChanged: () => Promise<void>;
}) {
  // Largest position first, like the console report.
  const [sortState, setSortState] = useState<SortState>({ columnKey: 'marketValue', direction: 'descending' });
  // One trades panel open at a time.
  const [expandedHoldingId, setExpandedHoldingId] = useState<number | null>(null);

  const sortedHoldings = useMemo(() => sortHoldings(holdings, sortState), [holdings, sortState]);
  const sectorOptions = useMemo(() => distinctSectorsOf(holdings), [holdings]);
  const tableActions: TableActions = { onDataChanged };

  const handleSort = (clickedColumn: ColumnDefinition) =>
    setSortState((currentSort) => sortStateAfterClicking(currentSort, clickedColumn));

  const toggleExpanded = (holdingId: number) =>
    setExpandedHoldingId((currentlyExpandedId) => (currentlyExpandedId === holdingId ? null : holdingId));

  return (
    <div className="overflow-x-auto">
      <datalist id={SECTOR_OPTIONS_LIST_ID}>
        {sectorOptions.map((sector) => (
          <option key={sector} value={sector} />
        ))}
      </datalist>
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
            <th className="w-6">
              <span className="sr-only">Trades</span>
            </th>
            {COLUMNS.map((column) => (
              <SortableHeaderCell
                key={column.key}
                column={column}
                sortState={sortState}
                onSort={handleSort}
              />
            ))}
          </tr>
        </thead>
        <tbody>
          {/* id, not symbol: two holdings can share a symbol, and React needs a unique key per row. */}
          {sortedHoldings.map((holding) => {
            const isExpanded = expandedHoldingId === holding.id;
            return (
              <Fragment key={holding.id}>
                <HoldingRow
                  holding={holding}
                  isExpanded={isExpanded}
                  onToggleExpanded={() => toggleExpanded(holding.id)}
                  tableActions={tableActions}
                />
                {isExpanded && (
                  <tr className="border-b border-slate-800/60">
                    <td colSpan={COLUMNS.length + 1} className="px-3 pb-3">
                      <div className="flex flex-col gap-3">
                        <MomentumDetails momentum={holding.momentum} />
                        <InvestorSplitTable
                          position={{ kind: 'holding', holding }}
                          closedPositions={closedPositionsOf(holding, closedPositions)}
                        />
                        <TradesPanel owner={{ kind: 'holding', holding }} onDataChanged={onDataChanged} />
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
