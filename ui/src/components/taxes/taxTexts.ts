import { EMPTY_VALUE, formatDate, formatShekels } from '../../lib/format';
import type { MonthlySettlement, Sale, SaleNotComputedReason, SettlementDay } from '../../types/taxes';

/** The words the Taxes page shares between its parts. "Calculated" is what PortfolioBoss worked out. */

/** Amounts within half an agora are the same amount: the API's are in agorot already. */
const HALF_AN_AGORA = 0.005;

export function isSameAmount(firstAmount: number, secondAmount: number): boolean {
  return Math.abs(firstAmount - secondAmount) < HALF_AN_AGORA;
}

/** A day's or a confirmation's amount as an entry: "Tax ₪1,277.13", "Shield ₪849.38", and "—" for 0. */
export function entryText(taxAmount: number): string {
  if (isSameAmount(taxAmount, 0)) return EMPTY_VALUE;
  return taxAmount > 0 ? `Tax ${formatShekels(taxAmount)}` : `Shield ${formatShekels(-taxAmount)}`;
}

/** "2026-06" → "June 2026". */
export function monthName(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number);
  const monthWord = new Date(year, monthNumber - 1, 1).toLocaleString('en-US', { month: 'long' });
  return `${monthWord} ${year}`;
}

/** The month's net entry in a heading: "tax ₪1,279.40", "shield ₪849.38", or nothing for a month that nets to 0. */
function netEntryOfTheMonthText(entriesOfTheMonth: number): string | null {
  if (isSameAmount(entriesOfTheMonth, 0)) return null;
  return entriesOfTheMonth > 0
    ? `tax ${formatShekels(entriesOfTheMonth)}`
    : `shield ${formatShekels(-entriesOfTheMonth)}`;
}

/** How an ended month settled: "→ paid", "→ refunded ₪750.00" (an amount only when it isn't the entry's), "→ carried". */
function settlementOfAnEndedMonthText(monthlySettlement: MonthlySettlement): string {
  const { entriesOfTheMonth, settlementAmount } = monthlySettlement;
  if (settlementAmount > 0) {
    return isSameAmount(settlementAmount, entriesOfTheMonth) ? '→ paid' : `→ paid ${formatShekels(settlementAmount)}`;
  }
  if (settlementAmount < 0) {
    return isSameAmount(settlementAmount, entriesOfTheMonth)
      ? '→ refunded'
      : `→ refunded ${formatShekels(-settlementAmount)}`;
  }
  // Nothing moved: a shield with no tax paid this year to refund, or a tax an earlier shield covered.
  return entriesOfTheMonth < 0 ? '→ carried' : '→ covered by the shield';
}

/**
 * What a month's heading says after its name: "tax ₪1,279.40 → paid", "shield ₪849.38 → refunded", "tax ₪34.98 · not
 * settled yet", "nothing to settle". A confirmed day counts as the broker recorded it — the API's months are so.
 */
export function monthHeadingText(monthlySettlement: MonthlySettlement): string {
  const netEntry = netEntryOfTheMonthText(monthlySettlement.entriesOfTheMonth);
  if (!monthlySettlement.settled) {
    return netEntry === null ? 'not settled yet' : `${netEntry} · not settled yet`;
  }
  if (netEntry === null && isSameAmount(monthlySettlement.settlementAmount, 0)) return 'nothing to settle';
  return `${netEntry ?? ''} ${settlementOfAnEndedMonthText(monthlySettlement)}`.trim();
}

/** The reasons no later sync will change: the broker's amount is typed in instead. */
type ReasonThatCannotBeCalculated = Exclude<SaleNotComputedReason, 'SOLD_MORE_THAN_BOUGHT' | 'RATE_NOT_PUBLISHED_YET'>;

/** Why a sale can't be calculated, as the line under its day says it. */
export const NOT_COMPUTED_REASON_LINES: Record<ReasonThatCannotBeCalculated, (sale: Sale) => string> = {
  NO_RATE_FOR_THE_CURRENCY: (sale) => `Not calculated: in ${sale.currency}, there is no rate for it.`,
  CORPORATE_ACTION_NOT_SUPPORTED: () => 'Not calculated: a corporate action of this stock isn’t supported.',
  COMMISSION_IN_ANOTHER_CURRENCY: () => 'Not calculated: its commission is in another currency.',
};

const TYPE_THE_AMOUNT_TEXT = 'Type the amount from the statement.';

function isReasonThatCannotBeCalculated(reason: SaleNotComputedReason | null): reason is ReasonThatCannotBeCalculated {
  return reason !== null && reason in NOT_COMPUTED_REASON_LINES;
}

/**
 * The line under a day the user must do something about — a sale that can't be calculated, a sale beyond its buys —,
 * or `null`. On Portfolio a sale that can't be calculated also says to type the broker's amount; an investor's tab is
 * read only.
 */
export function lineUnderTheDayText(settlementDay: SettlementDay, isPortfolioTab: boolean): string | null {
  if (settlementDay.status === 'SOLD_MORE_THAN_BOUGHT') return 'Sold more than bought: check its trades.';
  if (settlementDay.status !== 'CANNOT_BE_CALCULATED') return null;

  for (const sale of settlementDay.sales) {
    if (isReasonThatCannotBeCalculated(sale.notComputedReason)) {
      const reasonLine = NOT_COMPUTED_REASON_LINES[sale.notComputedReason](sale);
      return isPortfolioTab ? `${reasonLine} ${TYPE_THE_AMOUNT_TEXT}` : reasonLine;
    }
  }
  return null;
}

/** The clock's explanation, on a day that waits for the Bank of Israel. */
export function waitingForARateText(settlementDay: string): string {
  return `Bank of Israel’s rate for ${formatDate(settlementDay)} isn’t stored yet: it comes with a sync after it is published.`;
}

/** The hint of a day calculated as 0, on its "₪0.00". */
export const CALCULATED_TO_ZERO_TEXT = 'No tax and no shield: a gain one way and a loss the other counts as 0.';

/** The "?" beside a sale whose cost first in first out isn't the Closed page's. */
export const FIRST_IN_FIRST_OUT_TEXT =
  'For tax: first in, first out, as the broker counts. The Closed page uses the average cost.';

/** Whether every sale of the day was calculated — then its calculated amount means something to compare with. */
export function isEverySaleCalculated(settlementDay: SettlementDay): boolean {
  return settlementDay.sales.every((sale) => sale.notComputedReason === null);
}
