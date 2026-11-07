import { EMPTY_VALUE } from '../../lib/format';
import type { HotStock } from '../../types/recommendations';
import { RiskSigns } from '../companyInfo/RiskSigns';
import { CANDIDATE_NOT_CHECKED_HINTS } from './hotStockTexts';

/**
 * A scanned stock's risk signs, each with its figure when `showFigures`: "—" while it was never analyzed — Finnhub is
 * asked only with the analysis —, then as every stock shows them.
 */
export function CandidateRiskSigns({ stock, showFigures }: { stock: HotStock; showFigures: boolean }) {
  if (stock.analysis === null) {
    return <span title="Not analyzed yet: the signs come with the analysis">{EMPTY_VALUE}</span>;
  }
  return (
    <RiskSigns
      companyInfo={stock.companyInfo}
      yearReturnPercent={stock.momentum.yearReturnPercent}
      showFigures={showFigures}
      notCheckedHints={CANDIDATE_NOT_CHECKED_HINTS}
    />
  );
}
