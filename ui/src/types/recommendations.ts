/**
 * What `GET /api/recommendations` returns (RECOMMENDATIONS_TODO.md, session 1), and `POST /api/scans`. Mirrors the
 * Java records in `api/response/` — the record component names are the JSON keys, so keep the two in sync.
 */

import type {
  AnalysisRun,
  AnalystAction,
  AnalystTarget,
  CompanyInfo,
  Momentum,
  NewsEvent,
  RiskSign,
  StockAnalysis,
} from './portfolio';

/** Which of the three lists a stock is in, by where its last close stands against its 50-day average. */
export type HotStockLabel = 'HOT' | 'TOO_HOT' | 'CORRECTING';

/** One event of the analysis and what it weighs in the news score — the API's weight, never worked out here. */
export interface WeighedNewsEvent {
  event: NewsEvent;
  /** 0 for an event no kind fits. */
  points: number;
}

/** An analyst's upgrade or downgrade that counts in the news score: with a price target, from the last 30 days. */
export interface WeighedAnalystMove {
  action: AnalystAction;
  /** +2 or −2. */
  points: number;
}

/**
 * Why the recommendation can't pick a stock, in the order the checks run: the analysts' target first — known for every
 * stock before anything is paid for —, then Finnhub's figures.
 */
export type NotPickableReason = 'NO_ANALYST_TARGET' | 'TARGET_TOO_CLOSE' | 'NO_COMPANY_FACTS' | 'RISK_SIGNS_TOO_HEAVY';

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
  /**
   * What Finnhub answered with that analysis, the timing note counted from the latest scan; null while never analyzed,
   * or when Finnhub didn't answer with the analysis.
   */
  companyInfo: CompanyInfo | null;
  /** Its news events and the analysts' moves weighed, as the recommendation weighed them; null while never analyzed. */
  newsScore: number | null;
  /** Every event of the analysis with what it weighs in `newsScore`; empty while never analyzed. */
  weighedNewsEvents: WeighedNewsEvent[];
  /** The analysts' moves that count in `newsScore`; empty while never analyzed. */
  countedAnalystMoves: WeighedAnalystMove[];
  /** The risk signs' weights added up; null when they were never checked. */
  riskWeight: number | null;
  /** The news score less `riskWeight`: what the picks are ordered by; null when the signs were never checked. */
  score: number | null;
  /** No reason against it (`reasonsNotPickable` empty): the recommendation could pick it on its score. */
  canBePicked: boolean;
  /** Known before any analysis, from the headlines read with the scan. */
  analystTarget: AnalystTarget;
  /** Among the first 10 hot stocks with enough upside: the full run analyzes it. */
  inShortList: boolean;
  /** Empty when it can be picked. */
  reasonsNotPickable: NotPickableReason[];
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
  /** How far the analysts' median target is above the last close, in percent: 15 or more. */
  targetUpsidePercent: number;
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
