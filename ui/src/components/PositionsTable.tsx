import { Fragment, useMemo, useState, type ReactNode } from 'react';
import { ArrowDown, ArrowUp, ChevronDown, ChevronRight } from 'lucide-react';
import {
  formatDate,
  formatDayCount,
  formatHoldingPeriod,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { Holding } from '../types/portfolio';
import { SECTOR_OPTIONS_LIST_ID, SectorCell } from './SectorCell';
import { TradesPanel } from './TradesPanel';

// ── sorting ─────────────────────────────────────────────────────────────────────────────────────

/** Columns compared as text. The dates are 'yyyy-MM-dd', which sorts correctly as text. */
type TextField = 'symbol' | 'sector' | 'firstBuyDate' | 'lastSellDate';

type NumberField =
  | 'position'
  | 'averageCost'
  | 'marketPrice'
  | 'marketValue'
  | 'unrealizedPnl'
  | 'unrealizedPnlPercent'
  | 'holdingDays';

type SortableField = TextField | NumberField;

const TEXT_FIELDS: ReadonlySet<SortableField> = new Set<TextField>(['symbol', 'sector', 'firstBuyDate', 'lastSellDate']);

function isTextField(field: SortableField): field is TextField {
  return TEXT_FIELDS.has(field);
}

/** Uses the same words as the `aria-sort` attribute, so it can be passed straight to it. */
type SortDirection = 'ascending' | 'descending';

interface SortState {
  field: SortableField;
  direction: SortDirection;
}

function compareText(firstText: string, secondText: string): number {
  return firstText.localeCompare(secondText);
}

function compareNumbers(firstNumber: number, secondNumber: number): number {
  return firstNumber - secondNumber;
}

/**
 * Empty values always sink to the bottom, whichever way the column is sorted: the direction is applied
 * only to the values that exist. Flipping it afterwards would put every "—" at the top.
 */
function compareWithEmptyLast<Value>(
  firstValue: Value | null,
  secondValue: Value | null,
  compareValues: (first: Value, second: Value) => number,
  direction: SortDirection,
): number {
  if (firstValue === null && secondValue === null) return 0;
  if (firstValue === null) return 1;
  if (secondValue === null) return -1;
  const directionMultiplier = direction === 'ascending' ? 1 : -1;
  return directionMultiplier * compareValues(firstValue, secondValue);
}

function compareHoldings(first: Holding, second: Holding, sortState: SortState): number {
  const { field, direction } = sortState;
  if (isTextField(field)) {
    return compareWithEmptyLast(first[field], second[field], compareText, direction);
  }
  return compareWithEmptyLast(first[field], second[field], compareNumbers, direction);
}

function sortHoldings(holdings: Holding[], sortState: SortState): Holding[] {
  return [...holdings].sort((first, second) => compareHoldings(first, second, sortState));
}

function sortStateAfterClicking(currentSort: SortState, clickedField: SortableField): SortState {
  if (currentSort.field === clickedField) {
    const flippedDirection = currentSort.direction === 'ascending' ? 'descending' : 'ascending';
    return { field: clickedField, direction: flippedDirection };
  }
  // A newly chosen column starts in its natural order: A→Z for text, oldest first for dates,
  // largest first for numbers.
  return { field: clickedField, direction: isTextField(clickedField) ? 'ascending' : 'descending' };
}

// ── columns ─────────────────────────────────────────────────────────────────────────────────────

/** What a cell can do beyond showing a value: an editable cell reloads the portfolio after saving. */
interface TableActions {
  onDataChanged: () => Promise<void>;
}

interface ColumnDefinition {
  field: SortableField;
  title: string;
  alignment: 'left' | 'right';
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
    </span>
  );
}

/** Months are approximate in the short form, so hovering shows the exact number of days. */
function HoldingPeriod({ days }: { days: number | null }) {
  const exactDayCount = days === null ? undefined : formatDayCount(days);
  return <span title={exactDayCount}>{formatHoldingPeriod(days)}</span>;
}

