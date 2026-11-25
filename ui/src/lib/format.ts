/** Shown for any value that is missing: a figure or a sector IB did not report, or a date not entered yet. */
export const EMPTY_VALUE = '—';

/** The real minus sign, as wide as "+", rather than a hyphen. */
const MINUS_SIGN = '−';

const twoDecimalFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** "1,250.00", or "−310.50" for a negative amount. */
export function formatMoney(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  const sign = amount < 0 ? MINUS_SIGN : '';
  return `${sign}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** Always shows the sign, e.g. "+1,250.00" or "−310.50". */
export function formatSignedMoney(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  const sign = amount >= 0 ? '+' : MINUS_SIGN;
  return `${sign}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** Always shows the sign, with two decimal places, e.g. "+45.92%". */
export function formatSignedPercent(percent: number | null): string {
  if (percent === null) return EMPTY_VALUE;
  const sign = percent >= 0 ? '+' : MINUS_SIGN;
  return `${sign}${Math.abs(percent).toFixed(2)}%`;
}

/** A stock's move, with one decimal place and its sign: "+6.1%", "−7.4%", and "0.0%" with no sign at all. */
export function formatSignedPercentOneDecimal(percent: number | null): string {
  if (percent === null) return EMPTY_VALUE;
  const rounded = Math.abs(percent).toFixed(1);
  if (Number(rounded) === 0) return `${rounded}%`;
  return `${percent > 0 ? '+' : MINUS_SIGN}${rounded}%`;
}

/** A correlation between −1 and 1, with two decimals: "0.81", "−0.12". */
export function formatCorrelation(coefficient: number): string {
  const sign = coefficient < 0 ? MINUS_SIGN : '';
  return `${sign}${Math.abs(coefficient).toFixed(2)}`;
}

/** A part of a whole, with one decimal place and a sign only when below zero: "24.1%", "−3.0%". */
export function formatPercent(percent: number | null): string {
  if (percent === null) return EMPTY_VALUE;
  const sign = percent < 0 ? MINUS_SIGN : '';
  return `${sign}${Math.abs(percent).toFixed(1)}%`;
}

const SHEKEL_SIGN = '₪';
const DOLLAR_SIGN = '$';

/** An amount in shekels, the sign before the ₪: "₪1,277.13", "−₪35.76". */
export function formatShekels(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  const sign = amount < 0 ? MINUS_SIGN : '';
  return `${sign}${SHEKEL_SIGN}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** Always with its sign, and none for 0: "+₪5,108.50", "−₪3,397.50", "₪0.00". */
export function formatSignedShekels(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  if (amount === 0) return formatShekels(0);
  const sign = amount > 0 ? '+' : MINUS_SIGN;
  return `${sign}${SHEKEL_SIGN}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** An amount in dollars, the sign before the $: "$4,205.00", "−$12.00". */
export function formatDollars(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  const sign = amount < 0 ? MINUS_SIGN : '';
  return `${sign}${DOLLAR_SIGN}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** An amount in a trade's own currency: "$4,205.00" in dollars, "2,500.00 HKD" in any other. */
export function formatAmountInItsCurrency(amount: number, currency: string): string {
  return currency === ACCOUNT_CURRENCY ? formatDollars(amount) : `${formatMoney(amount)} ${currency}`;
}

/** A Bank of Israel rate, with its 3 decimals: "3.050". */
export function formatExchangeRate(rate: number | null): string {
  return rate === null ? EMPTY_VALUE : rate.toFixed(3);
}

/** Whole share counts print without decimals; fractional shares keep 4 places. */
export function formatQuantity(shares: number): string {
  return Number.isInteger(shares) ? shares.toLocaleString('en-US') : shares.toFixed(4);
}

/** The account's currency: every figure but a realized P&L is in it. */
export const ACCOUNT_CURRENCY = 'USD';

/**
 * Totals per currency, the account's first and then A→Z — "+1,757.89 USD · +950.00 HKD". Currencies are never added
 * together.
 */
export function currencyTotalsInOrder(totalsByCurrency: Record<string, number> | Map<string, number>): [string, number][] {
  const entries = totalsByCurrency instanceof Map ? [...totalsByCurrency] : Object.entries(totalsByCurrency);
  return entries.sort(([firstCurrency], [secondCurrency]) => {
    if (firstCurrency === ACCOUNT_CURRENCY) return -1;
    if (secondCurrency === ACCOUNT_CURRENCY) return 1;
    return firstCurrency.localeCompare(secondCurrency);
  });
}

/** Green for a gain, red for a loss; the ordinary text color when flat or unknown. */
export function profitLossColorClass(profitLoss: number | null): string {
  if (profitLoss === null || profitLoss === 0) return 'text-ink';
  return profitLoss > 0 ? 'text-positive' : 'text-negative';
}

/** The API's day, "2026-11-22". */
const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * A day from the API ("2026-11-22") as the user reads it, "22/11/2026" (DESIGN_BRIEF.md §11.6, rule 5 — yyyy-MM-dd until
 * round 6). Only for showing: sorting and the API keep the ISO text.
 */
export function formatDate(isoDate: string | null): string {
  if (isoDate === null) return EMPTY_VALUE;
  const parts = ISO_DATE_PATTERN.exec(isoDate);
  if (parts === null) return isoDate;
  const [, year, month, day] = parts;
  return `${day}/${month}/${year}`;
}

function twoDigits(value: number): string {
  return String(value).padStart(2, '0');
}

/** A moment's day as "22/11/2026", in this computer's time zone. */
export function formatDayOf(moment: Date): string {
  return `${twoDigits(moment.getDate())}/${twoDigits(moment.getMonth() + 1)}/${moment.getFullYear()}`;
}

/** A moment's time of day as "19:10", in this computer's time zone. */
export function formatTimeOf(moment: Date): string {
  return `${twoDigits(moment.getHours())}:${twoDigits(moment.getMinutes())}`;
}

/** An instant from the API ("2026-10-10T13:05:12Z") as "10/10/2026 16:05", in this computer's time zone. */
export function formatDateTime(isoInstant: string): string {
  const moment = new Date(isoInstant);
  return `${formatDayOf(moment)} ${formatTimeOf(moment)}`;
}

const DAYS_PER_YEAR = 365;
/** An approximation, which is why the exact day count is also available ({@link formatDayCount}). */
const DAYS_PER_MONTH = 30;

/** The two largest units: 412 → "1y 1m", 45 → "1m 15d", 6 → "6d". */
export function formatHoldingPeriod(days: number | null): string {
  if (days === null) return EMPTY_VALUE;

  const years = Math.floor(days / DAYS_PER_YEAR);
  const daysAfterYears = days % DAYS_PER_YEAR;
  const months = Math.floor(daysAfterYears / DAYS_PER_MONTH);
  const daysAfterMonths = daysAfterYears % DAYS_PER_MONTH;

  if (years > 0) return withSmallerUnit(`${years}y`, months, 'm');
  if (months > 0) return withSmallerUnit(`${months}m`, daysAfterMonths, 'd');
  return `${daysAfterMonths}d`;
}

/** "1y" with 3 months → "1y 3m"; with 0 months → just "1y". */
function withSmallerUnit(largerPart: string, smallerAmount: number, smallerUnit: string): string {
  return smallerAmount > 0 ? `${largerPart} ${smallerAmount}${smallerUnit}` : largerPart;
}

/** 412 → "412 days", 1 → "1 day". */
export function formatDayCount(days: number): string {
  return days === 1 ? '1 day' : `${days.toLocaleString('en-US')} days`;
}
