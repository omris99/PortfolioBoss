/**
 * What `GET /api/recommendations` returns (RECOMMENDATIONS_TODO.md, session 1), and `POST /api/scans`. Mirrors the
 * Java records in `api/response/` — the record component names are the JSON keys, so keep the two in sync.
 */

import type { Momentum } from './portfolio';

/** Which of the three lists a stock is in, by where its last close stands against its 50-day average. */
export type HotStockLabel = 'HOT' | 'TOO_HOT' | 'CORRECTING';

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
}

/** What `POST /api/scans` answers once the new scan is stored. */
export interface ScanRun {
  scannedAt: string;
  candidateCount: number;
}
