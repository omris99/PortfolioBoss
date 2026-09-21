/** Mirrors the JSON served by `GET /api/portfolio` (see PortfolioResponse and HoldingResponse in api/response/). */

export interface Holding {
  symbol: string;
  secType: string;
  currency: string;
  position: number;
  averageCost: number;
  marketPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  realizedPnl: number;
  account: string;
  costBasis: number;
  unrealizedPnlPercent: number;
}

export interface PortfolioSnapshot {
  account: string;
  /** ISO-8601 instant at which the snapshot was read from TWS. */
  asOf: string;
  /** `null` when IB did not report the figure. */
  netLiquidation: number | null;
  totalCashValue: number | null;
  holdings: Holding[];
}
