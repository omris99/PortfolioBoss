const twoDecimalFormatter = new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatMoney(amount: number): string {
  return twoDecimalFormatter.format(amount);
}

/** Always shows the sign, e.g. "+1,250.00" or "-310.50". */
export function formatSignedMoney(amount: number): string {
  const sign = amount >= 0 ? '+' : '-';
  return `${sign}${twoDecimalFormatter.format(Math.abs(amount))}`;
}

/** Always shows the sign, with one decimal place, e.g. "+45.9%". */
export function formatSignedPercent(percent: number): string {
  const sign = percent >= 0 ? '+' : '-';
  return `${sign}${Math.abs(percent).toFixed(1)}%`;
}

/** Whole share counts print without decimals; fractional shares keep 4 places. */
export function formatQuantity(shares: number): string {
  return Number.isInteger(shares) ? shares.toLocaleString('en-US') : shares.toFixed(4);
}

/** Green for a gain, red for a loss, grey when flat. */
export function profitLossColorClass(profitLoss: number): string {
  if (profitLoss > 0) return 'text-emerald-400';
  if (profitLoss < 0) return 'text-rose-400';
  return 'text-slate-400';
}
