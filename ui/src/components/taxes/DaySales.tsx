import type { KeyboardEvent } from 'react';
import { ChevronRight } from 'lucide-react';
import {
  formatAmountInItsCurrency,
  formatExchangeRate,
  formatSignedShekels,
  profitLossColorClass,
} from '../../lib/format';
import type { SettlementDay, TaxDeductibleExpense } from '../../types/taxes';
import { HelpOnHover } from '../HelpOnHover';
import { Hint } from '../Hint';
import { SharesSoldFromEachBuyTable } from './SharesSoldFromEachBuyTable';
import { FEES_TEXT, sharesText, stockSalesOfTheDay, type StockSalesOfTheDay } from './stockSalesOfTheDay';
import { FIRST_IN_FIRST_OUT_TEXT } from './taxTexts';

/** Enter or Space opens a line, as a click does. */
function isOpeningKey(event: KeyboardEvent): boolean {
  return event.key === 'Enter' || event.key === ' ';
}

/**
 * One stock's line in the opened day: the symbol and its shares, "3 parts" when IB reported the sale in parts, and its
 * taxable gain — with the first-in-first-out "?" beside it when the Closed page counts its cost otherwise. A click opens
 * the buys it took from.
 */
function StockSalesLine({
  stockSales,
  isOpen,
  onToggle,
}: {
  stockSales: StockSalesOfTheDay;
  isOpen: boolean;
  onToggle: () => void;
}) {
  const partCount = stockSales.parts.length;
  return (
    <div className="border-b last:border-b-0">
      <div
        role="button"
        tabIndex={0}
        aria-expanded={isOpen}
        onClick={onToggle}
        onKeyDown={(event) => {
          if (isOpeningKey(event)) {
            event.preventDefault();
            onToggle();
          }
        }}
        className="flex h-10 cursor-pointer items-center gap-2 px-3 outline-none hover:bg-subtle focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
      >
        <ChevronRight size={16} className={`shrink-0 text-muted transition-transform ${isOpen ? 'rotate-90' : ''}`} />
        <span className="font-semibold">{stockSales.symbol}</span>
        <span>· {sharesText(stockSales.quantity)}</span>
        {partCount > 1 && <span className="text-caption text-faint">{partCount} parts</span>}
        <span className="ml-auto flex items-center gap-1">
          {stockSales.fifoCostDiffersFromAverageCost && (
            <HelpOnHover label="Why the Closed page shows another gain">{FIRST_IN_FIRST_OUT_TEXT}</HelpOnHover>
          )}
          <span className={`tabular-nums ${profitLossColorClass(stockSales.taxableGain)}`}>
            {formatSignedShekels(stockSales.taxableGain)}
          </span>
        </span>
      </div>
      {isOpen && <SharesSoldFromEachBuyTable stockSales={stockSales} />}
    </div>
  );
}

/** The fee's hint: its amount in its own currency, the rate it was turned into shekels at, and IB's words. */
function feeHintOf(fee: TaxDeductibleExpense): string {
  const rateText = fee.rate === null ? ' · its rate isn’t stored yet' : ` at ${formatExchangeRate(fee.rate)}`;
  return `${formatAmountInItsCurrency(fee.amount, fee.currency)}${rateText} · ${fee.description}`;
}

/** One of IB's fees in the opened day: "Fees · market data −₪35.76", its dollars, rate and IB's words in its hint. */
function FeeLine({ fee }: { fee: TaxDeductibleExpense }) {
  return (
    <div className="flex h-10 items-center gap-2 border-b px-3 last:border-b-0">
      <span className="w-4 shrink-0" />
      <Hint text={feeHintOf(fee)} align="start">
        {FEES_TEXT}
      </Hint>
      <span className={`ml-auto tabular-nums ${profitLossColorClass(fee.amountInShekels)}`}>
        {formatSignedShekels(fee.amountInShekels)}
      </span>
    </div>
  );
}

/**
 * A settlement day opened: a line for each stock sold that day and one for each of IB's fees. `openedStockSales` holds
 * the symbols opened on this day.
 */
export function DaySales({
  settlementDay,
  openedStockSales,
  onToggleStockSales,
}: {
  settlementDay: SettlementDay;
  openedStockSales: Set<string>;
  onToggleStockSales: (symbol: string) => void;
}) {
  return (
    <div className="overflow-hidden rounded-md border bg-surface">
      {stockSalesOfTheDay(settlementDay).map((stockSales) => (
        <StockSalesLine
          key={stockSales.symbol}
          stockSales={stockSales}
          isOpen={openedStockSales.has(stockSales.symbol)}
          onToggle={() => onToggleStockSales(stockSales.symbol)}
        />
      ))}
      {settlementDay.expenses.map((fee) => (
        <FeeLine key={fee.ibTransactionId} fee={fee} />
      ))}
    </div>
  );
}
