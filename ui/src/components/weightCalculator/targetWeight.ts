import { ACCOUNT_CURRENCY } from '../../lib/format';
import { defaultCommissionFor } from '../../lib/orderCommission';
import type { Holding } from '../../types/portfolio';
import type { WholeAccount } from '../graphs/portfolioWeights';

/**
 * The weight calculator's arithmetic: how many whole shares of one holding to sell or buy so that it weighs a target
 * percent of the whole account — the stocks plus the cash, as in the Overview's graphs. A sale turns shares into cash
 * and a purchase turns cash into shares, so the whole account keeps its size; the commission does come off it, but is
 * too small to change the share count, so the weights leave it out.
 */

/** Below this, a share count counts as whole: 30.0000000001 shares is 30, not 31, after the division's rounding. */
const ROUNDING_TOLERANCE = 1e-9;

/** The holding and the account, every figure the calculator needs known. */
export interface HoldingInAccount {
  quantity: number;
  /** IB's last price, as of the last sync. */
  price: number;
  /** IB's average cost a share, commissions included; `null` when IB reported none. */
  averageCost: number | null;
  wholeAccount: WholeAccount;
}

/** Why the calculator can't help with a holding, in words shown as they are. */
export interface CannotCalculate {
  reason: string;
}

/**
 * The figures the calculator needs, or why it can't work on this holding. Another currency can't be weighed against a
 * dollar account without IB's exchange rate, which the API doesn't send yet.
 */
export function holdingInAccountOf(holding: Holding, wholeAccount: WholeAccount | null): HoldingInAccount | CannotCalculate {
  if (holding.currency !== ACCOUNT_CURRENCY) {
    return { reason: `This holding is in ${holding.currency}: the calculator works in ${ACCOUNT_CURRENCY} only for now.` };
  }
  if (holding.marketPrice === null || holding.marketPrice <= 0) return { reason: 'IB reported no price for this holding.' };
  if (holding.position <= 0) return { reason: 'This is not a long position.' };
  if (wholeAccount === null) return { reason: 'The whole account’s value is unknown: IB reported no cash.' };
  return { quantity: holding.position, price: holding.marketPrice, averageCost: holding.averageCost, wholeAccount };
}

/** The holding's part of the whole account, 0–100. */
export function percentOfAccountOf(holdingInAccount: HoldingInAccount, quantity = holdingInAccount.quantity): number {
  return ((quantity * holdingInAccount.price) / holdingInAccount.wholeAccount.value) * 100;
}

/** What a sale or a purchase comes to. */
interface TradeFigures {
  shareCount: number;
  /** The shares at the price: what a sale brings in, or what a purchase costs, before the commission. */
  amount: number;
  /** The default commission, as a trade entered without one would get. */
  commission: number;
  quantityAfter: number;
  percentAfter: number;
}

export type WeightPlan =
  /** `realizedPnl` at IB's average cost, the commission off; `null` without an average cost. */
  | ({ action: 'sell'; realizedPnl: number | null } & TradeFigures)
  /** `isCoveredByCash`: the cash pays for the shares and the commission. */
  | ({ action: 'buy'; isCoveredByCash: boolean } & TradeFigures)
  /** Already within one share of the target: no whole share brings it closer. */
  | { action: 'none' };

function tradeFiguresOf(holdingInAccount: HoldingInAccount, shareCount: number, quantityAfter: number): TradeFigures {
  return {
    shareCount,
    amount: shareCount * holdingInAccount.price,
    commission: defaultCommissionFor(shareCount),
    quantityAfter,
    percentAfter: percentOfAccountOf(holdingInAccount, quantityAfter),
  };
}

/**
 * Selling, enough whole shares to land at the target or just under it — at most every share; buying, as many whole
 * shares as stay at the target or just under it.
 */
export function weightPlanOf(holdingInAccount: HoldingInAccount, targetPercent: number): WeightPlan {
  const { quantity, price, averageCost, wholeAccount } = holdingInAccount;
  const targetValue = (targetPercent / 100) * wholeAccount.value;
  const sharesFromTarget = (targetValue - quantity * price) / price;

  if (sharesFromTarget < 0) {
    const shareCount = Math.min(Math.ceil(-sharesFromTarget - ROUNDING_TOLERANCE), quantity);
    if (shareCount === 0) return { action: 'none' };
    const figures = tradeFiguresOf(holdingInAccount, shareCount, quantity - shareCount);
    const realizedPnl = averageCost === null ? null : shareCount * (price - averageCost) - figures.commission;
    return { action: 'sell', realizedPnl, ...figures };
  }

  const shareCount = Math.floor(sharesFromTarget + ROUNDING_TOLERANCE);
  if (shareCount === 0) return { action: 'none' };
  const figures = tradeFiguresOf(holdingInAccount, shareCount, quantity + shareCount);
  return { action: 'buy', isCoveredByCash: figures.amount + figures.commission <= wholeAccount.cash, ...figures };
}

/** The target typed in, as a percent; or why it can't be used. Empty is neither: nothing is calculated yet. */
export function targetPercentOf(typedText: string): number | CannotCalculate | null {
  if (typedText.trim() === '') return null;
  const targetPercent = Number(typedText);
  if (!Number.isFinite(targetPercent)) return { reason: 'Type the target as a number, like 15 or 12.5.' };
  if (targetPercent < 0 || targetPercent > 100) return { reason: 'A weight is between 0% and 100%.' };
  return targetPercent;
}
