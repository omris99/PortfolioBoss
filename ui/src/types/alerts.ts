/**
 * What `GET /api/alerts` answers — mirrors the records of `portfolioboss.alerts.response` by hand: nothing checks that
 * the two agree, so a key is renamed only together with them.
 */

/** The nine kinds of alert, as `alerts.AlertType` names them. */
export type AlertType =
  | 'MOMENTUM_TURNED_WEAK'
  | 'CLOSED_BELOW_SMA50'
  | 'CLOSED_BELOW_SMA200'
  | 'EARNINGS_SOON'
  | 'ANALYST_CUT_TARGET'
  | 'FAR_BELOW_AVERAGE_COST'
  | 'ABOVE_WEIGHT_LIMIT'
  | 'TRADES_DONT_MATCH_IB'
  | 'ENTERED_SHORT_LIST';

/**
 * The bell's two groups, as `model.alerts.AlertGroup` names them; which one an alert is in is the server's
 * (`AlertGroupCalculator`): a stock held now is first of all a holding.
 */
export type AlertGroup = 'YOUR_HOLDINGS' | 'SHORT_LIST';

export interface Alert {
  id: number;
  alertType: AlertType;
  conId: number;
  symbol: string;
  /** The holding whose drawer it opens; `null` for a stock from the scan that isn't held. */
  holdingId: number | null;
  /** In English, as the server wrote it when the condition became true. */
  message: string;
  /** ISO-8601 instant: the check that found it true. */
  createdAt: string;
  /** `null` while the bell hasn't been opened after it — the bell counts these. */
  seenAt: string | null;
  /** The group of the bell's panel it is shown in. */
  group: AlertGroup;
}

/** One line of the settings drawer: as saved, or the type's default. */
export interface AlertSetting {
  alertType: AlertType;
  enabled: boolean;
  /** Days or percent; `null` for a type without a number. */
  threshold: number | null;
  defaultThreshold: number | null;
}

export interface Alerts {
  /** The types turned on only, open or ended, the newest first. */
  alerts: Alert[];
  /** Every type, in the drawer's order. */
  settings: AlertSetting[];
}
