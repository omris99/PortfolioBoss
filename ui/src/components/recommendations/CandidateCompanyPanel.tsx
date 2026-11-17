import type { HotStock } from '../../types/recommendations';
import { TargetUpside } from '../AnalystTarget';
import { CompanyPanel, CompanyRow } from '../companyInfo/CompanyPanel';
import { CandidateRiskSigns } from './CandidateRiskSigns';
import { CANDIDATE_NOT_CHECKED_HINTS } from './hotStockTexts';
import { formatPoints, NEWS_SCORE_HINT } from './NewsScore';
import { NotPickableReasons } from './NotPickableReasons';

/**
 * A scanned stock's Company tab: the company info every stock shows — Finnhub is asked only on Analyze, so it comes with
 * the analysis —, then what the recommendation's rule says about the stock.
 */

/** "No: analysts' target +4.2%; a pick needs +15% · …", or yes — every reason the API gives, in its order. */
function CanItBePicked({ stock }: { stock: HotStock }) {
  if (stock.canBePicked) return <>Yes: its target and its risk signs pass the rule</>;
  return (
    <span className="inline-flex flex-wrap items-center justify-end gap-1.5">
      No: <NotPickableReasons stock={stock} />
    </span>
  );
}

/** What the recommendation's rule makes of the stock: its analysts' target, whether it can be picked, its score. */
function ForTheRecommendation({ stock }: { stock: HotStock }) {
  return (
    <section className="mt-5">
      <h3 className="mb-2 text-label text-muted">For the recommendation</h3>
      <ul className="divide-y rounded-lg border">
        <CompanyRow label="Analysts’ target" rule="From Dow Jones: 15% above the price or more, of 3 firms or more">
          <TargetUpside analystTarget={stock.analystTarget} />
        </CompanyRow>
        {stock.riskWeight !== null && (
          <CompanyRow label="Risk signs">
            <CandidateRiskSigns stock={stock} showFigures={false} />
            <span className="text-muted">weigh {stock.riskWeight}</span>
          </CompanyRow>
        )}
        <CompanyRow label="Can it be picked">
          <CanItBePicked stock={stock} />
        </CompanyRow>
        {stock.score !== null && stock.newsScore !== null && (
          <CompanyRow label="Score" rule="The news score less the signs’ weight: the picks are ordered by it">
            <span title={NEWS_SCORE_HINT} className="text-muted">
              news {formatPoints(stock.newsScore)} − {stock.riskWeight} =
            </span>
            <b className="font-semibold">{formatPoints(stock.score)}</b>
          </CompanyRow>
        )}
      </ul>
    </section>
  );
}

export function CandidateCompanyPanel({ stock }: { stock: HotStock }) {
  if (stock.analysis === null) {
    return (
      <p className="py-6 text-muted">
        Not analyzed yet. The company’s figures and its next earnings date come from Finnhub with the analysis:
        Analyze in the Analysts & news tab, or in its row.
      </p>
    );
  }
  return (
    <CompanyPanel companyInfo={stock.companyInfo} notCheckedHints={CANDIDATE_NOT_CHECKED_HINTS}>
      <ForTheRecommendation stock={stock} />
    </CompanyPanel>
  );
}
