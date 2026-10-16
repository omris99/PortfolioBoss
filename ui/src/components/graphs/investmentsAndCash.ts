import type { Holding, Investor } from '../../types/portfolio';
import { hasSeveralInvestors } from '../InvestorsContext';

/**
 * What each tab of the weights weighs — Portfolio, then one tab per investor: on Portfolio every open holding and IB's
 * cash; on an investor's tab their shares of each holding and their own cash, so their weights are of their own money.
 * Only figures the API already sends: each holding's `investorQuantities` and each investor's `cash`, worked out by the
 * same rule as the investors' cards — so every investor's investments and cash add up to the Portfolio's.
 *
 * Example: AAPL is worth 20,000, 14,000 of it Omri's and 6,000 Shay's; Shay's cash is 2,000. On Shay's tab AAPL is
 * 6,000 of Shay's 8,000 — 75%.
 */

/** One holding as a tab weighs it: all of it on Portfolio, one investor's shares of it on that investor's tab. */
export interface Investment {
  holding: Holding;
  quantity: number;
  /** At IB's last price, in the holding's currency; `null` while IB reported none. */
  marketValue: number | null;
  /** A share, commissions included — IB's on Portfolio, the investor's own on their tab; `null` when unknown. */
  averageCost: number | null;
}

/** One tab: its investments, its cash, and how its texts name them. */
export interface InvestmentsAndCash {
  /** "Portfolio", or the investor's name. */
  name: string;
  /** `null` on Portfolio. */
  investorId: number | null;
  /** Only the holdings of this tab: on an investor's tab, those they hold some of. */
  investments: Investment[];
  /** IB's cash on Portfolio, the investor's on their tab; `null` while IB reported none. */
  cash: number | null;
  /** "the whole account", "all of Shay's money". */
  wholeName: string;
  /** What it is made of: "the stocks plus the cash", "Shay's shares plus Shay's cash". */
  wholeParts: string;
  /** "the money in stocks", "Shay's money in stocks". */
  stocksName: string;
  /** What the cash is, on hover of its bar. */
  cashHint: string;
  /** What a sale's realized P&L is worked out at: "IB's average cost", "the average cost of Shay's shares". */
  averageCostName: string;
}

export const PORTFOLIO_TAB_NAME = 'Portfolio';

function portfolioInvestmentOf(holding: Holding): Investment {
  return { holding, quantity: holding.position, marketValue: holding.marketValue, averageCost: holding.averageCost };
}

/** The whole account: every open holding, and IB's cash. */
function portfolioInvestmentsAndCashOf(openHoldings: Holding[], ibCash: number | null): InvestmentsAndCash {
  return {
    name: PORTFOLIO_TAB_NAME,
    investorId: null,
    investments: openHoldings.map(portfolioInvestmentOf),
    cash: ibCash,
    wholeName: 'the whole account',
    wholeParts: 'the stocks plus the cash',
    stocksName: 'the money in stocks',
    cashHint: 'The cash in the account, as IB reported it',
    averageCostName: 'IB’s average cost',
  };
}

/** The investor's shares of `holding`, or `null` while they hold none of it. */
function investorInvestmentOf(holding: Holding, investorId: number): Investment | null {
  const part = holding.investorQuantities.find((investorQuantity) => investorQuantity.investorId === investorId);
  if (part === undefined || part.quantity === 0) return null;
  const averageCost = part.sharesCost === null ? null : part.sharesCost / part.quantity;
  return { holding, quantity: part.quantity, marketValue: part.sharesValue, averageCost };
}

function cashHintOf(investor: Investor): string {
  return investor.accountOwner
    ? `${investor.name}’s cash: IB’s cash less the other investors’`
    : `${investor.name}’s cash: deposits less withdrawals, less buys and plus sells, commissions off`;
}

/** One investor's money: their shares of every open holding they hold some of, and their cash. */
function investorInvestmentsAndCashOf(openHoldings: Holding[], investor: Investor): InvestmentsAndCash {
  const investments = openHoldings
    .map((holding) => investorInvestmentOf(holding, investor.id))
    .filter((investment): investment is Investment => investment !== null);
  const { name } = investor;
  return {
    name,
    investorId: investor.id,
    investments,
    cash: investor.cash,
    wholeName: `all of ${name}’s money`,
    wholeParts: `${name}’s shares plus ${name}’s cash`,
    stocksName: `${name}’s money in stocks`,
    cashHint: cashHintOf(investor),
    averageCostName: `the average cost of ${name}’s shares`,
  };
}

/**
 * The weights' tabs: Portfolio, then every investor in the list's order, the account owner first. Portfolio alone while
 * the account owner is the only investor: their tab would be the same.
 */
export function weightsTabsOf(openHoldings: Holding[], ibCash: number | null, investors: Investor[]): InvestmentsAndCash[] {
  const portfolioTab = portfolioInvestmentsAndCashOf(openHoldings, ibCash);
  if (!hasSeveralInvestors(investors)) return [portfolioTab];
  return [portfolioTab, ...investors.map((investor) => investorInvestmentsAndCashOf(openHoldings, investor))];
}

/** The tab of `investorId` (`null`: Portfolio), or Portfolio — the first tab — when there is no such tab. */
export function weightsTabOf(tabs: InvestmentsAndCash[], investorId: number | null): InvestmentsAndCash {
  return tabs.find((tab) => tab.investorId === investorId) ?? tabs[0];
}

/** `holding` as `tab` weighs it — none of it, on the tab of an investor who holds none, so that buying some can be worked out. */
export function investmentIn(tab: InvestmentsAndCash, holding: Holding): Investment {
  const investment = tab.investments.find((tabInvestment) => tabInvestment.holding.id === holding.id);
  return investment ?? { holding, quantity: 0, marketValue: 0, averageCost: null };
}
