import { Fragment } from 'react';
import { CARD_CLASS, TABLE_CLASS } from '../../lib/styles';
import type { SettlementDay, TaxYear } from '../../types/taxes';
import { MonthHeadingRow } from './MonthHeadingRow';
import { SettlementDayRows } from './SettlementDayRows';
import { monthsNewestFirst } from './taxYears';

const HEADER_CELL_CLASS = 'h-9 whitespace-nowrap border-b bg-subtle px-3 text-label text-muted';

/** No symbol opened on a day. */
const NO_SYMBOL_OPENED: Set<string> = new Set();

/**
 * The year's settlement days in one table, grouped by month, the newest first (the design's arrangement B): one header
 * row — Settles · Sales · Taxable gain · Entry · and Check on Portfolio —, each month a heading row on the page's grey,
 * then its days. The columns line up down the whole year. On an investor's tab there is no Check column: their tax is
 * shown, not confirmed (decision 7).
 */
export function SettlementDaysTable({
  taxYear,
  isPortfolioTab,
  openedSettlementDays,
  openedStockSalesByDay,
  isConfirming,
  onToggleSettlementDay,
  onToggleStockSales,
  onConfirmAsCalculated,
  onConfirmationChanged,
}: {
  taxYear: TaxYear;
  isPortfolioTab: boolean;
  openedSettlementDays: Set<string>;
  /** The symbols opened on each day. */
  openedStockSalesByDay: Map<string, Set<string>>;
  isConfirming: boolean;
  onToggleSettlementDay: (settlementDay: string) => void;
  onToggleStockSales: (settlementDay: string, symbol: string) => void;
  onConfirmAsCalculated: (settlementDays: SettlementDay[]) => void;
  onConfirmationChanged: () => Promise<void>;
}) {
  const columnCount = isPortfolioTab ? 6 : 5;
  return (
    <section aria-label={`The settlement days of ${taxYear.year}`} className={`${CARD_CLASS} overflow-hidden`}>
      <table className={`${TABLE_CLASS} table-fixed`}>
        <colgroup>
          <col className="w-9" />
          <col className="w-[136px]" />
          <col />
          <col className="w-[132px]" />
          <col className="w-[144px]" />
          {isPortfolioTab && <col className="w-[208px]" />}
        </colgroup>
        <thead>
          <tr>
            <th className={HEADER_CELL_CLASS}>
              <span className="sr-only">Open</span>
            </th>
            <th className={`${HEADER_CELL_CLASS} text-left`}>Settles</th>
            <th className={`${HEADER_CELL_CLASS} text-left`}>Sales</th>
            <th className={`${HEADER_CELL_CLASS} text-right`}>Taxable gain</th>
            <th className={`${HEADER_CELL_CLASS} text-right`}>Entry</th>
            {isPortfolioTab && <th className={`${HEADER_CELL_CLASS} text-left`}>Check</th>}
          </tr>
        </thead>
        <tbody>
          {monthsNewestFirst(taxYear).map((month) => (
            <Fragment key={month.monthlySettlement.month}>
              <MonthHeadingRow
                month={month}
                columnCount={columnCount}
                isPortfolioTab={isPortfolioTab}
                isConfirming={isConfirming}
                onConfirmAsCalculated={onConfirmAsCalculated}
              />
              {month.settlementDays.map((settlementDay) => (
                <SettlementDayRows
                  key={settlementDay.settlementDay}
                  settlementDay={settlementDay}
                  columnCount={columnCount}
                  isPortfolioTab={isPortfolioTab}
                  isOpen={openedSettlementDays.has(settlementDay.settlementDay)}
                  openedStockSales={openedStockSalesByDay.get(settlementDay.settlementDay) ?? NO_SYMBOL_OPENED}
                  isConfirming={isConfirming}
                  onToggle={() => onToggleSettlementDay(settlementDay.settlementDay)}
                  onToggleStockSales={(symbol) => onToggleStockSales(settlementDay.settlementDay, symbol)}
                  onConfirmAsCalculated={onConfirmAsCalculated}
                  onConfirmationChanged={onConfirmationChanged}
                />
              ))}
            </Fragment>
          ))}
        </tbody>
      </table>
    </section>
  );
}
