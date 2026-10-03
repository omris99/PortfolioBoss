import { Fragment, useState, type ReactNode } from 'react';
import { TriangleAlert } from 'lucide-react';
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
import { ExpandRowButton } from './ExpandRowButton';
import { HoldingPeriod } from './HoldingPeriod';
import { ManualPositionPanel } from './ManualPositionPanel';
import { TradeList } from './TradesPanel';

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

/** "+40.00 USD", as in the totals above the table: positions in different currencies don't add up. */
function RealizedPnl({ closedPosition }: { closedPosition: ClosedPosition }) {
  if (closedPosition.realizedPnl === null) return <UnknownWithoutPrices />;
  return (
    <>
      {formatSignedMoney(closedPosition.realizedPnl)}{' '}
      <span className="text-[10px] text-slate-500">{closedPosition.currency}</span>
    </>
  );
}

/** A long note is cut to the column's width; hovering shows all of it. */
function NoteCell({ note }: { note: string | null }) {
  if (note === null) return <>{EMPTY_VALUE}</>;
  return (
    <span title={note} className="block max-w-64 truncate font-sans">
      {note}
    </span>
  );
}

function Tag({ colorClass, children }: { colorClass: string; children: ReactNode }) {
  return (
    <span className={`rounded px-1 py-0.5 font-sans text-[9px] font-medium uppercase ${colorClass}`}>{children}</span>
  );
}

/**
 * "manual" on a manual position, like "closed" in the positions table; "partial · still holding 10" while part of the
 * stretch is still held — the row grows with every sell until it is closed.
 */
function SymbolWithTags({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {closedPosition.symbol}
      {closedPosition.source === 'MANUAL' && <Tag colorClass="bg-slate-800 text-slate-400">manual</Tag>}
      {closedPosition.remainingQuantity > 0 && (
        <Tag colorClass="bg-sky-500/10 text-sky-300">
          partial · still holding {formatQuantity(closedPosition.remainingQuantity)}
        </Tag>
      )}
    </span>
  );
}

