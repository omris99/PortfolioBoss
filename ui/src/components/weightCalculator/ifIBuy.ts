import { formatMoney } from '../../lib/format';
import { defaultCommissionFor } from '../../lib/orderCommission';
import type { Holding } from '../../types/portfolio';
import { investmentIn, type InvestmentsAndCash } from '../graphs/investmentsAndCash';
import { portfolioWeightsOf, wholeAccountOf } from '../graphs/portfolioWeights';
import type { CannotCalculate } from './targetWeight';

/**
 * "If I buy" (DESIGN_BRIEF.md §8.3), the arithmetic: what buying a scanned stock for an amount comes to on one tab —
 * whole shares at its last close, the default commission, and the stock's and its sector's weights after. As in the
 * Weight tab's purchase, cash turns into shares, so the whole account keeps its size, and the commission is too small to
 * change the weights, which leave it out.
 */

/** Below this, a share count counts as whole: 59.9999999999 shares is 60 after the division's rounding. */
const ROUNDING_TOLERANCE = 1e-9;

/** The sector the user picked for the stock: one of their own, or one none of their holdings has yet. */
export type SectorChoice = { kind: 'sector'; name: string } | { kind: 'notHeldYet' };

export interface SectorAfter {
  /** Of the money in stocks, 0–100. */
  percentBefore: number;
  percentAfter: number;
}

export interface Purchase {
  shareCount: number;
  /** The shares at the price, before the commission. */
  cost: number;
  commission: number;
  /** The shares of it the tab already holds — the investor's, on their tab. */
  heldQuantity: number;
  /** The stock after, with the shares already held, of the whole account — on an investor's tab, of all their money. */
  stockPercentAfter: number;
  /** `null` while no sector is picked. */
  sectorAfter: SectorAfter | null;
  cash: number;
  /** The cash pays for the shares and the commission. */
  isCoveredByCash: boolean;
}

/** The amount typed in, in dollars; or why it can't be used. Empty is neither: nothing is calculated yet. */
export function amountOf(typedText: string): number | CannotCalculate | null {
  const withoutCommas = typedText.replace(/,/g, '').trim();
  if (withoutCommas === '') return null;
  const amount = Number(withoutCommas);
  if (!Number.isFinite(amount) || amount <= 0) return { reason: 'Type an amount in USD, like 5000.' };
  return amount;
}

/** The sector's part of the money in stocks before and after `cost` more of it is bought. */
function sectorAfterOf(
  tab: InvestmentsAndCash,
  sectorChoice: SectorChoice,
  stocksValue: number,
  cost: number,
): SectorAfter {
  const sectorWeight =
    sectorChoice.kind === 'sector'
      ? portfolioWeightsOf(tab).bySector.find((weight) => weight.sector === sectorChoice.name)
      : undefined;
  const sectorValue = sectorWeight?.marketValue ?? 0;
  return {
    percentBefore: stocksValue > 0 ? (sectorValue / stocksValue) * 100 : 0,
    percentAfter: ((sectorValue + cost) / (stocksValue + cost)) * 100,
  };
}

/**
 * What buying `amount` of the stock at `price` comes to on `tab`, or why it can't be worked out. `heldAs`: the stock as
 * an open holding, when the account holds it already — its shares on the tab count in its weight after.
 */
export function purchaseOf(
  tab: InvestmentsAndCash,
  price: number,
  amount: number,
  heldAs: Holding | null,
  sectorChoice: SectorChoice | null,
): Purchase | CannotCalculate {
  const wholeAccount = wholeAccountOf(tab);
  if (wholeAccount === null) {
    const reason = `No weight can be worked out: ${tab.wholeName} is unknown (IB reported no cash) or not above zero.`;
    return { reason };
  }
  const shareCount = Math.floor(amount / price + ROUNDING_TOLERANCE);
  if (shareCount === 0) return { reason: `${formatMoney(amount)} buys no whole share at ${formatMoney(price)}.` };

  const held = heldAs === null ? null : investmentIn(tab, heldAs);
  const heldValue = held?.marketValue ?? 0;
  const cost = shareCount * price;
  const commission = defaultCommissionFor(shareCount);
  const stocksValue = wholeAccount.value - wholeAccount.cash;
  return {
    shareCount,
    cost,
    commission,
    heldQuantity: held?.quantity ?? 0,
    stockPercentAfter: ((heldValue + cost) / wholeAccount.value) * 100,
    sectorAfter: sectorChoice === null ? null : sectorAfterOf(tab, sectorChoice, stocksValue, cost),
    cash: wholeAccount.cash,
    isCoveredByCash: cost + commission <= wholeAccount.cash,
  };
}

/** The sectors the user has typed for their open holdings, A to Z: the list a scanned stock's sector is picked from. */
export function sectorsOfHoldings(openHoldings: Holding[]): string[] {
  const sectors = openHoldings.map((holding) => holding.sector).filter((sector) => sector !== null);
  return [...new Set(sectors)].sort((first, second) => first.localeCompare(second));
}

/** The user's sector named like one of IB's two words for the stock ("Technology", "Software"), ignoring case. */
export function matchingSectorOf(ibWords: (string | null)[], sectors: string[]): string | null {
  const ibWordsInLowerCase = ibWords.filter((word) => word !== null).map((word) => word.toLowerCase());
  return sectors.find((sector) => ibWordsInLowerCase.includes(sector.toLowerCase())) ?? null;
}
