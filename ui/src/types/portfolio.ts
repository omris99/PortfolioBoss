/**
 * Mirrors the JSON served by `GET /api/portfolio` (the `*Response` records in
 * src/main/java/portfolioboss/api/response/) and sent to the write endpoints (the `*Request` records in
 * api/request/). A figure IB did not report arrives as `null`.
 */

export type HoldingStatus = 'OPEN' | 'CLOSED';
export type TradeSide = 'BUY' | 'SELL';
export type HoldingWarningType = 'NO_TRADES_LOGGED' | 'CLOSED_WITHOUT_SELL' | 'QUANTITY_MISMATCH';
/** `TRADES`: a holding's trades, corrected in Positions. `MANUAL`: a manual position's, corrected where it is shown. */
export type ClosedPositionSource = 'TRADES' | 'MANUAL';
export type CashMovementType = 'DEPOSIT' | 'WITHDRAWAL';
export type InvestorWarningType =
  | 'TRADES_WITHOUT_PRICE'
  | 'TRADES_NOT_IN_USD'
  | 'NEGATIVE_CASH'
  | 'MORE_SHARES_THAN_IB'
  | 'CLOSED_POSITIONS_NOT_COUNTED';

/** A gap between the trades entered and what IB reports. It never blocks anything. */
export interface HoldingWarning {
  type: HoldingWarningType;
  /** In English, shown as it is. */
  message: string;
}

/** One buy or sell entered by hand. */
export interface Trade {
  id: number;
  /** 'yyyy-MM-dd' */
  tradeDate: string;
  side: TradeSide;
  quantity: number;
  price: number | null;
  note: string | null;
  /** Never `null`: a trade entered without one was stored with the default (1 cent a share, at least $5). */
  commission: number;
  /** Whose trade it is: one of `PortfolioSnapshot.investors`. */
  investorId: number;
}

/** The body of `POST /api/holdings/{id}/trades` and `PUT /api/trades/{id}`: a trade as the user typed it. */
export interface TradeRequest {
  /** 'yyyy-MM-dd', not in the future. */
  tradeDate: string;
  side: TradeSide;
  /** Greater than 0. */
  quantity: number;
  price: number | null;
  note: string | null;
  /** `null` stores the default for this many shares; 0 is a commission too. */
  commission: number | null;
  /** A new trade without one is the account owner's; a correction without one keeps the trade's investor. */
  investorId: number | null;
}

/**
 * The body of `PUT /api/manual-positions/{id}`: a manual position's own details — a position PortfolioBoss never saw
 * as a holding, sold before the first sync. Its buys and sells are changed like any trade.
 */
export interface ManualPositionRequest {
  symbol: string;
  currency: string;
  sector: string | null;
  note: string | null;
}

/** The body of `POST /api/manual-positions`: a new manual position with its first buy and its first sell. */
export interface NewManualPositionRequest extends ManualPositionRequest {
  /** Greater than 0; bought and then sold. */
  quantity: number;
  /** 'yyyy-MM-dd', not in the future. */
  buyDate: string;
  buyPrice: number;
  /** `null` stores the default for that order. */
  buyCommission: number | null;
  /** 'yyyy-MM-dd', not in the future and not before `buyDate`. */
  sellDate: string;
  sellPrice: number;
  sellCommission: number | null;
  /** Whose buy and sell they are; `null` is the account owner. */
  investorId: number | null;
}

/**
 * An investor's part of a holding: the other investors' trades, and the account owner the rest of IB's figures — so the
 * parts always add up to the holding. In the holding's currency; a figure that needs a price nobody entered is `null`.
 */
export interface InvestorQuantity {
  investorId: number;
  quantity: number;
  /** At IB's last price. */
  sharesValue: number | null;
  /** At average cost, with the buy commissions; for the account owner IB's cost less the others'. */
  sharesCost: number | null;
  unrealizedPnl: number | null;
  /** Of `sharesCost`. */
  unrealizedPnlPercent: number | null;
}

