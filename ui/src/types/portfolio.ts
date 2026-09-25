/**
 * Mirrors the JSON served by `GET /api/portfolio` (the `*Response` records in
 * src/main/java/portfolioboss/api/response/) and sent to the write endpoints (the `*Request` records in
 * api/request/). A figure IB did not report arrives as `null`.
 */

export type HoldingStatus = 'OPEN' | 'CLOSED';
export type TradeSide = 'BUY' | 'SELL';

/** One buy or sell entered by hand. */
export interface Trade {
  id: number;
  /** 'yyyy-MM-dd' */
  tradeDate: string;
  side: TradeSide;
  quantity: number;
  price: number | null;
  note: string | null;
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
}

export interface PortfolioSnapshot {
  account: string;
  /** ISO-8601 instant of the last sync from TWS. */
  asOf: string;
  netLiquidation: number | null;
  totalCashValue: number | null;
  holdings: Holding[];
}
