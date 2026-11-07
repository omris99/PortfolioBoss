import { EMPTY_VALUE } from '../lib/format';
import type { Holding } from '../types/portfolio';
import { notCheckedHintOf, RiskSignTagList, type NotCheckedHints } from './companyInfo/RiskSigns';
import { SignalWarningSignTags, warningSignsOf } from './Signal';

/**
 * Why a holding's risk signs are unknown: Finnhub is asked on every run that reaches TWS, about the holdings in dollars
 * only (MY_HOLDINGS_TODO.md, decision 10).
 */
export const HOLDING_NOT_CHECKED_HINTS: NotCheckedHints = {
  finnhubDidNotAnswer:
    'No answer from Finnhub: it is asked on every run that reaches TWS, about the holdings in dollars only — on ' +
    'another exchange IB’s symbol isn’t Finnhub’s —, and only when its key is set.',
  finnhubHadNoFigures: 'Finnhub has none of the company’s four figures, so the risk signs could not be checked.',
};

const NO_WARNING_SIGN_HINT = 'No warning signs: none of the Signal dot’s, and none of the five risk signs';

/**
 * An open holding's warning signs as red tags: the Signal dot's three, then the risk signs with their figures
 * ("Expensive · P/E 52"); "—" when there are none, and "risk signs not checked" when Finnhub couldn't check them. Shown
 * together only: the dot still counts its own three. The drawer's line under the key figures and the Averaging
 * calculator's line under each holding.
 */
export function HoldingWarningSigns({ holding }: { holding: Holding }) {
  const companyInfo = holding.companyInfo;
  const riskSignsNotCheckedHint = notCheckedHintOf(companyInfo, HOLDING_NOT_CHECKED_HINTS);
  const warningSignCount = warningSignsOf(holding).length + (companyInfo?.riskSigns ?? []).length;
  return (
    <>
      <SignalWarningSignTags holding={holding} />
      <RiskSignTagList
        companyInfo={companyInfo}
        yearReturnPercent={holding.momentum?.yearReturnPercent ?? null}
        showFigures
      />
      {warningSignCount === 0 && riskSignsNotCheckedHint === null && (
        <span title={NO_WARNING_SIGN_HINT}>{EMPTY_VALUE}</span>
      )}
      {riskSignsNotCheckedHint !== null && (
        <span title={riskSignsNotCheckedHint} className="text-muted">
          risk signs not checked
        </span>
      )}
    </>
  );
}
