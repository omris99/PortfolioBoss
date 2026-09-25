/** Shown for any value that is missing: a figure IB did not report, or a sector or date not entered yet. */
export const EMPTY_VALUE = '—';

const twoDecimalFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(amount: number | null): string {
  return amount === null ? EMPTY_VALUE : twoDecimalFormatter.format(amount);
}

/** Always shows the sign, e.g. "+1,250.00" or "-310.50". */
export function formatSignedMoney(amount: number | null): string {
  if (amount === null) return EMPTY_VALUE;
  const sign = amount >= 0 ? '+' : '-';
  return `${sign}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** Always shows the sign, with one decimal place, e.g. "+45.9%". */
export function formatSignedPercent(percent: number | null): string {
  if (percent === null) return EMPTY_VALUE;
  const sign = percent >= 0 ? '+' : '-';
  return `${sign}${Math.abs(percent).toFixed(1)}%`;
}

/** Whole share counts print without decimals; fractional shares keep 4 places. */
export function formatQuantity(shares: number): string {
  return Number.isInteger(shares) ? shares.toLocaleString('en-US') : shares.toFixed(4);
}

/** Green for a gain, red for a loss, grey when flat or unknown. */
export function profitLossColorClass(profitLoss: number | null): string {
  if (profitLoss === null) return 'text-slate-400';
  if (profitLoss > 0) return 'text-emerald-400';
  if (profitLoss < 0) return 'text-rose-400';
  return 'text-slate-400';
}

/** 'yyyy-MM-dd' is shown as it is: unambiguous in any locale, and it sorts correctly as text. */
export function formatDate(isoDate: string | null): string {
  return isoDate ?? EMPTY_VALUE;
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
