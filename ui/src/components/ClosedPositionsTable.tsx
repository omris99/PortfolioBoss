import { Fragment, type KeyboardEvent, type ReactNode } from 'react';
import { ArrowDown } from 'lucide-react';
import {
  currencyTotalsInOrder,
  EMPTY_VALUE,
  formatDate,
  formatMoney,
  formatQuantity,
  formatSignedPercent,
  profitLossColorClass,
  ACCOUNT_CURRENCY,
} from '../lib/format';
import {
  CARD_CLASS,
  EXPLAINED_CLASS,
  TABLE_CLASS,
  TABLE_HEADER_CELL_CLASS,
  TAG_CLASS,
  tableRowClass,
} from '../lib/styles';
import type { ClosedPosition } from '../types/portfolio';
import { CurrencyFigure } from './CurrencyFigure';
import { HoldingPeriod } from './HoldingPeriod';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { WarnMark, WarningBand } from './WarnMark';

// ── which position is which ─────────────────────────────────────────────────────────────────────

/**
 * Which closed position a drawer shows, kept across reloads. A manual position's by its id and investor alone: adding a
 * buy dated before its first one moves the row's buy date, and the drawer should stay open while its trades are being
 * entered. A holding's by its holding, investor and the buy that opened the stretch — two stretches of the same investor
 * in the same holding never open on the same day, because all of a day's buys are counted before its sells.
 */
export function closedPositionKeyOf(closedPosition: ClosedPosition): string {
  return closedPosition.source === 'MANUAL'
    ? `manual-${closedPosition.manualPositionId}-${closedPosition.investorId}`
    : `holding-${closedPosition.holdingId}-${closedPosition.investorId}-${closedPosition.openDate}`;
}

/** The most recent sale first, and the same day's in symbol order. 'yyyy-MM-dd' sorts correctly as text. */
export function newestSaleFirst(closedPositions: ClosedPosition[]): ClosedPosition[] {
  return [...closedPositions].sort(
    (first, second) => second.closeDate.localeCompare(first.closeDate) || first.symbol.localeCompare(second.symbol),
  );
}

// ── pieces shared with the drawer ───────────────────────────────────────────────────────────────

/** Why a closed position has no realized P&L, on hover of its "—". */
export function missingRealizedPnlReason(closedPosition: ClosedPosition): string {
  return closedPosition.warning !== null
    ? 'Sold more than it bought in this period, so there is no P&L: check its trades.'
    : 'A trade in this period has no price, so there is no P&L yet.';
}

/** "manual" on a manual position; "partial · still holding 35" while part of the stretch is still held. */
export function ClosedPositionTags({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <>
      {closedPosition.source === 'MANUAL' && <span className={TAG_CLASS}>manual</span>}
      {closedPosition.remainingQuantity > 0 && (
        <span className={TAG_CLASS}>partial · still holding {formatQuantity(closedPosition.remainingQuantity)}</span>
      )}
    </>
  );
}

/** "+40.00 USD", or a dotted "—" that says why there is none. */
function RealizedPnl({ closedPosition }: { closedPosition: ClosedPosition }) {
  if (closedPosition.realizedPnl === null) {
    return (
      <span title={missingRealizedPnlReason(closedPosition)} className={EXPLAINED_CLASS}>
        {EMPTY_VALUE}
      </span>
    );
  }
  return <CurrencyFigure value={closedPosition.realizedPnl} currency={closedPosition.currency} />;
}

// ── columns ─────────────────────────────────────────────────────────────────────────────────────

interface ColumnDefinition {
  title: ReactNode;
  key: string;
  alignment: 'left' | 'right';
  renderValue: (closedPosition: ClosedPosition) => ReactNode;
  /** Text colour classes for the cell; the ordinary text colour when omitted. */
  valueColorClass?: (closedPosition: ClosedPosition) => string;
  /** Extra classes for the cell: a width limit, wrapping. */
  cellClass?: string;
  /** Left out while the account owner is the only investor: every row would say the same. */
  onlyWithSeveralInvestors?: boolean;
}

/** Whose shares were sold: each investor's trades make closed positions of their own. */
function InvestorName({ investorId }: { investorId: number }) {
  return <>{investorNameOf(useInvestors(), investorId)}</>;
}

function SymbolWithTags({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-x-1.5 gap-y-1 py-1">
      <span className="font-semibold">{closedPosition.symbol}</span>
      <ClosedPositionTags closedPosition={closedPosition} />
      {closedPosition.warning !== null && <WarnMark level="real" title={closedPosition.warning} />}
    </span>
  );
}

