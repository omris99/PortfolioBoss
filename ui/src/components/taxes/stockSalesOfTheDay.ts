import { formatQuantity } from '../../lib/format';
import type { Sale, SettlementDay } from '../../types/taxes';

/**
 * One stock's sales of one settlement day. IB reports an order sold in parts as a sale for each part, each with the
 * buys it took from — so a day opened shows a line per stock, and the stock's table a row per part and buy.
 */
export interface StockSalesOfTheDay {
  symbol: string;
  currency: string;
  /** Every part, in the order IB reported them. */
  parts: Sale[];
  /** Every part's shares together. */
  quantity: number;
  /** In shekels, every part's together; `null` when a part was not calculated. */
  taxableGain: number | null;
  /** A part's cost first in first out differs from its cost at the Closed page's average cost. */
  fifoCostDiffersFromAverageCost: boolean;
}

/** The day's sales by stock, in the order their first part comes. */
export function stockSalesOfTheDay(settlementDay: SettlementDay): StockSalesOfTheDay[] {
  const partsBySymbol = new Map<string, Sale[]>();
  for (const sale of settlementDay.sales) {
    partsBySymbol.set(sale.symbol, [...(partsBySymbol.get(sale.symbol) ?? []), sale]);
  }
  return [...partsBySymbol.entries()].map(([symbol, parts]) => ({
    symbol,
    currency: parts[0].currency,
    parts,
    quantity: parts.reduce((sharesSoFar, part) => sharesSoFar + part.quantity, 0),
    taxableGain: parts.some((part) => part.taxableGain === null)
      ? null
      : parts.reduce((gainSoFar, part) => gainSoFar + (part.taxableGain ?? 0), 0),
    fifoCostDiffersFromAverageCost: parts.some((part) => part.fifoCostDiffersFromAverageCost),
  }));
}

/** "20 shares", "1 share". */
export function sharesText(quantity: number): string {
  return quantity === 1 ? '1 share' : `${formatQuantity(quantity)} shares`;
}

/** The fees' line, wherever a day has one. */
export const FEES_TEXT = 'Fees · market data';

/** The most stocks the Sales cell names one by one; with more it counts them. */
const MOST_STOCKS_NAMED = 3;

/**
 * The day row's Sales cell: "TSLA · 20 shares" for one stock, "PRVX · TELQ" for two or three, "6 stocks" for more,
 * "Fees · market data" for a day of fees only — and "· fees" after the stocks on a day that has both.
 */
export function salesCellText(settlementDay: SettlementDay): string {
  const stocks = stockSalesOfTheDay(settlementDay);
  const hasFees = settlementDay.expenses.length > 0;
  if (stocks.length === 0) return hasFees ? FEES_TEXT : '';

  const stocksText = stocksOfTheDayText(stocks);
  return hasFees ? `${stocksText} · fees` : stocksText;
}

function stocksOfTheDayText(stocks: StockSalesOfTheDay[]): string {
  if (stocks.length === 1) return `${stocks[0].symbol} · ${sharesText(stocks[0].quantity)}`;
  if (stocks.length <= MOST_STOCKS_NAMED) return stocks.map((stock) => stock.symbol).join(' · ');
  return `${stocks.length} stocks`;
}
