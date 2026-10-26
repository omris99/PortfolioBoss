import { EMPTY_VALUE, formatCorrelation, formatSignedPercentOneDecimal } from '../../lib/format';
import type { Investor, Momentum } from '../../types/portfolio';
import type { Correlation, HotStock } from '../../types/recommendations';
import { hasSeveralInvestors, investorNameOf } from '../InvestorsContext';

/** The texts the lists and the drawer share, so a stock reads the same in both. */

/** IB returns a year of closes: a stock that has traded less shows its months next to its return. */
const MONTHS_IN_A_FULL_YEAR = 12;

export const NO_CORRELATION_REASON =
  'No figure: the account holds no stocks, or none with 100 trading days in common.';

/** IB's two words, "Technology / Software"; one when IB sent only one; "—" when it sent none. */
export function sectorText(stock: HotStock): string {
  const parts = [stock.industry, stock.category].filter((part) => part !== null);
  return parts.length === 0 ? EMPTY_VALUE : parts.join(' / ');
}

export function isYoungStock(momentum: Momentum): boolean {
  return momentum.monthsInMarket < MONTHS_IN_A_FULL_YEAR;
}

/** "10 months", "12 months". */
export function monthsInMarketText(momentum: Momentum): string {
  return momentum.monthsInMarket === 1 ? '1 month' : `${momentum.monthsInMarket} months`;
}

/** "+62.3%" for a full year; "+27.9% · 10 months" for a stock that has traded less. */
export function yearReturnText(momentum: Momentum): string {
  const yearReturn = formatSignedPercentOneDecimal(momentum.yearReturnPercent);
  return isYoungStock(momentum) ? `${yearReturn} · ${monthsInMarketText(momentum)}` : yearReturn;
}

/** "−7.4%" below the year's highest close; "0.0%" at it. */
export function fromHighText(momentum: Momentum): string {
  return formatSignedPercentOneDecimal(-momentum.percentBelowHigh52Weeks);
}

/** "Me · Dana" when held already, "Held" with one investor, "" when not held. */
export function heldByText(stock: HotStock, investors: Investor[]): string {
  if (stock.heldByInvestorIds.length === 0) return '';
  if (!hasSeveralInvestors(investors)) return 'Held';
  return stock.heldByInvestorIds.map((investorId) => investorNameOf(investors, investorId)).join(' · ');
}

/** "MSFT 0.81". */
export function correlationText(correlation: Correlation): string {
  return `${correlation.symbol} ${formatCorrelation(correlation.coefficient)}`;
}

/** The hover of the Moves with cell: "Moves most with MSFT 0.81 · AMZN 0.55 · V 0.31". */
export function correlationsHint(correlations: Correlation[]): string {
  return `Moves most with ${correlations.map(correlationText).join(' · ')}`;
}
