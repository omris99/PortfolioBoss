import { ACCOUNT_CURRENCY, formatMoney } from '../../lib/format';
import { defaultCommissionFor } from '../../lib/orderCommission';
import type { Holding } from '../../types/portfolio';
import { investmentIn, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { wholeAccountOf, type WholeAccount } from '../graphs/portfolioWeights';

/**
 * The weight calculator's arithmetic: how many whole shares of one holding to sell or buy so that it weighs a target
 * percent of the whole account — the stocks plus the cash, as in the Overview's graphs — or, on an investor's tab, of all
 * of that investor's money, with their shares and their cash; or how many whole shares an amount buys. A sale turns
 * shares into cash and a purchase turns cash into shares, so the whole account keeps its size; the commission does come
 * off it, but is too small to change the share count, so the weights leave it out. A purchase also says what it does to
 * the average cost, and how far the price must rise from today's to reach the average.
 */

/** Below this, a share count counts as whole: 30.0000000001 shares is 30, not 31, after the division's rounding. */
const ROUNDING_TOLERANCE = 1e-9;

/** The holding and the account, every figure the calculator needs known — on an investor's tab, their part of both. */
export interface HoldingInAccount {
  /** 0 on the tab of an investor who holds none: only buying is worked out then. */
  quantity: number;
  /** IB's last price, as of the last sync. */
  price: number;
  /** A share, commissions included — IB's, or the investor's own; `null` when unknown. */
  averageCost: number | null;
  wholeAccount: WholeAccount;
}

/** Why the calculator can't help with a holding, in words shown as they are. */
export interface CannotCalculate {
  reason: string;
}

/**
 * A short position on Portfolio; on an investor's tab, the account owner's part when the other investors' trades add up
 * to more shares than IB reports.
 */
function belowZeroReasonOf(tab: InvestmentsAndCash): string {
  return tab.investorId === null
    ? 'This is not a long position.'
    : `${tab.name}’s part of this holding is below zero: check its trades.`;
}

/**
 * The figures the calculator needs for `holding` on `tab`, or why it can't work on it. Another currency can't be
 * weighed against a dollar account without IB's exchange rate, which the API doesn't send yet.
 */
export function holdingInAccountOf(holding: Holding, tab: InvestmentsAndCash): HoldingInAccount | CannotCalculate {
  if (holding.currency !== ACCOUNT_CURRENCY) {
    return { reason: `This holding is in ${holding.currency}: the calculator works in ${ACCOUNT_CURRENCY} only for now.` };
  }
  if (holding.marketPrice === null || holding.marketPrice <= 0) return { reason: 'IB reported no price for this holding.' };
  const investment = investmentIn(tab, holding);
  if (investment.quantity < 0) return { reason: belowZeroReasonOf(tab) };
  const wholeAccount = wholeAccountOf(tab);
  if (wholeAccount === null) {
    return { reason: `No weight can be worked out: ${tab.wholeName} is unknown (IB reported no cash) or not above zero.` };
  }
  return { quantity: investment.quantity, price: holding.marketPrice, averageCost: investment.averageCost, wholeAccount };
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

/** What a purchase does to the average cost of a share, and how far the price must rise from today's to reach it. */
export interface AverageCostAfterPurchase {
  /** IB's, or the investor's own: commissions included. */
  currentAverageCost: number;
  /** With the shares bought and their commission, counted in as IB counts commissions in its average cost. */
  newAverageCost: number;
  /** (the average − the price) ÷ the price, in percent: +25 for an average of 50 at a price of 40; 0 or below at or above it. */
  riseNeededToReachCurrentAveragePercent: number;
  riseNeededToReachNewAveragePercent: number;
  /** (the price − the average) ÷ the average, in percent: 4 for a price of 52 and an average of 50; below 0 under it. */
  priceAboveCurrentAveragePercent: number;
}

export type WeightPlan =
  /** `realizedPnl` at IB's average cost, the commission off; `null` without an average cost. */
  | ({ action: 'sell'; realizedPnl: number | null } & TradeFigures)
  /**
   * `isCoveredByCash`: the cash pays for the shares and the commission. `averageCostAfterPurchase`: `null` without an
   * average cost, or without shares held — there is no average to change then.
   */
  | ({ action: 'buy'; isCoveredByCash: boolean; averageCostAfterPurchase: AverageCostAfterPurchase | null } & TradeFigures)
  /** Already within one share of the target: no whole share brings it closer. */
  | { action: 'none' };

/** A purchase, worked out from a target weight or from an amount. */
export type PurchasePlan = Extract<WeightPlan, { action: 'buy' }>;

function tradeFiguresOf(holdingInAccount: HoldingInAccount, shareCount: number, quantityAfter: number): TradeFigures {
  return {
    shareCount,
    amount: shareCount * holdingInAccount.price,
    commission: defaultCommissionFor(shareCount),
    quantityAfter,
    percentAfter: percentOfAccountOf(holdingInAccount, quantityAfter),
  };
}

/** How far the price must rise to reach `average`, in percent of the price. */
function calculateRiseNeededToReachAveragePercent(average: number, price: number): number {
  return ((average - price) / price) * 100;
}

/**
 * What buying `sharesToBuy` more shares for `commission` does to the average cost. Example (MY_HOLDINGS_TODO.md,
 * decision 2): 100 shares at an average of 50, a price of 40, 50 more bought for a commission of 5 — (5,000 + 2,000 + 5)
 * ÷ 150 = 46.70, which the price must rise 16.75% to reach, against 25% to reach 50.
 */
function calculateAverageCostAfterPurchase(
  holdingInAccount: HoldingInAccount,
  sharesToBuy: number,
  commission: number,
): AverageCostAfterPurchase | null {
  const { quantity, price, averageCost } = holdingInAccount;
  if (averageCost === null || quantity === 0) return null;
  const newAverageCost = (quantity * averageCost + sharesToBuy * price + commission) / (quantity + sharesToBuy);
  return {
    currentAverageCost: averageCost,
    newAverageCost,
    riseNeededToReachCurrentAveragePercent: calculateRiseNeededToReachAveragePercent(averageCost, price),
    riseNeededToReachNewAveragePercent: calculateRiseNeededToReachAveragePercent(newAverageCost, price),
    priceAboveCurrentAveragePercent: ((price - averageCost) / averageCost) * 100,
  };
}

/** Buying `sharesToBuy` whole shares: what they cost, the weight after, the cash against it, and the new average. */
function buildPurchasePlan(holdingInAccount: HoldingInAccount, sharesToBuy: number): PurchasePlan {
  const figures = tradeFiguresOf(holdingInAccount, sharesToBuy, holdingInAccount.quantity + sharesToBuy);
  return {
    action: 'buy',
    isCoveredByCash: figures.amount + figures.commission <= holdingInAccount.wholeAccount.cash,
    averageCostAfterPurchase: calculateAverageCostAfterPurchase(holdingInAccount, sharesToBuy, figures.commission),
    ...figures,
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
  return buildPurchasePlan(holdingInAccount, shareCount);
}

/** The whole shares `amount` buys at `price`; or why it buys none. */
export function calculateWholeSharesForAmount(amount: number, price: number): number | CannotCalculate {
  const sharesToBuy = Math.floor(amount / price + ROUNDING_TOLERANCE);
  if (sharesToBuy === 0) return { reason: `${formatMoney(amount)} buys no whole share at ${formatMoney(price)}.` };
  return sharesToBuy;
}

/** Buying the whole shares `amount` buys at IB's last price; or why it buys none. */
export function buildPurchasePlanForAmount(holdingInAccount: HoldingInAccount, amount: number): PurchasePlan | CannotCalculate {
  const sharesToBuy = calculateWholeSharesForAmount(amount, holdingInAccount.price);
  if (typeof sharesToBuy !== 'number') return sharesToBuy;
  return buildPurchasePlan(holdingInAccount, sharesToBuy);
}

/** The target typed in, as a percent; or why it can't be used. Empty is neither: nothing is calculated yet. */
export function targetPercentOf(typedText: string): number | CannotCalculate | null {
  if (typedText.trim() === '') return null;
  const targetPercent = Number(typedText);
  if (!Number.isFinite(targetPercent)) return { reason: 'Type the target as a number, like 15 or 12.5.' };
  if (targetPercent < 0 || targetPercent > 100) return { reason: 'A weight is between 0% and 100%.' };
  return targetPercent;
}
