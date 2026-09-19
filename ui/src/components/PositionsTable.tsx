import { useMemo, useState, type ReactNode } from 'react';
import { ArrowDown, ArrowUp } from 'lucide-react';
import {
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { Holding } from '../types/portfolio';

// ── sorting ─────────────────────────────────────────────────────────────────────────────────────

type SortableField =
  | 'symbol'
  | 'position'
  | 'averageCost'
  | 'marketPrice'
  | 'marketValue'
  | 'unrealizedPnl'
  | 'unrealizedPnlPercent';

/** Uses the same words as the `aria-sort` attribute, so it can be passed straight to it. */
type SortDirection = 'ascending' | 'descending';

interface SortState {
  field: SortableField;
  direction: SortDirection;
}

function compareHoldings(first: Holding, second: Holding, field: SortableField): number {
  if (field === 'symbol') {
    return first.symbol.localeCompare(second.symbol);
  }
  return first[field] - second[field];
}

function sortHoldings(holdings: Holding[], sortState: SortState): Holding[] {
  const directionMultiplier = sortState.direction === 'ascending' ? 1 : -1;
  return [...holdings].sort(
    (first, second) => directionMultiplier * compareHoldings(first, second, sortState.field),
  );
}

function sortStateAfterClicking(currentSort: SortState, clickedField: SortableField): SortState {
  if (currentSort.field === clickedField) {
    const flippedDirection = currentSort.direction === 'ascending' ? 'descending' : 'ascending';
    return { field: clickedField, direction: flippedDirection };
  }
  // A newly chosen column starts in its natural order: A→Z for the symbol, largest first for numbers.
  return { field: clickedField, direction: clickedField === 'symbol' ? 'ascending' : 'descending' };
}

// ── columns ─────────────────────────────────────────────────────────────────────────────────────

interface ColumnDefinition {
  field: SortableField;
  title: string;
  alignment: 'left' | 'right';
  renderValue: (holding: Holding) => ReactNode;
  /** Text colour classes for the cell; a neutral grey when omitted. */
  valueColorClass?: (holding: Holding) => string;
}

// Same columns, in the same order, as the console report.
const COLUMNS: ColumnDefinition[] = [
  {
    field: 'symbol',
    title: 'Symbol',
    alignment: 'left',
    renderValue: (holding) => holding.symbol,
    valueColorClass: () => 'font-semibold text-slate-100',
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

function HoldingRow({ holding }: { holding: Holding }) {
  return (
    <tr className="border-b border-slate-800/60 transition-colors last:border-b-0 hover:bg-slate-800/30">
      {COLUMNS.map((column) => {
        const colorClass = column.valueColorClass?.(holding) ?? 'text-slate-300';
        return (
          <td
            key={column.field}
            className={`px-3 py-2 font-mono text-xs ${alignmentClass(column)} ${colorClass}`}
          >
            {column.renderValue(holding)}
          </td>
        );
      })}
    </tr>
  );
}

export function PositionsTable({ holdings }: { holdings: Holding[] }) {
  // Largest position first, like the console report.
  const [sortState, setSortState] = useState<SortState>({ field: 'marketValue', direction: 'descending' });

  const sortedHoldings = useMemo(() => sortHoldings(holdings, sortState), [holdings, sortState]);

  const handleSort = (clickedField: SortableField) =>
    setSortState((currentSort) => sortStateAfterClicking(currentSort, clickedField));

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
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
          {sortedHoldings.map((holding) => (
            <HoldingRow key={holding.symbol} holding={holding} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