// The positions table's order: the figures first, the dates and the holding period at the end.
const COLUMNS: ColumnDefinition[] = [
  {
    key: 'symbol',
    title: 'Symbol',
    alignment: 'left',
    renderValue: (closedPosition) => <SymbolWithTags closedPosition={closedPosition} />,
    cellClass: 'min-w-[120px] !whitespace-normal',
  },
  {
    key: 'investor',
    title: 'Investor',
    alignment: 'left',
    renderValue: (closedPosition) => <InvestorName investorId={closedPosition.investorId} />,
    onlyWithSeveralInvestors: true,
  },
  {
    key: 'sector',
    title: 'Sector',
    alignment: 'left',
    renderValue: (closedPosition) => closedPosition.sector ?? <span className="text-faint">{EMPTY_VALUE}</span>,
    cellClass: 'max-w-[130px] truncate',
  },
  {
    key: 'quantity',
    title: 'Qty sold',
    alignment: 'right',
    renderValue: (closedPosition) => formatQuantity(closedPosition.quantity),
  },
  {
    key: 'averageBuyPrice',
    title: 'Avg buy',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.averageBuyPrice),
  },
  {
    key: 'averageSellPrice',
    title: 'Avg sell',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.averageSellPrice),
  },
  {
    // Already taken off the realized P&L next to it.
    key: 'commissions',
    title: 'Commission',
    alignment: 'right',
    renderValue: (closedPosition) => formatMoney(closedPosition.commissions),
  },
  {
    key: 'realizedPnl',
    title: 'Realized P&L',
    alignment: 'right',
    renderValue: (closedPosition) => <RealizedPnl closedPosition={closedPosition} />,
  },
  {
    key: 'realizedPnlPercent',
    title: '%',
    alignment: 'right',
    renderValue: (closedPosition) => formatSignedPercent(closedPosition.realizedPnlPercent),
    valueColorClass: (closedPosition) => profitLossColorClass(closedPosition.realizedPnlPercent),
  },
  {
    key: 'openDate',
    title: 'Bought',
    alignment: 'left',
    renderValue: (closedPosition) => formatDate(closedPosition.openDate),
  },
  {
    key: 'closeDate',
    title: (
      <span className="inline-flex items-center gap-1 text-ink">
        Sold
        <ArrowDown size={12} strokeWidth={2.5} />
      </span>
    ),
    alignment: 'left',
    renderValue: (closedPosition) => formatDate(closedPosition.closeDate),
  },
  {
    key: 'holdingDays',
    title: 'Held',
    alignment: 'right',
    renderValue: (closedPosition) => <HoldingPeriod days={closedPosition.holdingDays} />,
  },
  {
    // Only a row entered by hand has one: a derived row's notes are on its trades.
    key: 'note',
    title: 'Note',
    alignment: 'left',
    renderValue: (closedPosition) => closedPosition.note,
    valueColorClass: () => 'text-muted',
    cellClass: 'max-w-[150px] truncate',
  },
];

function alignmentClass(column: ColumnDefinition): string {
  return column.alignment === 'right' ? 'text-right' : 'text-left';
}

function columnsToShow(showsInvestor: boolean): ColumnDefinition[] {
  return showsInvestor ? COLUMNS : COLUMNS.filter((column) => !column.onlyWithSeveralInvestors);
}

// ── table ───────────────────────────────────────────────────────────────────────────────────────

/** 13px cells, 10px apart, so all thirteen columns fit at 1280px; the outer ones padded like the card. */
const CELL_CLASS = 'h-11 whitespace-nowrap border-b px-2.5 text-[13px] first:pl-5 last:pr-5';

/**
 * One closed position: clicking it, or Enter, opens its drawer. A position that sold more than it bought has its warning
 * in a band under the row, always visible, and the band opens the same drawer.
 */
function ClosedPositionRows({
  closedPosition,
  columns,
  isSelected,
  onOpen,
}: {
  closedPosition: ClosedPosition;
  columns: ColumnDefinition[];
  isSelected: boolean;
  onOpen: () => void;
}) {
  const hasWarning = closedPosition.warning !== null;
  const openOnEnter = (event: KeyboardEvent<HTMLTableRowElement>) => {
    if (event.key === 'Enter') onOpen();
  };
  return (
    <>
      <tr
        tabIndex={0}
        aria-label={`${closedPosition.symbol}, sold ${closedPosition.closeDate}, open details`}
        onClick={onOpen}
        onKeyDown={openOnEnter}
        className={tableRowClass(isSelected)}
      >
        {columns.map((column) => (
          <td
            key={column.key}
            className={`${CELL_CLASS} ${hasWarning ? '!border-b-0' : ''} ${alignmentClass(column)} ${
              column.valueColorClass?.(closedPosition) ?? ''
            } ${column.cellClass ?? ''}`}
          >
            {column.renderValue(closedPosition)}
          </td>
        ))}
      </tr>
      {closedPosition.warning !== null && (
        <tr onClick={onOpen} className={tableRowClass(isSelected)}>
          <td colSpan={columns.length} className="border-b px-5 pb-2.5 text-[13px]">
            <span className="inline-flex">
              <WarningBand level="real">{closedPosition.warning}</WarningBand>
            </span>
          </td>
        </tr>
      )}
    </>
  );
}

