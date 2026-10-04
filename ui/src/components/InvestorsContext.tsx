import { createContext, useContext, type ReactNode } from 'react';
import { EMPTY_VALUE } from '../lib/format';
import type { Investor } from '../types/portfolio';

/**
 * The investors of the last snapshot, for every component that shows or picks one — the trade forms, the trades lists,
 * the quantity column — without passing them down through every component in between. React calls this a context: it
 * is set once, at the top of `App`, and read with `useInvestors()` wherever it is needed.
 */
const InvestorsContext = createContext<Investor[]>([]);

export function InvestorsProvider({ investors, children }: { investors: Investor[]; children: ReactNode }) {
  return <InvestorsContext.Provider value={investors}>{children}</InvestorsContext.Provider>;
}

/** Every investor, the account owner first. Empty before the first snapshot. */
export function useInvestors(): Investor[] {
  return useContext(InvestorsContext);
}

/** With the account owner alone there is nothing to tell apart: the investor fields and columns stay hidden. */
export function hasSeveralInvestors(investors: Investor[]): boolean {
  return investors.length > 1;
}

export function accountOwnerOf(investors: Investor[]): Investor | null {
  return investors.find((investor) => investor.accountOwner) ?? null;
}

/** "Avi"; "—" for an id not in the list, which only happens for a moment while the portfolio reloads. */
export function investorNameOf(investors: Investor[], investorId: number): string {
  return investors.find((investor) => investor.id === investorId)?.name ?? EMPTY_VALUE;
}
