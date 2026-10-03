import type { ReactNode } from 'react';
import {
  EMPTY_VALUE,
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { ClosedPosition } from '../types/portfolio';
import { HoldingPeriod } from './HoldingPeriod';

const MISSING_PRICE_HINT = 'Enter the price of every buy and sell of this position to see its realized P&L.';

// ── columns ─────────────────────────────────────────────────────────────────────────────────────

interface ColumnDefinition {
  title: string;
  alignment: 'left' | 'right';
  renderValue: (closedPosition: ClosedPosition) => ReactNode;
  /** Text colour classes for the cell; a neutral grey when omitted. */
  valueColorClass?: (closedPosition: ClosedPosition) => string;
}

/** "—", and hovering says why: a buy or a sell of the position has no price entered. */
function UnknownWithoutPrices() {
  return <span title={MISSING_PRICE_HINT}>{EMPTY_VALUE}</span>;
}

function RealizedPnl({ closedPosition }: { closedPosition: ClosedPosition }) {
  if (closedPosition.realizedPnl === null) return <UnknownWithoutPrices />;
  return <>{formatSignedMoney(closedPosition.realizedPnl)}</>;
}

// The positions table's order: the figures first, the dates and the holding period at the end.
const COLUMNS: ColumnDefinition[] = [
  {
    title: 'Symbol',
    alignment: 'left',
    renderValue: (closedPosition) => closedPosition.symbol,
    valueColorClass: () => 'font-semibold text-slate-100',
  },
  {
    title: 'Sector',
    alignment: 'left',
    renderValue: (closedPosition) => closedPosition.sector ?? EMPTY_VALUE,
  },
  {
    title: 'Qty',
    alignment: 'right',
    renderValue: (closedPosition) => formatQuantity(closedPosition.quantity),
  },
  {
    title: 'Avg buy',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.averageBuyPrice),
  },
  {
    title: 'Avg sell',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.averageSellPrice),
  },
  {
    title: 'Realized P&L',
    alignment: 'right',
    renderValue: (closedPosition) => <RealizedPnl closedPosition={closedPosition} />,
    valueColorClass: (closedPosition) => profitLossColorClass(closedPosition.realizedPnl),
  },
  {
    title: '%',
    alignment: 'right',
    renderValue: (closedPosition) => formatSignedPercent(closedPosition.realizedPnlPercent),
    valueColorClass: (closedPosition) => profitLossColorClass(closedPosition.realizedPnlPercent),
  },
  {
    title: 'Bought',
    alignment: 'right',
    renderValue: (closedPosition) => formatDate(closedPosition.openDate),
  },
  {
    title: 'Sold',
    alignment: 'right',
    renderValue: (closedPosition) => formatDate(closedPosition.closeDate),
  },
  {
    title: 'Held',
    alignment: 'right',
    renderValue: (closedPosition) => <HoldingPeriod days={closedPosition.holdingDays} />,
  },
];

function alignmentClass(column: ColumnDefinition): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

// ── table ───────────────────────────────────────────────────────────────────────────────────────

/** The most recent sale first, and the same day's in symbol order. 'yyyy-MM-dd' sorts correctly as text. */
function compareNewestClosedFirst(first: ClosedPosition, second: ClosedPosition): number {
  return second.closeDate.localeCompare(first.closeDate) || first.symbol.localeCompare(second.symbol);
}

/**
 * Unique: two position periods of the same holding never open on the same day, because all of a day's buys are
 * counted before its sells.
 */
function rowKeyOf(closedPosition: ClosedPosition): string {
  return `${closedPosition.holdingId}-${closedPosition.openDate}`;
}

function ClosedPositionRow({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <tr className="border-b border-slate-800/60 transition-colors last:border-b-0 hover:bg-slate-800/30">
      {COLUMNS.map((column) => {
        const colorClass = column.valueColorClass?.(closedPosition) ?? 'text-slate-300';
        return (
          <td
            key={column.title}
            className={`whitespace-nowrap px-3 py-2 font-mono text-xs ${alignmentClass(column)} ${colorClass}`}
          >
            {column.renderValue(closedPosition)}
          </td>
        );
      })}
    </tr>
  );
}

/** Read-only: a closed position is corrected through the trades of its holding, in the positions table. */
export function ClosedPositionsTable({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const newestFirst = [...closedPositions].sort(compareNewestClosedFirst);

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
            {COLUMNS.map((column) => (
              <th key={column.title} className={`px-3 py-2 font-medium ${alignmentClass(column)}`}>
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {newestFirst.map((closedPosition) => (
            <ClosedPositionRow key={rowKeyOf(closedPosition)} closedPosition={closedPosition} />
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ── totals ──────────────────────────────────────────────────────────────────────────────────────

/** Per currency, since dollars and euros don't add up; a position with no realized P&L is left out. */
function realizedPnlByCurrency(closedPositions: ClosedPosition[]): Map<string, number> {
  const totalsByCurrency = new Map<string, number>();
  for (const closedPosition of closedPositions) {
    if (closedPosition.realizedPnl === null) continue;
    const runningTotal = totalsByCurrency.get(closedPosition.currency) ?? 0;
    totalsByCurrency.set(closedPosition.currency, runningTotal + closedPosition.realizedPnl);
  }
  return totalsByCurrency;
}

/** "Realized P&L +1,234.00 USD (1 without prices)": the total of every closed position, per currency. */
export function RealizedPnlTotals({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const totalsByCurrency = [...realizedPnlByCurrency(closedPositions)];
  const withoutPnlCount = closedPositions.filter((closedPosition) => closedPosition.realizedPnl === null).length;

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
      <span className="text-slate-500">Realized P&amp;L</span>
      {totalsByCurrency.map(([currency, total]) => (
        <span key={currency} className={`font-mono font-semibold ${profitLossColorClass(total)}`}>
          {formatSignedMoney(total)} {currency}
        </span>
      ))}
      {withoutPnlCount > 0 && (
        <span className="text-slate-500" title={MISSING_PRICE_HINT}>
          ({withoutPnlCount} without prices)
        </span>
      )}
    </div>
  );
}
