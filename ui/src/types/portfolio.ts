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
}
