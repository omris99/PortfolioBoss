/**
 * What `GET /api/taxes` answers (the server's `tax.response` records, CAPITAL_GAINS_TAX_TODO.md sessions 4–5) and what
 * its confirmations take. Mirrored by hand, like `portfolio.ts`: a key renamed on one side must be renamed on the other.
 * Amounts in shekels unless the field says otherwise; dates as 'yyyy-MM-dd'.
 */

/** What a settlement day needs on the page; `CONFIRMED` only on the account's days. */
export type SettlementDayStatus =
  | 'CALCULATED'
  | 'CONFIRMED'
  | 'CALCULATED_TO_ZERO'
  | 'WAITING_FOR_A_RATE'
  | 'CANNOT_BE_CALCULATED'
  | 'SOLD_MORE_THAN_BOUGHT';

/** Why a sale's taxable gain was not calculated. */
export type SaleNotComputedReason =
  | 'CORPORATE_ACTION_NOT_SUPPORTED'
  | 'COMMISSION_IN_ANOTHER_CURRENCY'
  | 'SOLD_MORE_THAN_BOUGHT'
  | 'NO_RATE_FOR_THE_CURRENCY'
  | 'RATE_NOT_PUBLISHED_YET';

/**
 * The shares a sale took from one buy, first in first out, and their taxable gain both ways. `cost` and `proceeds` are
 * in the trade's currency; the rates and everything in shekels are `null` on a sale not calculated.
 */
export interface SharesSoldFromOneBuy {
  buyTradeId: string;
  buyTradeDate: string;
  buySettlementDay: string;
  quantity: number;
  cost: number;
  proceeds: number;
  buyRate: number | null;
  saleRate: number | null;
  gainInDollarsAtTheSaleRate: number | null;
  gainInShekels: number | null;
  taxableGain: number | null;
}

/** One sale — for the account one execution of IB's, so an order sold in parts is several. */
export interface Sale {
  tradeId: string;
  symbol: string;
  currency: string;
  tradeDate: string;
  settlementDay: string;
  quantity: number;
  /** In the trade's currency, like `commission`. */
  price: number;
  commission: number;
  quantitySoldBeyondTheBuys: number;
  /** `null` when the sale is calculated. */
  notComputedReason: SaleNotComputedReason | null;
  /** `null` when not calculated. */
  taxableGain: number | null;
  sharesSoldFromEachBuy: SharesSoldFromOneBuy[];
  /** Its cost first in first out is more than a cent from its cost at the Closed page's average cost. */
  fifoCostDiffersFromAverageCost: boolean;
}

/** One of IB's fees (or its cancellation), with IB's sign: negative for a fee. */
export interface TaxDeductibleExpense {
  ibTransactionId: string;
  currency: string;
  amount: number;
  description: string;
  rate: number | null;
  amountInShekels: number | null;
}

/** What the user confirmed for a settlement day: what the broker recorded, final. */
export interface TaxConfirmation {
  id: number;
  settlementDay: string;
  /** Positive: tax; negative: a shield. */
  taxAmount: number;
  /** An instant, "2026-07-02T06:40:00Z". */
  confirmedAt: string;
  note: string | null;
}

/** One settlement day's entry: its sales and fees netted into one amount, and what the user confirmed for it. */
export interface SettlementDay {
  settlementDay: string;
  sales: Sale[];
  expenses: TaxDeductibleExpense[];
  taxableGainOfTheDay: number;
  /** 25% of the day's taxable gain: positive, tax; negative, a shield. */
  computedTaxAmount: number;
  /** `null` while the day waits, and always on an investor's day. */
  confirmation: TaxConfirmation | null;
  status: SettlementDayStatus;
}

/** How a month settled at its end. */
export interface MonthlySettlement {
  /** "2026-06". */
  month: string;
  entriesOfTheMonth: number;
  /** Positive: paid; negative: refunded; 0: nothing moves. */
  settlementAmount: number;
  shieldLeft: number;
  /** Whether the month had ended by the last sync. */
  settled: boolean;
}

/** One tax year: the account's as recorded at the broker, an investor's as calculated. */
export interface TaxYear {
  year: number;
  taxableGain: number;
  taxOfTheYearSoFar: number;
  shieldLeft: number;
  /** The earliest first. */
  settlementDays: SettlementDay[];
  /** The earliest first; only the months with a settlement day. */
  monthlySettlements: MonthlySettlement[];
}

/** An investor other than the account owner, from the trades entered for them. */
export interface InvestorTax {
  investorId: number;
  investorName: string;
  taxYears: TaxYear[];
}

export interface Taxes {
  lastSyncDay: string;
  accountTaxYears: TaxYear[];
  otherInvestorsTax: InvestorTax[];
  /** Whether FLEX_TOKEN and FLEX_QUERY_ID are set. */
  flexConnected: boolean;
  /** The last day of IB's reports stored; `null` before the first download. */
  flexReportsStoredUpTo: string | null;
}

export interface TaxConfirmationRequest {
  settlementDay: string;
  taxAmount: number;
  note: string | null;
}

/** One day or a whole month's days, stored together or not at all. */
export interface TaxConfirmationsRequest {
  confirmations: TaxConfirmationRequest[];
}

export interface TaxConfirmationCorrectionRequest {
  taxAmount: number;
  note: string | null;
}