export interface Holding {
  symbol: string;
  secType: string;
  currency: string;
  position: number;
  averageCost: number | null;
  marketPrice: number | null;
  marketValue: number | null;
  unrealizedPnl: number | null;
  realizedPnl: number | null;
  account: string;
  costBasis: number | null;
  unrealizedPnlPercent: number | null;
  /** The database row's id: what the write endpoints take, and unique even when two holdings share a symbol. */
  id: number;
  /** IB's contract id. */
  conId: number;
  /** Entered by hand; `null` until then. */
  sector: string | null;
  /** A holding TWS no longer reports is `CLOSED`, never deleted, so its sector and trades survive. */
  status: HoldingStatus;
  /** 'yyyy-MM-dd', derived from `trades` by the API: the first buy since the position was last empty. */
  firstBuyDate: string | null;
  /** 'yyyy-MM-dd': the last sell in that same stretch of ownership. */
  lastSellDate: string | null;
  /** Days from `firstBuyDate` to the last sync (still open) or to `lastSellDate` (closed). */
  holdingDays: number | null;
  /** Ordered by trade date, then id. */
  trades: Trade[];
  /** Empty when the trades entered match IB; otherwise the one gap to fix first. */
  warnings: HoldingWarning[];
  /** How `position`, its value and its cost divide between the investors — only those holding some of it. */
  investorQuantities: InvestorQuantity[];
  /** From the daily closes the last `run.sh` read from IB; `null` while none are stored for this holding. */
  momentum: Momentum | null;
  /** The latest analysis of its analyst ratings and news; `null` while it has never been analyzed. */
  analysis: StockAnalysis | null;
  /** The colored dot; `null` without an analysis, or while fewer than two of its three signs are known. */
  signal: HoldingSignal | null;
}

/** What a momentum score means: `STRONG` 4–5, `NEUTRAL` 2–3, `WEAK` 0–1 (AI_ANALYSIS_TODO.md, decision 5). */
export type MomentumLabel = 'STRONG' | 'NEUTRAL' | 'WEAK';

/**
 * A holding's momentum on its last daily close: five checks, a point for each one that holds. Averages are simple ones
 * of closes. A figure there are not enough closes for is `null`, and so is every check that needs it; the score and the
 * label exist only when all five checks do.
 */
export interface Momentum {
  /** 'yyyy-MM-dd': the date of the last close. */
  asOf: string;
  lastClose: number;
  sma20: number | null;
  sma50: number | null;
  sma200: number | null;
  /** The highest of the last 20 closes. */
  high20: number | null;
  /** The last close against the last one on or before the same day a month earlier. */
  oneMonthReturnPercent: number | null;
  /** SPY over the same month. */
  spyOneMonthReturnPercent: number | null;
  aboveSma20: boolean | null;
  aboveSma50: boolean | null;
  sma50AboveSma200: boolean | null;
  /** At most 10% below `high20`. */
  nearHigh: boolean | null;
  /** This month's return is higher than SPY's. */
  beatsSpy: boolean | null;
  /** How far below `high20` the last close is, in percent; 0 at the high. */
  percentBelowHigh: number | null;
  /** 0–5. */
  score: number | null;
  label: MomentumLabel | null;
}

/**
 * The colored dot next to a holding, worked out by the API from three warning signs — weak momentum, analysts
 * deteriorating, negative news: `RED` for two or more, `GREEN` for none, `YELLOW` for one (AI_ANALYSIS_TODO.md, decision 6).
 */
export type HoldingSignal = 'GREEN' | 'YELLOW' | 'RED';
/** One scale for every source: a source's own words ("Moderate Buy", "Outperform") are mapped onto it. */
export type AnalystRating = 'STRONG_BUY' | 'BUY' | 'HOLD' | 'SELL' | 'STRONG_SELL';
/**
 * Which way the analysts have moved, worked out by the API from the price targets in `recentActions` (decision 18): more
 * raised than lowered is `IMPROVING`, more lowered `DETERIORATING`, as many — or none moved — `STABLE`. A rating
 * without a target counts for nothing.
 */
