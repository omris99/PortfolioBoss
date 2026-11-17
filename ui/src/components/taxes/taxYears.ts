import type { MonthlySettlement, SettlementDay, Taxes, TaxYear } from '../../types/taxes';
import type { InvestorTab } from '../InvestorTabs';

/**
 * What the Taxes page shows of a year, picked out of `GET /api/taxes`: the tabs, the years, the months with their days,
 * and the year card's sums. The tax itself is the server's: here it is only grouped and added up for the screen.
 */

/**
 * Portfolio, then every investor besides the account owner. The owner's own tab waits for the session that splits IB's
 * trades between the investors (CAPITAL_GAINS_TAX_TODO.md).
 */
export function tabsOf(taxes: Taxes): InvestorTab[] {
  const portfolioTab: InvestorTab = { investorId: null, name: 'Portfolio' };
  const investorTabs = taxes.otherInvestorsTax.map((investorTax) => ({
    investorId: investorTax.investorId,
    name: investorTax.investorName,
  }));
  return [portfolioTab, ...investorTabs];
}

/** The account's years on Portfolio (`null`), an investor's own on their tab; none for an investor no longer listed. */
export function taxYearsOfTab(taxes: Taxes, investorId: number | null): TaxYear[] {
  if (investorId === null) return taxes.accountTaxYears;
  const investorTax = taxes.otherInvestorsTax.find((otherInvestor) => otherInvestor.investorId === investorId);
  return investorTax?.taxYears ?? [];
}

/** The year of the last sync: the page opens on it. */
export function yearOfTheLastSync(taxes: Taxes): number {
  return Number(taxes.lastSyncDay.slice(0, 4));
}

/**
 * Every year with a sale on any tab, and the last sync's, the newest first: the select keeps the same years whatever
 * the tab, and a tab without that year says so ("No sales in 2024.").
 */
export function yearsToChooseFrom(taxes: Taxes): number[] {
  const everyTabsYears = [
    ...taxes.accountTaxYears,
    ...taxes.otherInvestorsTax.flatMap((investorTax) => investorTax.taxYears),
  ].map((taxYear) => taxYear.year);
  const years = new Set([...everyTabsYears, yearOfTheLastSync(taxes)]);
  return [...years].sort((firstYear, secondYear) => secondYear - firstYear);
}

/** A month of the year with its own settlement days, the newest day first. */
export interface MonthWithItsSettlementDays {
  monthlySettlement: MonthlySettlement;
  settlementDays: SettlementDay[];
}

/** The year's months the newest first, each with its days; only the months that have a settlement day. */
export function monthsNewestFirst(taxYear: TaxYear): MonthWithItsSettlementDays[] {
  return [...taxYear.monthlySettlements].reverse().map((monthlySettlement) => ({
    monthlySettlement,
    settlementDays: taxYear.settlementDays
      .filter((settlementDay) => settlementDay.settlementDay.startsWith(monthlySettlement.month))
      .reverse(),
  }));
}

/** What the broker took in the months that have ended. */
export function taxPaidInSettledMonths(taxYear: TaxYear): number {
  return taxYear.monthlySettlements
    .filter((monthlySettlement) => monthlySettlement.settled && monthlySettlement.settlementAmount > 0)
    .reduce((paidSoFar, monthlySettlement) => paidSoFar + monthlySettlement.settlementAmount, 0);
}

/** What the broker gave back in the months that have ended, as a positive amount. */
export function taxRefundedInSettledMonths(taxYear: TaxYear): number {
  return taxYear.monthlySettlements
    .filter((monthlySettlement) => monthlySettlement.settled && monthlySettlement.settlementAmount < 0)
    .reduce((refundedSoFar, monthlySettlement) => refundedSoFar - monthlySettlement.settlementAmount, 0);
}

/** The month that hasn't ended by the last sync, when it has a settlement day; `null` otherwise. */
export function monthNotSettledYet(taxYear: TaxYear): MonthlySettlement | null {
  return taxYear.monthlySettlements.find((monthlySettlement) => !monthlySettlement.settled) ?? null;
}

/**
 * How far the checking off has come: the days confirmed, of all the days that need the broker's amount — those
 * calculated (to confirm as they are) and those that can't be (to type in). A day of 0, a day still waiting for a rate
 * and a sale beyond its buys are not counted.
 */
export interface CheckingOffCounts {
  confirmedDays: number;
  daysNeedingAConfirmation: number;
  daysToTypeIn: number;
}

export function checkingOffCountsOf(settlementDays: SettlementDay[]): CheckingOffCounts {
  const countOf = (status: SettlementDay['status']) =>
    settlementDays.filter((settlementDay) => settlementDay.status === status).length;
  const confirmedDays = countOf('CONFIRMED');
  const daysToTypeIn = countOf('CANNOT_BE_CALCULATED');
  return {
    confirmedDays,
    daysNeedingAConfirmation: confirmedDays + countOf('CALCULATED') + daysToTypeIn,
    daysToTypeIn,
  };
}

/** The days Confirm the whole month confirms at the amounts PortfolioBoss calculated: those calculated, not 0. */
export function settlementDaysToConfirmAsCalculated(month: MonthWithItsSettlementDays): SettlementDay[] {
  return month.settlementDays.filter((settlementDay) => settlementDay.status === 'CALCULATED');
}

const MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

/** From the last sync's day to December 31 of its year: 0 on December 31. */
export function daysLeftUntilTheYearEnds(lastSyncDay: string): number {
  const [year, month, day] = lastSyncDay.split('-').map(Number);
  // Days counted in UTC, so that a clock change between the two dates can't make one of them an hour short.
  return Math.round((Date.UTC(year, 11, 31) - Date.UTC(year, month - 1, day)) / MILLISECONDS_PER_DAY);
}