// The positions table's order: the figures first, the dates and the holding period at the end.
const COLUMNS: ColumnDefinition[] = [
  {
    title: 'Symbol',
    alignment: 'left',
    renderValue: (closedPosition) => <SymbolWithTags closedPosition={closedPosition} />,
    valueColorClass: () => 'font-semibold text-slate-100',
  },
  {
    title: 'Sector',
    alignment: 'left',
    renderValue: (closedPosition) => closedPosition.sector ?? EMPTY_VALUE,
  },
  {
    title: 'Qty sold',
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
    // Already taken off the realized P&L next to it.
    title: 'Commission',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.commissions),
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
  {
    // Only a row entered by hand has one: a derived row's notes are on its trades.
    title: 'Note',
    alignment: 'left',
    renderValue: (closedPosition) => <NoteCell note={closedPosition.note} />,
    valueColorClass: () => 'text-slate-400',
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
 * Unique: a manual position or holding, and the buy that opened the stretch — two stretches of the same owner never
 * open on the same day, because all of a day's buys are counted before its sells.
 */
function rowKeyOf(closedPosition: ClosedPosition): string {
  return `${expansionKeyOf(closedPosition)}-${closedPosition.openDate}`;
}

/**
 * Which row is open, kept across reloads. A manual position's by its id alone: adding a buy dated before its first one
 * moves the row's buy date, and the row should stay open while its trades are being entered.
 */
function expansionKeyOf(closedPosition: ClosedPosition): string {
  return closedPosition.source === 'MANUAL'
    ? `manual-${closedPosition.manualPositionId}`
    : `holding-${closedPosition.holdingId}-${closedPosition.openDate}`;
}

/** A row with a warning leaves the line under it to the warning, so the two read as one. */
function ClosedPositionRow({
  closedPosition,
  isExpanded,
  onToggleExpanded,
}: {
  closedPosition: ClosedPosition;
  isExpanded: boolean;
  onToggleExpanded: () => void;
}) {
  const borderClass =
    closedPosition.warning === null && !isExpanded ? 'border-b border-slate-800/60 last:border-b-0' : '';
  return (
    <tr className={`${borderClass} transition-colors hover:bg-slate-800/30`}>
      <td className="w-6 py-2 pl-2">
        <ExpandRowButton
          isExpanded={isExpanded}
          onToggle={onToggleExpanded}
          subject={`the trades of ${closedPosition.symbol}`}
          expandHint="Show its trades"
        />
      </td>
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

/** Every column, plus the chevron's. */
const COLUMN_COUNT_WITH_CHEVRON = COLUMNS.length + 1;

/** Under the row it belongs to, always visible: the same orange as a holding's warnings. */
function ClosedPositionWarningRow({ message }: { message: string }) {
  return (
    <tr className="border-b border-slate-800/60 last:border-b-0">
      <td colSpan={COLUMN_COUNT_WITH_CHEVRON} className="px-3 pb-2 text-xs text-orange-500">
        <div className="flex items-center gap-1">
          <TriangleAlert size={12} className="shrink-0" />
          {message}
        </div>
      </td>
    </tr>
  );
}

function holdingRowHint(closedPosition: ClosedPosition): string {
  return (
    `From the trades of ${closedPosition.symbol}: correct them in its trades panel, in Positions ` +
    '(with "Show closed" on, if the holding is closed).'
  );
}

/** A holding's trades of this stretch, read-only: they are corrected in the positions table. */
function HoldingTradesOfStretch({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border border-slate-800 bg-slate-950/40 p-3 text-xs text-slate-300">
      <TradeList trades={closedPosition.trades} />
      <p className="text-[11px] text-slate-500">{holdingRowHint(closedPosition)}</p>
    </div>
  );
}

/** What opens under a row: a manual position, to correct; a holding's trades, to read. */
function ClosedPositionDetailsRow({
  closedPosition,
  onDataChanged,
}: {
  closedPosition: ClosedPosition;
  onDataChanged: () => Promise<void>;
}) {
  const manualPositionId = closedPosition.manualPositionId;
  return (
    <tr className="border-b border-slate-800/60 last:border-b-0">
      <td colSpan={COLUMN_COUNT_WITH_CHEVRON} className="px-3 pb-3">
        {manualPositionId === null ? (
          <HoldingTradesOfStretch closedPosition={closedPosition} />
        ) : (
          <ManualPositionPanel
            manualPositionId={manualPositionId}
            closedPosition={closedPosition}
            onDataChanged={onDataChanged}
          />
        )}
      </td>
    </tr>
  );
}

/**
 * Every stretch with a sale, newest sale first. The chevron opens its trades: a manual position's are corrected
 * there, a holding's in the positions table. `onDataChanged` reloads the portfolio after a correction.
 */
export function ClosedPositionsTable({
  closedPositions,
  onDataChanged,
}: {
  closedPositions: ClosedPosition[];
  onDataChanged: () => Promise<void>;
}) {
  // One row open at a time, as in the positions table.
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const newestFirst = [...closedPositions].sort(compareNewestClosedFirst);

  const toggleExpanded = (expansionKey: string) =>
    setExpandedKey((currentKey) => (currentKey === expansionKey ? null : expansionKey));

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-slate-800 text-[10px] uppercase tracking-wide text-slate-500">
            <th className="w-6">
              <span className="sr-only">Trades</span>
            </th>
            {COLUMNS.map((column) => (
              <th key={column.title} className={`px-3 py-2 font-medium ${alignmentClass(column)}`}>
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {newestFirst.map((closedPosition) => {
            const expansionKey = expansionKeyOf(closedPosition);
            const isExpanded = expandedKey === expansionKey;
            return (
              <Fragment key={rowKeyOf(closedPosition)}>
                <ClosedPositionRow
                  closedPosition={closedPosition}
                  isExpanded={isExpanded}
                  onToggleExpanded={() => toggleExpanded(expansionKey)}
                />
                {closedPosition.warning !== null && <ClosedPositionWarningRow message={closedPosition.warning} />}
                {isExpanded && <ClosedPositionDetailsRow closedPosition={closedPosition} onDataChanged={onDataChanged} />}
              </Fragment>
            );
          })}
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

/**
 * "Realized P&L +1,234.00 USD (1 without prices) (1 to check)": the total of every closed position, per currency, and
 * how many it leaves out — for a missing price, or for a warning shown under the position's row.
 */
export function RealizedPnlTotals({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const totalsByCurrency = [...realizedPnlByCurrency(closedPositions)];
  const withWarningCount = closedPositions.filter((closedPosition) => closedPosition.warning !== null).length;
  const withoutPricesCount = closedPositions.filter(
    (closedPosition) => closedPosition.realizedPnl === null && closedPosition.warning === null,
  ).length;

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
      <span className="text-slate-500">Realized P&amp;L</span>
      {totalsByCurrency.map(([currency, total]) => (
        <span key={currency} className={`font-mono font-semibold ${profitLossColorClass(total)}`}>
          {formatSignedMoney(total)} {currency}
        </span>
      ))}
      {withoutPricesCount > 0 && (
        <span className="text-slate-500" title={MISSING_PRICE_HINT}>
          ({withoutPricesCount} without prices)
        </span>
      )}
      {withWarningCount > 0 && <span className="text-orange-500">({withWarningCount} to check)</span>}
    </div>
  );
}
