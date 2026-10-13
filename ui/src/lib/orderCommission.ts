const COMMISSION_PER_SHARE = 0.01;
const MIN_COMMISSION_PER_ORDER = 5;

/**
 * The commission an order of `shareCount` shares is charged by default: 1 cent a share, at least $5 — 100 shares cost
 * $5, 600 shares $6. Rounded to the cent. The same rule as the API's `OrderCommission.defaultFor`, which stores it for a
 * trade entered without a commission.
 */
export function defaultCommissionFor(shareCount: number): number {
  const commission = Math.max(shareCount * COMMISSION_PER_SHARE, MIN_COMMISSION_PER_ORDER);
  return Math.round(commission * 100) / 100;
}
