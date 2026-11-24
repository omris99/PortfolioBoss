import { formatMoney, formatQuantity } from '../../lib/format';
import type { StockSplit, Trade } from '../../types/portfolio';

/** "4 for 1", as IB writes it ("SPLIT 4 FOR 1"): the shares after the split, for the shares before it. */
export function ratioInIbsWords(stockSplit: StockSplit): string {
  return `${stockSplit.sharesAfterTheSplit} for ${stockSplit.sharesBeforeTheSplit}`;
}

/** "the 4-for-1 split of 2026-03-10", inside a sentence. */
export function splitWithItsDay(stockSplit: StockSplit): string {
  return `the ${stockSplit.sharesAfterTheSplit}-for-${stockSplit.sharesBeforeTheSplit} split of ${stockSplit.splitDay}`;
}

function sharesWord(shareCount: number): string {
  return shareCount === 1 ? 'share' : 'shares';
}

/** What the split's line in the trades says on hover. */
export function whatTheSplitMeans(stockSplit: StockSplit): string {
  const { splitDay, sharesBeforeTheSplit, sharesAfterTheSplit } = stockSplit;
  return (
    `Since ${splitDay} IB counts ${sharesAfterTheSplit} ${sharesWord(sharesAfterTheSplit)} for every ` +
    `${sharesBeforeTheSplit} held before. The shares held then count that way here; a position sold in full before ` +
    'it stays as it was.'
  );
}

/** The trade form's note, for a date on or before the split. */
export function typeTheTradeAsItWasThen(stockSplit: StockSplit): string {
  return `This date is before ${splitWithItsDay(stockSplit)}: type the shares and price as they were then.`;
}

/** "10 at 400.00 before the split": a trade a split translated, as it was typed. */
export function tradeBeforeTheSplitText(trade: Trade): string {
  return `${formatQuantity(trade.quantity)} at ${formatMoney(trade.price)} before the split`;
}
