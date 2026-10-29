import type { ReactNode } from 'react';
import { EMPTY_VALUE, formatDateTime, formatSignedPercentOneDecimal } from '../../lib/format';
import type { CompanyFacts, HotStock, RiskSign } from '../../types/recommendations';
import { TargetUpside } from './AnalystTarget';
import { formatPoints, NEWS_SCORE_HINT } from './NewsScore';
import { NotPickableReasons } from './NotPickableReasons';
import { RiskSigns, RiskSignTag } from './RiskSignTag';
import { earningsText, TimingNote } from './TimingNote';

/**
 * The stock drawer's Company tab (DESIGN_BRIEF.md §8.3): the next earnings date and Finnhub's four figures, each with
 * the rule it is checked against and the risk-sign tag beside the figure that raises one; then what the recommendation's
 * rule says about the stock. All of it comes with the analysis — Finnhub is asked only on Analyze.
 */

interface CompanyFigure {
  label: string;
  /** The rule in words, under the label: "Expensive above 50". */
  rule: string;
  sign: RiskSign;
  valueOf: (companyFacts: CompanyFacts) => string;
}

function percentOrEmpty(percent: number | null): string {
  return percent === null ? EMPTY_VALUE : formatSignedPercentOneDecimal(percent);
}

const COMPANY_FIGURES: CompanyFigure[] = [
  {
    label: 'P/E',
    rule: 'Expensive above 50',
    sign: 'EXPENSIVE',
    valueOf: (companyFacts) => (companyFacts.peRatio === null ? EMPTY_VALUE : companyFacts.peRatio.toFixed(1)),
  },
  {
    label: 'Net margin, 12 months',
    rule: 'Losing money below 0%',
    sign: 'LOSES_MONEY',
    valueOf: (companyFacts) => percentOrEmpty(companyFacts.netMarginPercent),
  },
  {
    label: 'Debt to equity',
    rule: 'High debt above 2×',
    sign: 'HIGH_DEBT',
    valueOf: (companyFacts) =>
      companyFacts.debtToEquity === null ? EMPTY_VALUE : `${companyFacts.debtToEquity.toFixed(2)}×`,
  },
  {
    label: 'Revenue growth, 12 months',
    rule: 'Revenue falling below 0%',
    sign: 'REVENUE_FALLING',
    valueOf: (companyFacts) => percentOrEmpty(companyFacts.revenueGrowthPercent),
  },
];

function CompanyRow({ label, rule, children }: { label: string; rule?: string; children: ReactNode }) {
  return (
    <li className="flex min-h-12 items-center justify-between gap-3 px-3 py-2">
      <span>
        <span className="block text-muted">{label}</span>
        {rule !== undefined && <span className="block text-caption text-faint">{rule}</span>}
      </span>
      <span className="inline-flex items-center gap-2 text-right">{children}</span>
    </li>
  );
}

function NextEarnings({ stock }: { stock: HotStock }) {
  if (stock.nextEarningsDate === null) {
    return <span className="text-muted">none in the next four months</span>;
  }
  if (stock.reportsWithin14Days) return <TimingNote earningsDate={stock.nextEarningsDate} showsDateOnly={false} />;
  return <span>{earningsText(stock.nextEarningsDate)}</span>;
}

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
        {stock.riskSigns !== null && (
          <CompanyRow label="Risk signs">
            <RiskSigns stock={stock} showFigures={false} />
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

export function CompanyPanel({ stock }: { stock: HotStock }) {
  if (stock.analysis === null) {
    return (
      <p className="py-6 text-muted">
        Not analyzed yet. The company’s figures and its next earnings date come from Finnhub with the analysis:
        Analyze in the Analysts & news tab, or in its row.
      </p>
    );
  }
  const companyFacts = stock.companyFacts;
  const riskSigns = stock.riskSigns ?? [];
  return (
    <div>
      <p className="mb-4 text-muted">From Finnhub, with the analysis of {formatDateTime(stock.analysis.analyzedAt)}</p>
      <ul className="divide-y rounded-lg border">
        <CompanyRow label="Next earnings">
          <NextEarnings stock={stock} />
        </CompanyRow>
        {companyFacts === null ? (
          <li className="px-3 py-3 text-muted">
            Finnhub gave no figures: four of the five risk signs were never checked, so it can’t be picked.
          </li>
        ) : (
          COMPANY_FIGURES.map((figure) => (
            <CompanyRow key={figure.label} label={figure.label} rule={figure.rule}>
              {riskSigns.includes(figure.sign) && <RiskSignTag sign={figure.sign} />}
              <span className="font-medium">{figure.valueOf(companyFacts)}</span>
            </CompanyRow>
          ))
        )}
      </ul>
      <ForTheRecommendation stock={stock} />
    </div>
  );
}
