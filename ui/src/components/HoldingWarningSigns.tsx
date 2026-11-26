import type { Holding } from '../types/portfolio';
import { RiskSigns, type NotCheckedHints } from './companyInfo/RiskSigns';
import { LabeledValue } from './LabeledValue';
import { SignalWords } from './Signal';

/**
 * Why a holding's company risks are unknown: Finnhub is asked on every run that reaches TWS, about the holdings in
 * dollars only (MY_HOLDINGS_TODO.md, decision 10).
 */
export const HOLDING_NOT_CHECKED_HINTS: NotCheckedHints = {
  finnhubDidNotAnswer:
    'No answer from Finnhub: it is asked on every run that reaches TWS, about the holdings in dollars only — on ' +
    'another exchange IB’s symbol isn’t Finnhub’s —, and only when its key is set.',
  finnhubHadNoFigures: 'Finnhub has none of the company’s four figures, so the company risks could not be checked.',
};

/**
 * An open holding's company risks with their figures ("Expensive, P/E 52"), "No company risks", or "Not checked" when
 * Finnhub couldn't check them. `stacked`: each risk on a line of its own — the Averaging calculator's narrow slot.
 */
export function HoldingCompanyRisks({ holding, stacked = false }: { holding: Holding; stacked?: boolean }) {
  return (
    <RiskSigns
      companyInfo={holding.companyInfo}
      yearReturnPercent={holding.momentum?.yearReturnPercent ?? null}
      showFigures
      notCheckedHints={HOLDING_NOT_CHECKED_HINTS}
      stacked={stacked}
    />
  );
}

/**
 * An open holding's signs in two labeled groups that can't be confused (round 6, the user's names): "Signal" — the three
 * warning signs the dot counts — and "Company risks" — Finnhub's figures and the year's return. The drawer's line under
 * the key figures.
 */
export function HoldingSignsAndRisks({ holding }: { holding: Holding }) {
  return (
    <>
      <LabeledValue label="Signal">
        <SignalWords holding={holding} />
      </LabeledValue>
      <LabeledValue label="Company risks">
        <HoldingCompanyRisks holding={holding} />
      </LabeledValue>
    </>
  );
}