export type AnalystTrend = 'IMPROVING' | 'STABLE' | 'DETERIORATING';
export type AnalystActionType = 'UPGRADE' | 'DOWNGRADE' | 'INITIATE' | 'REITERATE' | 'TARGET_RAISED' | 'TARGET_LOWERED';
/** How the week's news reads for someone holding the stock. */
export type Sentiment = 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE';

/** How many analysts give each rating, as one source shows them. */
export interface RatingCounts {
  strongBuy: number;
  buy: number;
  hold: number;
  sell: number;
  strongSell: number;
}

/** The analysts' consensus as one source shows it, extracted by Claude without merging it with any other. */
export interface SourceConsensus {
  sourceUrl: string;
  /** 'yyyy-MM-dd' */
  publishedDate: string | null;
  analystCount: number | null;
  /** `null` when the source shows no breakdown. */
  ratingCounts: RatingCounts | null;
  averageTarget: number | null;
  /** The source's own consensus, on the one scale. */
  ratingLabel: AnalystRating | null;
}

/**
 * The consensus the API chose among the sources (MarketBeat first, AI_ANALYSIS_TODO.md decision 16), against IB's
 * latest price, and how far every source's target spreads.
 */
export interface AnalystConsensus {
  /** Worked out from the chosen source's breakdown, else its own label; `null` when it shows neither. */
  rating: AnalystRating | null;
  analystCount: number | null;
  averageTarget: number | null;
  /** How far `averageTarget` is above IB's market price, in percent; negative below it. */
  targetUpsidePercent: number | null;
  /** The chosen source. */
  sourceUrl: string;
  /** The lowest average target among the sources (`targetHigh` the highest); `null` when none gives one. */
  targetLow: number | null;
  targetHigh: number | null;
  /** How many sources give an average target. */
  sourceCount: number;
}

/** One recent move by an analyst firm. The ratings are the firm's own words, not the one scale. */
export interface AnalystAction {
  /** 'yyyy-MM-dd' */
  date: string;
  firm: string;
  action: AnalystActionType;
  fromRating: string | null;
  toRating: string | null;
  previousPriceTarget: number | null;
  priceTarget: number | null;
  /** The search result it comes from. */
  url: string;
  /**
   * 'yyyy-MM-dd': the date the search engine gives that result — how old its copy of the page may be. `null` when it gives
   * none, and in analyses stored before 2026-10-10.
   */
  sourcePublishedDate: string | null;
}

/** A news headline, word for word as published. */
export interface Headline {
  /** 'yyyy-MM-dd' */
  date: string | null;
  title: string;
  /** Who published it ("Reuters"). */
  source: string;
  url: string;
}

/**
 * A holding's latest analysis: what Claude found in two web searches, as it is, and the consensus the API chose from it.
 * Claude reports only — what the results don't say is `null` or an empty list, never a guess.
 */
export interface StockAnalysis {
  /** ISO-8601 instant. */
  analyzedAt: string;
  /** The model that actually answered. */
  model: string;
  /** `null` when no source showed a consensus. */
  consensus: AnalystConsensus | null;
  consensusBySource: SourceConsensus[];
  /** `null` while no action states a price target. */
  analystTrend: AnalystTrend | null;
  /** Up to 10 from the last 90 days, the newest first. */
  recentActions: AnalystAction[];
  /** Up to 3, the most important of the last 14 days. */
  headlines: Headline[];
  sentiment: Sentiment | null;
  /** One short sentence in English. */
  sentimentReason: string | null;
  /** The price targets raised among `recentActions` (the target before and after stated), which the trend weighs. */
  raisedTargetCount: number;
  /** The price targets lowered. */
  loweredTargetCount: number;
}

/** A stock an analysis run could not analyze, and why; it keeps its previous analysis. */
export interface FailedAnalysis {
  symbol: string;
  message: string;
}

/** The answer to `POST /api/analysis`: what one run did and cost. The analyses come with the next reload. */
export interface AnalysisRun {
  /** How many stocks were analyzed and stored. */
  analyzed: number;
  failed: FailedAnalysis[];
  /** Tavily's credits (the free plan has 1,000 a month). */
  tavilyCredits: number;
  inputTokens: number;
  outputTokens: number;
  /** What Claude's tokens cost, in dollars. */
  costUsd: number;
}

