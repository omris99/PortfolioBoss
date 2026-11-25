import type { KeyboardEvent } from 'react';
import { ArrowRight, ChevronRight, Clock } from 'lucide-react';
import { EMPTY_VALUE, formatDate, formatShekels, formatSignedShekels, profitLossColorClass } from '../../lib/format';
import { tableRowClass } from '../../lib/styles';
import type { SettlementDay } from '../../types/taxes';
import { Hint, RowReachedAsAWhole } from '../Hint';
import { DaySales } from './DaySales';
import { salesCellText } from './stockSalesOfTheDay';
import { TaxCheckCell } from './TaxCheckCell';
import {
  CALCULATED_TO_ZERO_TEXT,
  entryText,
  lineUnderTheDayText,
  waitingForARateText,
} from './taxTexts';

const DAY_CELL_CLASS = 'h-[52px] whitespace-nowrap px-3';

/** "Traded 2026-06-18" — the days of the day's sales, when there are any (a day of fees has none). */
function tradeDaysText(settlementDay: SettlementDay): string | null {
  const tradeDays = [...new Set(settlementDay.sales.map((sale) => sale.tradeDate))];
  return tradeDays.length === 0 ? null : `Traded ${tradeDays.map((tradeDay) => formatDate(tradeDay)).join(', ')}`;
}

/** Whether anything of the day is calculated: a sale, or a fee whose rate is known. */
function hasACalculatedPart(settlementDay: SettlementDay): boolean {
  const hasACalculatedSale = settlementDay.sales.some((sale) => sale.notComputedReason === null);
  const hasAFeeWithItsRate = settlementDay.expenses.some((fee) => fee.amountInShekels !== null);
  return hasACalculatedSale || hasAFeeWithItsRate;
}

/** The Taxable gain cell: the day's in its tone, "₪0.00" with why in its hint for a 0, "—" when nothing is calculated. */
function TaxableGainCell({ settlementDay }: { settlementDay: SettlementDay }) {
  if (settlementDay.status === 'CALCULATED_TO_ZERO') {
    return (
      <Hint text={CALCULATED_TO_ZERO_TEXT} align="end">
        {formatShekels(0)}
      </Hint>
    );
  }
  if (!hasACalculatedPart(settlementDay)) return <span className="text-muted">{EMPTY_VALUE}</span>;
  return (
    <span className={profitLossColorClass(settlementDay.taxableGainOfTheDay)}>
      {formatSignedShekels(settlementDay.taxableGainOfTheDay)}
    </span>
  );
}

/**
 * The Entry cell: the amount the broker recorded once confirmed, else the calculated one — "Tax ₪1,277.13", "Shield
 * ₪849.38" —, "—" while nothing can be. On an investor's tab, which has no Check column, a day waiting for a rate keeps
 * its clock here, why in its hint.
 */
function EntryCell({ settlementDay, isPortfolioTab }: { settlementDay: SettlementDay; isPortfolioTab: boolean }) {
  if (settlementDay.confirmation !== null) return <>{entryText(settlementDay.confirmation.taxAmount)}</>;
  if (settlementDay.status === 'CALCULATED' || settlementDay.status === 'CALCULATED_TO_ZERO') {
    return <>{entryText(settlementDay.computedTaxAmount)}</>;
  }
  return (
    <span className="inline-flex items-center gap-1 text-muted">
      {EMPTY_VALUE}
      {!isPortfolioTab && settlementDay.status === 'WAITING_FOR_A_RATE' && (
        <Hint text={waitingForARateText(settlementDay.settlementDay)} align="end">
          <Clock size={16} role="img" aria-label="Not calculated yet" className="text-faint" />
        </Hint>
      )}
    </span>
  );
}

/**
 * One settlement day in the year's table: its row — Settles, Sales, Taxable gain, Entry and, on Portfolio, Check —, a
 * line under it only where the user must act, and once opened (a click, Enter or Space anywhere on the row) its sales.
 */
export function SettlementDayRows({
  settlementDay,
  columnCount,
  isPortfolioTab,
  isOpen,
  openedStockSales,
  isConfirming,
  onToggle,
  onToggleStockSales,
  onConfirmAsCalculated,
  onConfirmationChanged,
}: {
  settlementDay: SettlementDay;
  columnCount: number;
  isPortfolioTab: boolean;
  isOpen: boolean;
  /** The symbols opened on this day. */
  openedStockSales: Set<string>;
  isConfirming: boolean;
  onToggle: () => void;
  onToggleStockSales: (symbol: string) => void;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
  onConfirmationChanged: () => Promise<void>;
}) {
  const lineUnderTheDay = lineUnderTheDayText(settlementDay, isPortfolioTab);
  const tradeDays = tradeDaysText(settlementDay);
  const hasSomethingUnderTheRow = lineUnderTheDay !== null || isOpen;

  const toggleOnOpeningKey = (event: KeyboardEvent<HTMLTableRowElement>) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onToggle();
    }
  };

  return (
    <>
      <RowReachedAsAWhole>
        <tr
          tabIndex={0}
          aria-expanded={isOpen}
          onClick={onToggle}
          onKeyDown={toggleOnOpeningKey}
          className={`${tableRowClass(isOpen)} ${hasSomethingUnderTheRow ? '' : 'border-b'}`}
        >
        <td className="h-[52px] pl-3">
          <ChevronRight size={16} className={`text-muted transition-transform ${isOpen ? 'rotate-90' : ''}`} />
        </td>
        <td className={`${DAY_CELL_CLASS} tabular-nums`}>
          {tradeDays === null ? (
            formatDate(settlementDay.settlementDay)
          ) : (
            <Hint text={tradeDays} align="start">
              {formatDate(settlementDay.settlementDay)}
            </Hint>
          )}
        </td>
        <td className={`${DAY_CELL_CLASS} truncate`}>{salesCellText(settlementDay)}</td>
        <td className={`${DAY_CELL_CLASS} text-right tabular-nums`}>
          <TaxableGainCell settlementDay={settlementDay} />
        </td>
        <td className={`${DAY_CELL_CLASS} text-right tabular-nums`}>
          <EntryCell settlementDay={settlementDay} isPortfolioTab={isPortfolioTab} />
        </td>
        {isPortfolioTab && (
          <td className={DAY_CELL_CLASS}>
            <TaxCheckCell
              settlementDay={settlementDay}
              isConfirming={isConfirming}
              onConfirmAsCalculated={onConfirmAsCalculated}
              onConfirmationChanged={onConfirmationChanged}
            />
          </td>
        )}
        </tr>
      </RowReachedAsAWhole>
      {lineUnderTheDay !== null && (
        <tr className={isOpen ? '' : 'border-b'}>
          <td />
          <td colSpan={columnCount - 1} className="px-3 pb-3 text-[13px] text-ink">
            <span className="flex items-center gap-1.5">
              <ArrowRight size={14} className="shrink-0 text-accent" />
              {lineUnderTheDay}
            </span>
          </td>
        </tr>
      )}
      {isOpen && (
        <tr className="border-b">
          <td colSpan={columnCount} className="bg-subtle px-5 py-3">
            <DaySales
              settlementDay={settlementDay}
              openedStockSales={openedStockSales}
              onToggleStockSales={onToggleStockSales}
            />
          </td>
        </tr>
      )}
    </>
  );
}
