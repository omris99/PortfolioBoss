import type { Holding } from '../../types/portfolio';
import type { InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { amountOf } from '../weightCalculator/ifIBuy';
import {
  buildPurchasePlanForAmount,
  calculatePriceAboveAveragePercent,
  calculateRiseNeededToReachAveragePercent,
  holdingInAccountOf,
  percentOfAccountOf,
  targetPercentOf,
  weightPlanOf,
  type CannotCalculate,
  type HoldingInAccount,
  type PurchasePlan,
} from '../weightCalculator/targetWeight';

/**
 * The Averaging calculator's arithmetic (MY_HOLDINGS_TODO.md, decision 13; DESIGN_BRIEF.md §9): on one tab, which
 * holdings are below their average cost and which at or above it, what is typed on each holding's row comes to — the
 * Weight tab's purchase, unchanged (targetWeight.ts) —, and every purchase typed added up against the tab's cash.
 *
 * The purchases don't change one another: a purchase turns cash into shares, so the whole account keeps its size, and
 * each row's weight after is worked out as if it were the only purchase. The one thing they share is the cash.
 */

/** A holding on the page: its figures on the tab, and how its price stands against its average cost today. */
export interface AveragingRow {
  holding: Holding;
  holdingInAccount: HoldingInAccount;
  /** The tab's average cost of a share: IB's on Portfolio, the investor's own on their tab. */
  averageCost: number;
  isBelowAverage: boolean;
  /** How far the price must rise to reach the average, in percent of the price: +21.9 for 905.10 at 742.60. */
  riseNeededToReachAveragePercent: number;
  /** How far the price is above the average, in percent of the average: 12.4 for 301.80 against 268.40. */
  priceAboveAveragePercent: number;
}

/** A holding of the tab the page can't work on, and why, in words shown as they are. */
export interface LeftOutHolding {
  holding: Holding;
  reason: string;
}

export interface HoldingsSplitByAverage {
  /** The farthest below its average first: the one the price must rise most to bring back to it. */
  belowAverage: AveragingRow[];
  /** The closest to its average first. */
  atOrAboveAverage: AveragingRow[];
  leftOut: LeftOutHolding[];
}

/** The holding as a row of the page, or why it is left out: the Weight tab's reasons, and an average cost unknown. */
function buildAveragingRowOrLeftOut(holding: Holding, tab: InvestmentsAndCash): AveragingRow | LeftOutHolding {
  const holdingInAccount = holdingInAccountOf(holding, tab);
  if ('reason' in holdingInAccount) return { holding, reason: holdingInAccount.reason };
  const { averageCost, price } = holdingInAccount;
  if (averageCost === null) return { holding, reason: `No average to bring down: ${tab.averageCostName} is unknown.` };
  return {
    holding,
    holdingInAccount,
    averageCost,
    isBelowAverage: price < averageCost,
    riseNeededToReachAveragePercent: calculateRiseNeededToReachAveragePercent(averageCost, price),
    priceAboveAveragePercent: calculatePriceAboveAveragePercent(price, averageCost),
  };
}

/** Equal ones by symbol, so the order never changes between two loads. */
function compareSymbols(first: AveragingRow, second: AveragingRow): number {
  return first.holding.symbol.localeCompare(second.holding.symbol);
}

/**
 * The tab's holdings — on an investor's tab, only those they hold some of —, split by where the price stands against
 * the average cost, each part in the page's order, and those the page leaves out.
 */
export function splitHoldingsByAverage(tab: InvestmentsAndCash): HoldingsSplitByAverage {
  const rows: AveragingRow[] = [];
  const leftOut: LeftOutHolding[] = [];
  tab.investments.forEach(({ holding }) => {
    const rowOrLeftOut = buildAveragingRowOrLeftOut(holding, tab);
    if ('reason' in rowOrLeftOut) {
      leftOut.push(rowOrLeftOut);
    } else {
      rows.push(rowOrLeftOut);
    }
  });
  const belowAverage = rows
    .filter((row) => row.isBelowAverage)
    .sort(
      (first, second) =>
        second.riseNeededToReachAveragePercent - first.riseNeededToReachAveragePercent || compareSymbols(first, second),
    );
  const atOrAboveAverage = rows
    .filter((row) => !row.isBelowAverage)
    .sort(
      (first, second) => first.priceAboveAveragePercent - second.priceAboveAveragePercent || compareSymbols(first, second),
    );
  return { belowAverage, atOrAboveAverage, leftOut };
}

/** What is typed on a row: a target weight or an amount — typing in one empties the other. */
export interface TypedTexts {
  targetText: string;
  amountText: string;
}

export const NOTHING_TYPED: TypedTexts = { targetText: '', amountText: '' };

/** What a row comes to. */
export type RowResult =
  | { kind: 'nothingTyped' }
  /** The Weight tab's purchase: the shares, their cost, the commission, the weight after and the new average. */
  | { kind: 'purchase'; purchasePlan: PurchasePlan }
  /** What is typed in `field` can't be used: its border turns red, and `reason` shows with the row. */
  | { kind: 'unusable'; field: 'target' | 'amount'; reason: string }
  /** A target at or under the weight now: the page only buys — selling stays in the drawer's Weight tab. */
  | { kind: 'atOrAboveTarget' }
  /** A target above the weight now that no whole share reaches. */
  | { kind: 'alreadyAtTarget'; targetPercent: number };

function calculateResultOfTarget(row: AveragingRow, target: number | CannotCalculate): RowResult {
  if (typeof target !== 'number') return { kind: 'unusable', field: 'target', reason: target.reason };
  if (target <= percentOfAccountOf(row.holdingInAccount)) return { kind: 'atOrAboveTarget' };
  const weightPlan = weightPlanOf(row.holdingInAccount, target);
  if (weightPlan.action === 'buy') return { kind: 'purchase', purchasePlan: weightPlan };
  return { kind: 'alreadyAtTarget', targetPercent: target };
}

function calculateResultOfAmount(row: AveragingRow, amount: number | CannotCalculate): RowResult {
  if (typeof amount !== 'number') return { kind: 'unusable', field: 'amount', reason: amount.reason };
  const purchasePlan = buildPurchasePlanForAmount(row.holdingInAccount, amount);
  if ('reason' in purchasePlan) return { kind: 'unusable', field: 'amount', reason: purchasePlan.reason };
  return { kind: 'purchase', purchasePlan };
}

/** What the target weight or the amount typed on the row comes to — only one of them is typed at a time. */
export function calculateRowResult(row: AveragingRow, typedTexts: TypedTexts): RowResult {
  const target = targetPercentOf(typedTexts.targetText);
  if (target !== null) return calculateResultOfTarget(row, target);
  const amount = amountOf(typedTexts.amountText);
  if (amount !== null) return calculateResultOfAmount(row, amount);
  return { kind: 'nothingTyped' };
}

/** Every purchase typed on one tab, added up against its cash. */
export interface PlanTotal {
  purchaseCount: number;
  /** The shares at IB's last prices, before the commissions. */
  amount: number;
  /** The default commission of each purchase, added up. */
  commissions: number;
  /** What the purchases need: their amount and their commissions. */
  total: number;
  /** The tab's cash less the total; below zero when the cash can't pay for the plan. */
  cashLeft: number;
}

/** Only the rows that buy count: a row whose field can't be used, or that buys nothing, adds nothing. */
export function calculatePlanTotal(rowResults: RowResult[], cash: number): PlanTotal {
  const purchasePlans = rowResults.flatMap((rowResult) =>
    rowResult.kind === 'purchase' ? [rowResult.purchasePlan] : [],
  );
  const amount = purchasePlans.reduce((runningTotal, purchasePlan) => runningTotal + purchasePlan.amount, 0);
  const commissions = purchasePlans.reduce((runningTotal, purchasePlan) => runningTotal + purchasePlan.commission, 0);
  const total = amount + commissions;
  return { purchaseCount: purchasePlans.length, amount, commissions, total, cashLeft: cash - total };
}