/**
 * Every stretch with a sale, newest sale first (`rows`, already in that order). A row opens its drawer (`onOpen`), and
 * the open one is `selectedKey`.
 */
export function ClosedPositionsTable({
  rows,
  selectedKey,
  onOpen,
}: {
  rows: ClosedPosition[];
  selectedKey: string | null;
  onOpen: (closedPositionKey: string) => void;
}) {
  const columns = columnsToShow(hasSeveralInvestors(useInvestors()));
  return (
    <div className="overflow-x-auto">
      <table className={TABLE_CLASS}>
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                className={`${TABLE_HEADER_CELL_CLASS} !px-2.5 font-medium first:!pl-5 last:!pr-5 ${alignmentClass(column)}`}
              >
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="[&>tr:last-child>td]:border-b-0">
          {rows.map((closedPosition) => {
            const closedPositionKey = closedPositionKeyOf(closedPosition);
            return (
              <ClosedPositionRows
                key={`${closedPositionKey}-${closedPosition.openDate}`}
                closedPosition={closedPosition}
                columns={columns}
                isSelected={closedPositionKey === selectedKey}
                onOpen={() => onOpen(closedPositionKey)}
              />
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

/** "+1,757.89 USD +950.00 HKD", or "+0.00 USD" with nothing realized yet. */
function CurrencyTotals({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const totals = currencyTotalsInOrder(realizedPnlByCurrency(closedPositions));
  if (totals.length === 0) return <CurrencyFigure value={0} currency={ACCOUNT_CURRENCY} />;
  return (
    <>
      {totals.map(([currency, total]) => (
        <CurrencyFigure key={currency} value={total} currency={currency} />
      ))}
    </>
  );
}

/** "Me +1,757.89 USD +950.00 HKD · Dana +0.00 USD": the same totals, investor by investor — what their cards say. */
function RealizedPnlByInvestor({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const investors = useInvestors();
  return (
    <div className="mt-3 flex flex-wrap items-center gap-3 border-t pt-3 text-[13px]">
      {investors.map((investor, index) => (
        <Fragment key={investor.id}>
          {index > 0 && <span className="text-faint">·</span>}
          <span className="inline-flex items-baseline gap-2">
            <b className="font-semibold">{investor.name}</b>
            <CurrencyTotals
              closedPositions={closedPositions.filter((closedPosition) => closedPosition.investorId === investor.id)}
            />
          </span>
        </Fragment>
      ))}
    </div>
  );
}

/**
 * The Closed page's summary: the realized P&L of every closed position, per currency — never added across currencies —,
 * how many it leaves out (a warning under the row, a missing price), and with several investors each one's total.
 */
export function ClosedSummary({ closedPositions }: { closedPositions: ClosedPosition[] }) {
  const investors = useInvestors();
  const totals = currencyTotalsInOrder(realizedPnlByCurrency(closedPositions));
  const withWarningCount = closedPositions.filter((closedPosition) => closedPosition.warning !== null).length;
  const withoutPricesCount = closedPositions.filter(
    (closedPosition) => closedPosition.realizedPnl === null && closedPosition.warning === null,
  ).length;

  return (
    <section aria-label="Realized P&L" className={`${CARD_CLASS} px-6 py-4`}>
      <span className="text-label text-muted">Realized P&amp;L</span>
      <div className="mt-1 flex flex-wrap items-baseline gap-3">
        {totals.length === 0 ? (
          <CurrencyFigure value={0} currency={ACCOUNT_CURRENCY} isLarge />
        ) : (
          totals.map(([currency, total], index) => (
            <Fragment key={currency}>
              {index > 0 && <span className="text-[20px] text-faint">·</span>}
              <CurrencyFigure value={total} currency={currency} isLarge />
            </Fragment>
          ))
        )}
        {withWarningCount > 0 && (
          <span className="inline-flex items-center gap-1.5 self-center font-medium text-warn-real">
            <WarnMark level="real" />({withWarningCount} to check)
          </span>
        )}
        {withoutPricesCount > 0 && (
          <span
            title="Some trades have no price, so their periods are left out of these totals."
            className={`self-center font-medium text-muted ${EXPLAINED_CLASS}`}
          >
            ({withoutPricesCount} without prices)
          </span>
        )}
      </div>
      {hasSeveralInvestors(investors) && <RealizedPnlByInvestor closedPositions={closedPositions} />}
    </section>
  );
}
