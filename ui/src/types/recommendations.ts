/**
 * What `GET /api/recommendations` returns (RECOMMENDATIONS_TODO.md, session 1), and `POST /api/scans`. Mirrors the
 * Java records in `api/response/` — the record component names are the JSON keys, so keep the two in sync.
 */

import type { AnalysisRun, Momentum, StockAnalysis } from './portfolio';

/** Which of the three lists a stock is in, by where its last close stands against its 50-day average. */
export type HotStockLabel = 'HOT' | 'TOO_HOT' | 'CORRECTING';

/** The five risk signs of a stock the recommendation may pick, in the order they are listed. */
export type RiskSign = 'LOSES_MONEY' | 'HIGH_DEBT' | 'EXPENSIVE' | 'REVENUE_FALLING' | 'RECOVERING';

/** A company's next earnings report, as Finnhub's calendar gives it. */
export interface EarningsDate {
  /** 'yyyy-MM-dd' */
  date: string;
  /** 'bmo' before the market opens, 'amc' after it closes, 'dmh' during the trading hours; null while not known. */
  hour: string | null;
}

/** Four of a company's figures from Finnhub; each null when Finnhub doesn't give it. */
export interface CompanyFacts {
  /** The last 12 months' revenue against the 12 before, in percent. */
  revenueGrowthPercent: number | null;
  /** The last 12 months' net profit out of the revenue, in percent; negative for a loss. */
  netMarginPercent: number | null;
  /** The debt divided by the equity — a ratio: 2 is twice the equity. */
  debtToEquity: number | null;
  peRatio: number | null;
}

/** How closely a scanned stock moves with one open holding: 1 exactly together, 0 unrelated, −1 opposite. */
export interface Correlation {
  holdingId: number;
  symbol: string;
  coefficient: number;
  /** 0.7 or more. */
  movesTogether: boolean;
}

/** One stock of a list; its price is `momentum.lastClose`. */
export interface HotStock {
  /** The `scan_candidate` row: the drawer's address. */
  candidateId: number;
  conId: number;
  symbol: string;
  /** IB's, in capitals: "KERVAN SYSTEMS INC"; null when IB sent none. */
  companyName: string | null;
  /** `NYSE` or `NASDAQ`. */
  exchange: string;
  /** IB's sector, "Technology"; null when IB sent none. */
  industry: string | null;
  /** Within it, "Software". */
  category: string | null;
  label: HotStockLabel;
  momentum: Momentum;
  /** Below 0 in CORRECTING. */
  percentAboveSma50: number;
  /** HOT only: 5–15% below the year's highest close. */
  pulledBack: boolean;
  /** Lower than a year ago (or than its first close); null when unknown. */
  recovering: boolean | null;
  /** The open holdings it moves with most, highest first — at most three. */
  correlations: Correlation[];
  /** The investors holding some of it, when it is an open holding already. */
  heldByInvestorIds: number[];
  /** Its latest analysis, from any scan; the consensus measured against the last close. null while never analyzed. */
  analysis: StockAnalysis | null;
  /** From that analysis's Finnhub answer; null without one, or without a report in the next four months. */
  nextEarningsDate: EarningsDate | null;
  /** From that analysis's Finnhub answer; null without one. */
  companyFacts: CompanyFacts | null;
  /** null without Finnhub's figures — then the signs were never checked. */
  riskSigns: RiskSign[] | null;
  /** Its report is due 0 to 14 days after the latest scan: wait for it. */
  reportsWithin14Days: boolean;
  /** Its news events and the analysts' moves weighed, as the recommendation weighed them; null while never analyzed. */
  newsScore: number | null;
}

/** One stock the code picked, in the recommendation's order. */
export interface RecommendationPick {
  candidateId: number;
  symbol: string;
  /** Their weights added up are 2 at most. */
  riskSigns: RiskSign[];
  reportsWithin14Days: boolean;
  /** The week's news events and the analysts' moves weighed; above 0 the good outweighs the bad. */
  newsScore: number;
  /** The news score less the risks' weight: what the picks are ordered by. */
  score: number;
}

/** What `POST /api/recommendations/analysis` answers: the run, as for the holdings, and the picks. */
export interface RecommendationAnalysisRun {
  analysis: AnalysisRun;
  /** Empty when no stock qualified; null for one stock's analysis, which makes no recommendation. */
  picks: RecommendationPick[] | null;
}

/** The latest scan's stocks in the three lists, each in the page's order. */
export interface Recommendations {
  /** ISO instant. */
  scannedAt: string;
  /** Every stock the scan kept — also those in no list. */
  candidateCount: number;
  hotStocks: HotStock[];
  tooHotStocks: HotStock[];
  correctingStocks: HotStock[];
  /** The code's latest picks, from whichever scan; null before the first full run. */
  latestRecommendation: LatestRecommendation | null;
}

/** The latest recommendation, shown even after a newer scan, with the date of its own. */
export interface LatestRecommendation {
  /** ISO instant. */
  recommendedAt: string;
  /** ISO instant: the scan its short list came from — older than the page's when a scan came since. */
  scannedAt: string;
  /** In the recommendation's order; empty when no stock qualified. */
  picks: PickedStock[];
}

/** One stock of the latest recommendation; found in today's lists by `conId` when it is of an earlier scan. */
export interface PickedStock {
  candidateId: number;
  conId: number;
  symbol: string;
}

/** What `POST /api/scans` answers once the new scan is stored. */
export interface ScanRun {
  scannedAt: string;
  candidateCount: number;
}
