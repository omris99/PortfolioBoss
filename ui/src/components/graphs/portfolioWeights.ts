import { ACCOUNT_CURRENCY } from '../../lib/format';
import type { Holding } from '../../types/portfolio';
import type { Investment, InvestmentsAndCash } from './investmentsAndCash';

/**
 * The calculations behind the Overview's graphs: how much each sector and each holding weighs — in the money in stocks
 * and in the whole account — and how the stocks and the cash split the account. No drawing here — the graphs only show
 * these results. They work on one tab's investments and cash (investmentsAndCash.ts): the whole account's on Portfolio,
 * one investor's on their tab — the same calculation for both.
 *
 * "The whole account" is the stocks weighed plus the cash, so the holdings and the cash add up to exactly 100% — on an
 * investor's tab, all of that investor's money. IB's net liquidation is a hair more (it also counts accrued interest and
 * dividends), and is not used here.
 */

/** The weight of a part of the money in stocks: one sector's, one holding's, or several small ones added together. */
export interface Weight {
  marketValue: number;
  /** Of the money in stocks, 0–100: the weights of one graph add up to 100. */
  percentOfStocks: number;
  /** Of the whole account, cash included; `null` while IB has reported no cash. */
  percentOfAccount: number | null;
}

/** The cash as a part of the whole account. Below zero when stocks were bought with borrowed money. */
export interface CashWeight {
  amount: number;
  percentOfAccount: number;
}

export interface SectorWeight extends Weight {
  sector: string;
  holdingCount: number;
}

/** The holdings no sector has been entered for yet, weighed together. */
export interface NoSectorWeight extends Weight {
  holdingCount: number;
}

export interface HoldingWeight extends Weight {
  holding: Holding;
}

/** An open holding the graphs can't weigh, and why, in words shown as they are: "in HKD", "no market value from IB". */
export interface LeftOutHolding {
  holding: Holding;
  reason: string;
}

export interface PortfolioWeights {
  /** The sectors entered, heaviest first. */
  bySector: SectorWeight[];
  /** `null` when every weighed holding has a sector. */
  noSector: NoSectorWeight | null;
  /** Heaviest first. */
  byHolding: HoldingWeight[];
  /** `null` while IB has reported no cash. */
  cash: CashWeight | null;
  leftOut: LeftOutHolding[];
}

/** An investment with a market value the graphs can weigh. */
interface WeighedHolding {
  holding: Holding;
  marketValue: number;
}

/**
 * The investment's holding with its market value, or why it is left out. A value in another currency can't be added to
 * dollars without IB's exchange rate, which the API doesn't send yet; a value of 0 or less (a short position) is no part
 * of the money in stocks.
 */
function weighedOrLeftOut({ holding, marketValue }: Investment): WeighedHolding | LeftOutHolding {
  if (marketValue === null) return { holding, reason: 'no market value from IB' };
  if (holding.currency !== ACCOUNT_CURRENCY) return { holding, reason: `in ${holding.currency}` };
  if (marketValue <= 0) return { holding, reason: 'a market value of 0 or less' };
  return { holding, marketValue };
}

function sumOfMarketValues(weighedHoldings: WeighedHolding[]): number {
  return weighedHoldings.reduce((runningTotal, weighedHolding) => runningTotal + weighedHolding.marketValue, 0);
}

/** Heaviest first; equal ones by name, so the order never changes between two loads. */
function heaviestFirst<SomeWeight extends Weight>(
  weights: SomeWeight[],
  nameOf: (weight: SomeWeight) => string,
): SomeWeight[] {
  return [...weights].sort(
    (first, second) => second.marketValue - first.marketValue || nameOf(first).localeCompare(nameOf(second)),
  );
}

/** The holdings of each sector entered, and those with none: a sector is typed in by hand, so it may be missing. */
function groupBySector(weighedHoldings: WeighedHolding[]): {
  holdingsOfSector: Map<string, WeighedHolding[]>;
  holdingsWithoutSector: WeighedHolding[];
} {
  const holdingsOfSector = new Map<string, WeighedHolding[]>();
  const holdingsWithoutSector: WeighedHolding[] = [];
  weighedHoldings.forEach((weighedHolding) => {
    const sector = weighedHolding.holding.sector;
    if (sector === null) {
      holdingsWithoutSector.push(weighedHolding);
      return;
    }
    const holdingsOfThisSector = holdingsOfSector.get(sector) ?? [];
    holdingsOfThisSector.push(weighedHolding);
    holdingsOfSector.set(sector, holdingsOfThisSector);
  });
  return { holdingsOfSector, holdingsWithoutSector };
}

/** The investments, split into those the graphs weigh and those they leave out. */
function weighedAndLeftOutOf(investments: Investment[]): {
  weighedHoldings: WeighedHolding[];
  leftOut: LeftOutHolding[];
} {
  const weighedHoldings: WeighedHolding[] = [];
  const leftOut: LeftOutHolding[] = [];
  investments.map(weighedOrLeftOut).forEach((result) => {
    if ('reason' in result) {
      leftOut.push(result);
    } else {
      weighedHoldings.push(result);
    }
  });
  return { weighedHoldings, leftOut };
}

