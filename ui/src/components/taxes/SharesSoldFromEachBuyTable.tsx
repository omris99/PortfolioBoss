import {
  EMPTY_VALUE,
  formatAmountInItsCurrency,
  formatDate,
  formatExchangeRate,
  formatQuantity,
  formatShekels,
  formatSignedShekels,
  profitLossColorClass,
} from '../../lib/format';
import { INNER_TABLE_CLASS, INNER_TABLE_FRAME_CLASS } from '../../lib/styles';
import type { Sale, SharesSoldFromOneBuy } from '../../types/taxes';
import { HelpOnHover } from '../HelpOnHover';
import { Hint } from '../Hint';
import type { StockSalesOfTheDay } from './stockSalesOfTheDay';
import { isSameAmount } from './taxTexts';

/** One row of the table: the shares one part of the sale took from one buy. */
interface SharesSoldRow {
  part: Sale;
  /** 1 for the first part IB reported. */
  partNumber: number;
  sharesSold: SharesSoldFromOneBuy;
  /** The first row of a run of rows from the same buy: its date shows, and a line comes above it. */
  startsANewBuy: boolean;
}

/** Every part of the stock's sale, and in each the buys it took from, in that order. */
function sharesSoldRowsOf(stockSales: StockSalesOfTheDay): SharesSoldRow[] {
  const rows: SharesSoldRow[] = [];
  stockSales.parts.forEach((part, partIndex) => {
    for (const sharesSold of part.sharesSoldFromEachBuy) {
      const previousRow = rows.at(-1);
      rows.push({
        part,
        partNumber: partIndex + 1,
        sharesSold,
        startsANewBuy: previousRow === undefined || previousRow.sharesSold.buyTradeId !== sharesSold.buyTradeId,
      });
    }
  });
  return rows;
}

/** An amount of the trade's currency in shekels at a rate; `null` while the rate isn't known. */
function inShekels(amount: number, rate: number | null): number | null {
  return rate === null ? null : amount * rate;
}

const HEADER_CELL_CLASS = 'h-9 whitespace-nowrap border-b bg-subtle px-2 text-label text-muted first:pl-3 last:pr-3';
const CELL_CLASS = 'h-10 whitespace-nowrap px-2 tabular-nums first:pl-3 last:pr-3';

const RULE_TEXT =
  'Each buy is calculated two ways, in dollars and in shekels, and the result closer to zero counts. A gain one way ' +
  'and a loss the other counts as 0.';

/** A figure in shekels with its amount in the trade's currency and the rate in its hint: "$4,205.00 at 3.750". */
function ShekelsWithTheirRate({
  amount,
  currency,
  rate,
  partText = '',
}: {
  amount: number;
  currency: string;
  rate: number | null;
  /** "Part 2 of 3: 7 shares at $342.25 · " before it, on a sale IB reported in parts. */
  partText?: string;
}) {
  const rateText = rate === null ? '' : ` at ${formatExchangeRate(rate)}`;
  return (
    <Hint text={`${partText}${formatAmountInItsCurrency(amount, currency)}${rateText}`} align="end">
      {formatShekels(inShekels(amount, rate))}
    </Hint>
  );
}

/**
 * The two ways of one row and what counts (CAPITAL_GAINS_TAX_TODO.md, decision 11): the way that counts in its tone at
 * 600, the other muted; bought and sold at one rate, the two ways are one result. Nothing on a sale not calculated.
 */