// The console report's columns in its order, with the sector after the symbol and the dates and
// holding period, which are derived from the trades entered by hand, at the end.
const COLUMNS: ColumnDefinition[] = [
  {
    field: 'symbol',
    title: 'Symbol',
    alignment: 'left',
    renderValue: (holding) => <SymbolWithStatus holding={holding} />,
    valueColorClass: () => 'font-semibold text-slate-100',
  },
  {
    field: 'sector',
    title: 'Sector',
    alignment: 'left',
    renderValue: (holding, tableActions) => (
      <SectorCell holding={holding} onDataChanged={tableActions.onDataChanged} />
    ),
  },
  {
    field: 'position',
    title: 'Qty',
    alignment: 'right',
    renderValue: (holding) => formatQuantity(holding.position),
  },
  {
    field: 'averageCost',
    title: 'Avg cost',
    alignment: 'right',
    renderValue: (holding) => formatMoney(holding.averageCost),
  },
  {
    field: 'marketPrice',
    title: 'Last',
    alignment: 'right',
    renderValue: (holding) => formatMoney(holding.marketPrice),
  },
  {
    field: 'marketValue',
    title: 'Market value',
    alignment: 'right',
    renderValue: (holding) => formatMoney(holding.marketValue),
  },
  {
    field: 'unrealizedPnl',
    title: 'Unrealized P&L',
    alignment: 'right',
    renderValue: (holding) => formatSignedMoney(holding.unrealizedPnl),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnl),
  },
  {
    field: 'unrealizedPnlPercent',
    title: '%',
    alignment: 'right',
    renderValue: (holding) => formatSignedPercent(holding.unrealizedPnlPercent),
    valueColorClass: (holding) => profitLossColorClass(holding.unrealizedPnlPercent),
  },
  {
    field: 'firstBuyDate',
    title: 'Bought',
    alignment: 'right',
    renderValue: (holding) => formatDate(holding.firstBuyDate),
  },
  {
    field: 'lastSellDate',
    title: 'Last sold',
    alignment: 'right',
    renderValue: (holding) => formatDate(holding.lastSellDate),
  },
  {
    field: 'holdingDays',
    title: 'Held',
    alignment: 'right',
    renderValue: (holding) => <HoldingPeriod days={holding.holdingDays} />,
  },
];

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
  onSort: (field: SortableField) => void;
}) {
  const isSortedByThisColumn = sortState.field === column.field;

  return (
    <th
      aria-sort={isSortedByThisColumn ? sortState.direction : 'none'}
      className={`px-3 py-2 font-medium ${alignmentClass(column)}`}
    >
      <button
        type="button"
        onClick={() => onSort(column.field)}
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

function ExpandTradesButton({
  holding,
  isExpanded,
  onToggle,
}: {
  holding: Holding;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      aria-expanded={isExpanded}
      title={isExpanded ? 'Hide trades' : 'Show and enter trades'}
      aria-label={`${isExpanded ? 'Hide' : 'Show'} the trades of ${holding.symbol}`}
      onClick={onToggle}
      className="rounded p-0.5 text-slate-500 transition-colors hover:bg-slate-800 hover:text-slate-200"
    >
      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
    </button>
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
        <ExpandTradesButton holding={holding} isExpanded={isExpanded} onToggle={onToggleExpanded} />
      </td>
      {COLUMNS.map((column) => {
        const colorClass = column.valueColorClass?.(holding) ?? 'text-slate-300';
        return (
          <td
            key={column.field}
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
  return [...new Set(sectors)].sort(compareText);
}

/** `onDataChanged` reloads the portfolio; the sector cells and the trades panel call it after every save. */
export function PositionsTable({
  holdings,
  onDataChanged,
}: {
  holdings: Holding[];
  onDataChanged: () => Promise<void>;
}) {
  // Largest position first, like the console report.
  const [sortState, setSortState] = useState<SortState>({ field: 'marketValue', direction: 'descending' });
  // One trades panel open at a time.
  const [expandedHoldingId, setExpandedHoldingId] = useState<number | null>(null);

  const sortedHoldings = useMemo(() => sortHoldings(holdings, sortState), [holdings, sortState]);
  const sectorOptions = useMemo(() => distinctSectorsOf(holdings), [holdings]);
  const tableActions: TableActions = { onDataChanged };

  const handleSort = (clickedField: SortableField) =>
    setSortState((currentSort) => sortStateAfterClicking(currentSort, clickedField));

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
                key={column.field}
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
                      <TradesPanel holding={holding} onDataChanged={onDataChanged} />
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
