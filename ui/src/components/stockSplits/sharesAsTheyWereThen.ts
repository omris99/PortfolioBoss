import { isIsoDate } from '../../lib/formInput';
import type { StockSplit } from '../../types/portfolio';

// The one place the UI works out a split itself (STOCK_SPLITS_TODO.md, 2.3): the trade form's backfill starts from IB's
// quantity and average cost, which are in the shares of today, and a trade dated before a split is typed in the shares
// of then. Every other figure after a split is the API's.

/**
 * Whether a trade of `tradeDate` was before the split — on the split's own day too, since IB books a split after that
 * day's market closed. A copy of `StockSplit.isBeforeTheSplit(tradeDay)` in Java: change both together.
 */
export function isBeforeTheSplit(tradeDate: string, stockSplit: StockSplit): boolean {
  return tradeDate <= stockSplit.splitDay;
}

/** The splits a trade of `tradeDate` was before, the earliest first; none while the date isn't typed in full. */
export function splitsAfterTheTradeDate(stockSplits: StockSplit[], tradeDate: string): StockSplit[] {
  if (!isIsoDate(tradeDate)) return [];
  return stockSplits.filter((stockSplit) => isBeforeTheSplit(tradeDate, stockSplit));
}

/** How many shares there were, before all of `stockSplits`, for the shares {@link sharesAfterAllTheSplits} says. */
function sharesBeforeAllTheSplits(stockSplits: StockSplit[]): number {
  return stockSplits.reduce((shares, stockSplit) => shares * stockSplit.sharesBeforeTheSplit, 1);
}

/** After a 4-for-1 split and then a 2-for-1, 1 share before both is 8 after them. */
function sharesAfterAllTheSplits(stockSplits: StockSplit[]): number {
  return stockSplits.reduce((shares, stockSplit) => shares * stockSplit.sharesAfterTheSplit, 1);
}

/** A quantity of today as it was on the trade date: 40 shares after a 4-for-1 split were 10 before it. */
export function quantityAsItWasThen(quantityToday: number, splitsAfterTheTradeDate: StockSplit[]): number {
  return (
    (quantityToday * sharesBeforeAllTheSplits(splitsAfterTheTradeDate)) / sharesAfterAllTheSplits(splitsAfterTheTradeDate)
  );
}

/** A price of today as it was on the trade date: 100.00 after a 4-for-1 split was 400.00 before it. */
export function priceAsItWasThen(priceToday: number, splitsAfterTheTradeDate: StockSplit[]): number {
  return (
    (priceToday * sharesAfterAllTheSplits(splitsAfterTheTradeDate)) / sharesBeforeAllTheSplits(splitsAfterTheTradeDate)
  );
}
