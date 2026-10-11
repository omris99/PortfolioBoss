import { Fragment, useState, type ReactNode } from 'react';
import { TriangleAlert } from 'lucide-react';
import { Link } from 'react-router';
import {
  EMPTY_VALUE,
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import { HINT_TEXT_CLASS, PANEL_CLASS, TABLE_CLASS, TABLE_HEAD_ROW_CLASS, TAG_CLASS } from '../lib/styles';
import type { ClosedPosition } from '../types/portfolio';
import { ExpandRowButton } from './ExpandRowButton';
import { HoldingPeriod } from './HoldingPeriod';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable } from './InvestorSplitTable';
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
  /** Left out while the account owner is the only investor: every row would say the same. */
  onlyWithSeveralInvestors?: boolean;
}

/** Whose shares were sold: each investor's trades make closed positions of their own. */
function InvestorName({ investorId }: { investorId: number }) {
  return <>{investorNameOf(useInvestors(), investorId)}</>;
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
      <span className="text-caption text-muted">{closedPosition.currency}</span>
    </>
  );
}

/** A long note is cut to the column's width; hovering shows all of it. */
function NoteCell({ note }: { note: string | null }) {
  if (note === null) return <>{EMPTY_VALUE}</>;
  return (
    <span title={note} className="block max-w-64 truncate">
      {note}
    </span>
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
      {closedPosition.source === 'MANUAL' && <span className={TAG_CLASS}>manual</span>}
      {closedPosition.remainingQuantity > 0 && (
        <span className={TAG_CLASS}>partial · still holding {formatQuantity(closedPosition.remainingQuantity)}</span>
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
    valueColorClass: () => 'font-semibold',
  },
  {
    title: 'Investor',
    alignment: 'left',
    renderValue: (closedPosition) => <InvestorName investorId={closedPosition.investorId} />,
    onlyWithSeveralInvestors: true,
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
    valueColorClass: () => 'text-muted',
  },
];

function alignmentClass(column: ColumnDefinition): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

function columnsToShow(showsInvestor: boolean): ColumnDefinition[] {
  return showsInvestor ? COLUMNS : COLUMNS.filter((column) => !column.onlyWithSeveralInvestors);
}

// ── table ───────────────────────────────────────────────────────────────────────────────────────

/** The most recent sale first, and the same day's in symbol order. 'yyyy-MM-dd' sorts correctly as text. */
function compareNewestClosedFirst(first: ClosedPosition, second: ClosedPosition): number {
  return second.closeDate.localeCompare(first.closeDate) || first.symbol.localeCompare(second.symbol);
}

/**
 * Unique: a manual position or holding, the investor, and the buy that opened the stretch — two stretches of the same
 * investor in the same holding never open on the same day, because all of a day's buys are counted before its sells.
 * Two investors' stretches can, so the investor is part of it.
 */
function rowKeyOf(closedPosition: ClosedPosition): string {
  return `${expansionKeyOf(closedPosition)}-${closedPosition.openDate}`;
}

/**
 * Which row is open, kept across reloads. A manual position's by its id and investor alone: adding a buy dated before
 * its first one moves the row's buy date, and the row should stay open while its trades are being entered.
 */
function expansionKeyOf(closedPosition: ClosedPosition): string {
  return closedPosition.source === 'MANUAL'
    ? `manual-${closedPosition.manualPositionId}-${closedPosition.investorId}`
    : `holding-${closedPosition.holdingId}-${closedPosition.investorId}-${closedPosition.openDate}`;
}

/** A row with a warning leaves the line under it to the warning, so the two read as one. */
function ClosedPositionRow({
  closedPosition,
  columns,
  isExpanded,
  onToggleExpanded,
}: {
  closedPosition: ClosedPosition;
  columns: ColumnDefinition[];
  isExpanded: boolean;
  onToggleExpanded: () => void;
}) {
  const borderClass = closedPosition.warning === null && !isExpanded ? 'border-b last:border-b-0' : '';
  return (
    <tr className={`${borderClass} transition-colors hover:bg-subtle`}>
      <td className="w-8 py-1.5 pl-2">
        <ExpandRowButton
          isExpanded={isExpanded}
          onToggle={onToggleExpanded}
          subject={`the trades of ${closedPosition.symbol}`}
          expandHint="Show its trades"
        />
      </td>
      {columns.map((column) => {
        const colorClass = column.valueColorClass?.(closedPosition) ?? '';
        return (
          <td key={column.title} className={`whitespace-nowrap px-3 py-2 ${alignmentClass(column)} ${colorClass}`}>
            {column.renderValue(closedPosition)}
          </td>
        );
      })}
    </tr>
  );
}

/**
 * Under the row it belongs to, always visible: the same orange as a holding's warnings. `columnCount` includes the
 * chevron's.
 */
function ClosedPositionWarningRow({ message, columnCount }: { message: string; columnCount: number }) {
  return (
    <tr className="border-b last:border-b-0">
      <td colSpan={columnCount} className="px-3 pb-2 text-warn-real">
        <div className="flex items-center gap-2">
          <TriangleAlert size={14} className="shrink-0" />
          {message}
        </div>
      </td>
    </tr>
  );
}

/**
 * A holding's trades of this stretch, read-only: they are corrected in the positions table, where the link opens the
 * holding's row — a closed holding's too.
 */
function HoldingTradesOfStretch({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <div className={PANEL_CLASS}>
      <TradeList trades={closedPosition.trades} />
      <p className={HINT_TEXT_CLASS}>
        From the trades of {closedPosition.symbol}: correct them in its trades panel, in{' '}
        <Link
          to={`/positions/${closedPosition.holdingId}`}
          className="font-medium text-accent underline underline-offset-2"
        >
          Positions
        </Link>
        .
      </p>
    </div>
  );
}

/** Every closed position of the same manual position, of every investor: what its "By investor" table splits. */
function closedPositionsOfManualPosition(manualPositionId: number, closedPositions: ClosedPosition[]): ClosedPosition[] {
  return closedPositions.filter((closedPosition) => closedPosition.manualPositionId === manualPositionId);
}

/**
 * What opens under a row: a manual position, to correct, with its realized P&L split by investor when it is shared; a
 * holding's trades, to read. `closedPositions` are all of the table's.
 */
function ClosedPositionDetailsRow({
  closedPosition,
  closedPositions,
  columnCount,
  onDataChanged,
}: {
  closedPosition: ClosedPosition;
  closedPositions: ClosedPosition[];
  columnCount: number;
  onDataChanged: () => Promise<void>;
}) {
  const manualPositionId = closedPosition.manualPositionId;
  return (
    <tr className="border-b last:border-b-0">
      <td colSpan={columnCount} className="px-3 pb-3">
        {manualPositionId === null ? (
          <HoldingTradesOfStretch closedPosition={closedPosition} />
        ) : (
          <div className="flex flex-col gap-3">
            <InvestorSplitTable
              position={{ kind: 'manualPosition', currency: closedPosition.currency }}
              closedPositions={closedPositionsOfManualPosition(manualPositionId, closedPositions)}
            />
            <ManualPositionPanel
              manualPositionId={manualPositionId}
              closedPosition={closedPosition}
              onDataChanged={onDataChanged}
            />
          </div>
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
  const columns = columnsToShow(hasSeveralInvestors(useInvestors()));
  const columnCountWithChevron = columns.length + 1;

  const toggleExpanded = (expansionKey: string) =>
    setExpandedKey((currentKey) => (currentKey === expansionKey ? null : expansionKey));

  return (
    <div className="overflow-x-auto">
      <table className={TABLE_CLASS}>
        <thead>
          <tr className={TABLE_HEAD_ROW_CLASS}>
            <th className="w-8">
              <span className="sr-only">Trades</span>
            </th>
            {columns.map((column) => (
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
                  columns={columns}
                  isExpanded={isExpanded}
                  onToggleExpanded={() => toggleExpanded(expansionKey)}
                />
                {closedPosition.warning !== null && (
                  <ClosedPositionWarningRow message={closedPosition.warning} columnCount={columnCountWithChevron} />
                )}
                {isExpanded && (
                  <ClosedPositionDetailsRow
                    closedPosition={closedPosition}
                    closedPositions={closedPositions}
                    columnCount={columnCountWithChevron}
                    onDataChanged={onDataChanged}
                  />
                )}
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
 * "Me +18,000.00 USD +950.00 HKD · Avi +1,180.00 USD": the same totals, investor by investor — what their cards say —
 * once there is more than one. An investor with no realized P&L yet is left out.
 */
function RealizedPnlByInvestor({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const investors = useInvestors();
  if (!hasSeveralInvestors(investors)) return null;

  const investorTotals = investors
    .map((investor) => ({
      investor,
      totalsByCurrency: [
        ...realizedPnlByCurrency(closedPositions.filter((closedPosition) => closedPosition.investorId === investor.id)),
      ],
    }))
    .filter(({ totalsByCurrency }) => totalsByCurrency.length > 0);

  return (
    <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-caption">
      {investorTotals.map(({ investor, totalsByCurrency }, index) => (
        <span key={investor.id} className="inline-flex items-center gap-1.5">
          {index > 0 && <span className="text-faint">·</span>}
          <span className="text-muted">{investor.name}</span>
          {totalsByCurrency.map(([currency, total]) => (
            <span key={currency} className={profitLossColorClass(total)}>
              {formatSignedMoney(total)} {currency}
            </span>
          ))}
        </span>
      ))}
    </div>
  );
}

/**
 * "Realized P&L +1,234.00 USD (1 without prices) (1 to check)": the total of every closed position, per currency, and
 * how many it leaves out — for a missing price, or for a warning shown under the position's row. With several
 * investors, each one's total under it.
 */
export function RealizedPnlTotals({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const totalsByCurrency = [...realizedPnlByCurrency(closedPositions)];
  const withWarningCount = closedPositions.filter((closedPosition) => closedPosition.warning !== null).length;
  const withoutPricesCount = closedPositions.filter(
    (closedPosition) => closedPosition.realizedPnl === null && closedPosition.warning === null,
  ).length;

  return (
    <div className="flex flex-col gap-1">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <span className="text-muted">Realized P&amp;L</span>
        {totalsByCurrency.map(([currency, total]) => (
          <span key={currency} className={`font-semibold ${profitLossColorClass(total)}`}>
            {formatSignedMoney(total)} {currency}
          </span>
        ))}
        {withoutPricesCount > 0 && (
          <span className="text-muted" title={MISSING_PRICE_HINT}>
            ({withoutPricesCount} without prices)
          </span>
        )}
        {withWarningCount > 0 && <span className="text-warn-real">({withWarningCount} to check)</span>}
      </div>
      <RealizedPnlByInvestor closedPositions={closedPositions} />
    </div>
  );
}