/**
 * The shares sold in one stretch of owning a stock, at average cost: a stretch a sell brought back to zero, or one still
 * held that has had a sell already (`remainingQuantity` above 0). Derived by the API from the trades of a holding or of
 * a manual position (`source`). A figure that needs a price nobody entered is `null`.
 */
export interface ClosedPosition {
  /** The holding whose trades it was derived from; `null` for a manual position. */
  holdingId: number | null;
  symbol: string;
  currency: string;
  sector: string | null;
  /** 'yyyy-MM-dd' */
  openDate: string;
  /** 'yyyy-MM-dd' */
  closeDate: string;
  holdingDays: number;
  /** The shares sold so far. */
  quantity: number;
  /** What the shares sold cost on average. */
  averageBuyPrice: number | null;
  averageSellPrice: number | null;
  realizedPnl: number | null;
  /** Of the buy cost. */
  realizedPnlPercent: number | null;
  /** What to fix in its trades (more sold than bought, so no realized P&L), in English; shown as it is. */
  warning: string | null;
  /** Of every buy and sell in it, already taken off `realizedPnl`. */
  commissions: number;
  source: ClosedPositionSource;
  /** The manual position it comes from, when `MANUAL`; `null` otherwise. */
  manualPositionId: number | null;
  /** The manual position's note; `null` for a holding, whose notes are on its trades. */
  note: string | null;
  /** Still held: 0 once a sell brought the stretch back to zero. */
  remainingQuantity: number;
  /** Every trade of the stretch, by date. */
  trades: Trade[];
  /** Whose shares were sold: a stretch is made of one investor's trades only. */
  investorId: number;
}

/** A deposit into the IB account for an investor, or a withdrawal. */
export interface CashMovement {
  id: number;
  /** 'yyyy-MM-dd' */
  movementDate: string;
  type: CashMovementType;
  /** Always above 0: `type` says which way the money went. */
  amount: number;
  note: string | null;
}

/** The body of `POST /api/investors/{id}/cash-movements` and `PUT /api/cash-movements/{id}`. */
export interface CashMovementRequest {
  /** 'yyyy-MM-dd', not in the future. */
  movementDate: string;
  type: CashMovementType;
  /** Greater than 0. */
  amount: number;
  note: string | null;
}

/** Something to check in what was entered for an investor. It never blocks anything. */
export interface InvestorWarning {
  type: InvestorWarningType;
  /** In English, shown as it is. */
  message: string;
}

/**
 * One investor's summary card. The account owner's figures are IB's less the other investors', so all of them always
 * add up to IB's own. Everything is in USD except the realized P&L, which is by currency. A figure that needs something
 * unknown — a trade without a price — is `null`.
 */
export interface Investor {
  id: number;
  name: string;
  /** Exactly one: the investor whose cash comes from IB, and who gets whatever the others' entries don't explain. */
  accountOwner: boolean;
  /** `null` for the account owner, who has no deposits. */
  depositsMinusWithdrawals: number | null;
  cash: number | null;
  sharesValue: number | null;
  /** Cash and shares; for the account owner IB's net liquidation value less the others'. */
  totalValue: number | null;
  /** What their shares cost, at average cost with their buy commissions. */
  sharesCost: number | null;
  unrealizedPnl: number | null;
  /** Of `sharesCost`. */
  unrealizedPnlPercent: number | null;
  /** `{"HKD": 950, "USD": 500}`; empty with no closed position yet. */
  realizedPnlByCurrency: Record<string, number>;
  /** Unrealized and realized, in USD only. */
  totalPnl: number | null;
  /** By date; always empty for the account owner. */
  cashMovements: CashMovement[];
  warnings: InvestorWarning[];
}

export interface PortfolioSnapshot {
  account: string;
  /** ISO-8601 instant of the last sync from TWS. */
  asOf: string;
  netLiquidation: number | null;
  totalCashValue: number | null;
  holdings: Holding[];
  /** From every holding, open or closed: one still open today may have been sold in full before. */
  closedPositions: ClosedPosition[];
  /** Every investor's card, the account owner first. */
  investors: Investor[];
}
