import type { Holding } from '../types/portfolio';

/**
 * The API also sends the holdings TWS no longer reports (CLOSED), whose sector and trades are kept. The Overview counts
 * only the open ones, and Positions shows the closed ones only on request.
 */
export function openHoldingsOf(holdings: Holding[]): Holding[] {
  return holdings.filter((holding) => holding.status === 'OPEN');
}