/**
 * The whole account as the graphs and the weight calculator see it: the stocks weighed plus the cash — on an investor's
 * tab, all of their money.
 */
export interface WholeAccount {
  value: number;
  /** IB's cash, or the investor's; below zero when stocks were bought with borrowed money. */
  cash: number;
}

/** `null` when the cash is unknown, or the sum isn't above 0 (nothing to divide by). */
function wholeAccountFrom(stocksValue: number, cash: number | null): WholeAccount | null {
  if (cash === null) return null;
  const value = stocksValue + cash;
  return value > 0 ? { value, cash } : null;
}

/** The whole account the weight calculator divides by — the same the graphs divide by. */
export function wholeAccountOf(investmentsAndCash: InvestmentsAndCash): WholeAccount | null {
  const { weighedHoldings } = weighedAndLeftOutOf(investmentsAndCash.investments);
  return wholeAccountFrom(sumOfMarketValues(weighedHoldings), investmentsAndCash.cash);
}

/** Every weight the graphs show, on one tab. */
export function portfolioWeightsOf(investmentsAndCash: InvestmentsAndCash): PortfolioWeights {
  const { weighedHoldings, leftOut } = weighedAndLeftOutOf(investmentsAndCash.investments);
  const stocksValue = sumOfMarketValues(weighedHoldings);
  const wholeAccount = wholeAccountFrom(stocksValue, investmentsAndCash.cash);
  const weightOf = (marketValue: number): Weight => ({
    marketValue,
    percentOfStocks: (marketValue / stocksValue) * 100,
    percentOfAccount: wholeAccount === null ? null : (marketValue / wholeAccount.value) * 100,
  });

  const { holdingsOfSector, holdingsWithoutSector } = groupBySector(weighedHoldings);
  const sectorWeights = [...holdingsOfSector].map(([sector, holdingsOfThisSector]) => ({
    ...weightOf(sumOfMarketValues(holdingsOfThisSector)),
    sector,
    holdingCount: holdingsOfThisSector.length,
  }));
  const noSectorWeight =
    holdingsWithoutSector.length === 0
      ? null
      : { ...weightOf(sumOfMarketValues(holdingsWithoutSector)), holdingCount: holdingsWithoutSector.length };
  const holdingWeights = weighedHoldings.map(({ holding, marketValue }) => ({ ...weightOf(marketValue), holding }));

  return {
    bySector: heaviestFirst(sectorWeights, (sectorWeight) => sectorWeight.sector),
    noSector: noSectorWeight,
    byHolding: heaviestFirst(holdingWeights, (holdingWeight) => holdingWeight.holding.symbol),
    cash:
      wholeAccount === null
        ? null
        : { amount: wholeAccount.cash, percentOfAccount: (wholeAccount.cash / wholeAccount.value) * 100 },
    leftOut,
  };
}

/** A graph shows only so many weights: the heaviest of them, and the others added together into one. */
export interface HeaviestWeights<SomeWeight extends Weight> {
  heaviest: SomeWeight[];
  /** The others and their total; `null` when every weight is among the heaviest. */
  rest: { weights: SomeWeight[]; total: Weight } | null;
}

function addWeights(first: Weight, second: Weight): Weight {
  return {
    marketValue: first.marketValue + second.marketValue,
    percentOfStocks: first.percentOfStocks + second.percentOfStocks,
    percentOfAccount:
      first.percentOfAccount === null || second.percentOfAccount === null
        ? null
        : first.percentOfAccount + second.percentOfAccount,
  };
}

const NO_WEIGHT: Weight = { marketValue: 0, percentOfStocks: 0, percentOfAccount: 0 };

/** `weightsHeaviestFirst` split after the first `shownCount`. */
export function heaviestWeightsOf<SomeWeight extends Weight>(
  weightsHeaviestFirst: SomeWeight[],
  shownCount: number,
): HeaviestWeights<SomeWeight> {
  const restWeights = weightsHeaviestFirst.slice(shownCount);
  if (restWeights.length === 0) return { heaviest: weightsHeaviestFirst, rest: null };
  return {
    heaviest: weightsHeaviestFirst.slice(0, shownCount),
    rest: { weights: restWeights, total: restWeights.reduce<Weight>(addWeights, NO_WEIGHT) },
  };
}

/** The positions value and the cash as parts of their sum, 0–100 each. */
export interface StocksAndCash {
  stocksPercent: number;
  cashPercent: number;
}

/**
 * `null` when the two can't be parts of one whole: the cash is unknown or below zero (stocks bought with borrowed
 * money), or there is nothing at all.
 */
export function stocksAndCashOf(positionsValue: number, cash: number | null): StocksAndCash | null {
  if (cash === null || cash < 0 || positionsValue < 0) return null;
  const total = positionsValue + cash;
  if (total === 0) return null;
  return { stocksPercent: (positionsValue / total) * 100, cashPercent: (cash / total) * 100 };
}