function TwoWaysAndWhatCounts({ sharesSold }: { sharesSold: SharesSoldFromOneBuy }) {
  const { buyRate, saleRate, gainInDollarsAtTheSaleRate, gainInShekels, taxableGain } = sharesSold;
  if (taxableGain === null || gainInDollarsAtTheSaleRate === null || gainInShekels === null) {
    return (
      <>
        <td className={`${CELL_CLASS} text-right text-muted`}>{EMPTY_VALUE}</td>
        <td className={`${CELL_CLASS} text-right text-muted`}>{EMPTY_VALUE}</td>
        <td className={`${CELL_CLASS} text-right text-muted`}>{EMPTY_VALUE}</td>
      </>
    );
  }
  const countedClass = `font-semibold ${profitLossColorClass(taxableGain)}`;
  const counts = <td className={`${CELL_CLASS} text-right ${countedClass}`}>{formatSignedShekels(taxableGain)}</td>;
  if (buyRate !== null && saleRate !== null && isSameAmount(buyRate, saleRate)) {
    return (
      <>
        <td colSpan={2} className={`${CELL_CLASS} text-center text-caption text-faint`}>
          one rate: one result
        </td>
        {counts}
      </>
    );
  }
  const doesTheDollarWayCount = isSameAmount(gainInDollarsAtTheSaleRate, taxableGain);
  const doesTheShekelWayCount = !doesTheDollarWayCount && isSameAmount(gainInShekels, taxableGain);
  return (
    <>
      <td className={`${CELL_CLASS} text-right ${doesTheDollarWayCount ? countedClass : 'text-muted'}`}>
        {formatSignedShekels(gainInDollarsAtTheSaleRate)}
      </td>
      <td className={`${CELL_CLASS} text-right ${doesTheShekelWayCount ? countedClass : 'text-muted'}`}>
        {formatSignedShekels(gainInShekels)}
      </td>
      {counts}
    </>
  );
}

/** The buy date's hint: "Settled 18/06/2026 · rate 3.750". */
function buySettledText(sharesSold: SharesSoldFromOneBuy): string {
  const rateText = sharesSold.buyRate === null ? '' : ` · rate ${formatExchangeRate(sharesSold.buyRate)}`;
  return `Settled ${formatDate(sharesSold.buySettlementDay)}${rateText}`;
}

function SharesSoldTableRow({ row, partCount }: { row: SharesSoldRow; partCount: number }) {
  const { part, partNumber, sharesSold, startsANewBuy } = row;
  const partText =
    partCount > 1
      ? `Part ${partNumber} of ${partCount}: ${formatQuantity(part.quantity)} shares at ${formatAmountInItsCurrency(part.price, part.currency)} · `
      : '';
  return (
    <tr className={startsANewBuy ? 'border-t first:border-t-0' : ''}>
      <td className={CELL_CLASS}>
        {startsANewBuy && (
          <Hint text={buySettledText(sharesSold)} align="start">
            {formatDate(sharesSold.buyTradeDate)}
          </Hint>
        )}
      </td>
      <td className={`${CELL_CLASS} text-right`}>{formatQuantity(sharesSold.quantity)}</td>
      <td className={`${CELL_CLASS} text-right`}>
        <ShekelsWithTheirRate
          amount={sharesSold.cost}
          currency={part.currency}
          rate={sharesSold.buyRate}
        />
      </td>
      <td className={`${CELL_CLASS} text-right`}>
        <ShekelsWithTheirRate
          amount={sharesSold.proceeds}
          currency={part.currency}
          rate={sharesSold.saleRate}
          partText={partText}
        />
      </td>
      <TwoWaysAndWhatCounts sharesSold={sharesSold} />
    </tr>
  );
}

/**
 * A stock's sale of a day opened into the buys it took from, first in first out: a row for each part IB reported and
 * each buy it took from — the buy's date once for its run of rows, the shares telling the parts apart, each part's price
 * in the hint of Sold for. Cost and Sold for in shekels, at the buy's and the sale's settlement days' rates; then the two
 * ways of the taxable gain and what counts.
 */
export function SharesSoldFromEachBuyTable({ stockSales }: { stockSales: StockSalesOfTheDay }) {
  const rows = sharesSoldRowsOf(stockSales);
  return (
    <div className="px-3 pb-3">
      <div className={INNER_TABLE_FRAME_CLASS}>
        <table className={INNER_TABLE_CLASS}>
          <thead>
            <tr>
              <th className={`${HEADER_CELL_CLASS} text-left`}>Bought</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>Shares</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>Cost</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>Sold for</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>In dollars</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>In shekels</th>
              <th className={`${HEADER_CELL_CLASS} text-right`}>
                <span className="inline-flex items-center gap-1">
                  Counts
                  <HelpOnHover label="Which result counts">{RULE_TEXT}</HelpOnHover>
                </span>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <SharesSoldTableRow
                key={`${row.part.tradeId}-${row.sharesSold.buyTradeId}`}
                row={row}
                partCount={stockSales.parts.length}
              />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
