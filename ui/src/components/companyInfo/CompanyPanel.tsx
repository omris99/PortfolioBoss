import type { ReactNode } from 'react';
import { EMPTY_VALUE, formatDateTime, formatSignedPercentOneDecimal } from '../../lib/format';
import type { CompanyFacts, CompanyInfo, RiskSign } from '../../types/portfolio';
import { RiskSignTag, type NotCheckedHints } from './RiskSigns';
import { NextEarnings } from './TimingNote';

/**
 * A drawer's Company tab, a holding's or a scanned stock's (DESIGN_BRIEF.md §8.3): when Finnhub answered, the next
 * earnings date and Finnhub's four figures, each with the rule it is checked against and the risk-sign tag beside the
 * figure that raises one.
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

/** A row of the tab's bordered lists: the label with its rule under it, the value on the right. */
export function CompanyRow({ label, rule, children }: { label: string; rule?: string; children: ReactNode }) {
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

/** When Finnhub answered, then the next report and the four figures; under them why the signs are unknown, if they are. */
function CompanyFigures({
  companyInfo,
  finnhubHadNoFiguresHint,
}: {
  companyInfo: CompanyInfo;
  finnhubHadNoFiguresHint: string;
}) {
  const riskSigns = companyInfo.riskSigns ?? [];
  return (
    <>
      <p className="mb-4 text-muted">From Finnhub, answered {formatDateTime(companyInfo.finnhubAnsweredAt)}</p>
      <ul className="divide-y rounded-lg border">
        <CompanyRow label="Next earnings">
          <NextEarnings companyInfo={companyInfo} />
        </CompanyRow>
        {COMPANY_FIGURES.map((figure) => (
          <CompanyRow key={figure.label} label={figure.label} rule={figure.rule}>
            {riskSigns.includes(figure.sign) && <RiskSignTag sign={figure.sign} />}
            <span className="font-medium">{figure.valueOf(companyInfo.companyFacts)}</span>
          </CompanyRow>
        ))}
      </ul>
      {companyInfo.riskSigns === null && <p className="mt-2 text-caption text-faint">{finnhubHadNoFiguresHint}</p>}
    </>
  );
}

/**
 * The tab: Finnhub's figures, or — when it never answered — `notCheckedHints`' words for why; `children` come after
 * them (a scanned stock's part in the recommendation).
 */
export function CompanyPanel({
  companyInfo,
  notCheckedHints,
  children,
}: {
  companyInfo: CompanyInfo | null;
  notCheckedHints: NotCheckedHints;
  children?: ReactNode;
}) {
  return (
    <div>
      {companyInfo === null ? (
        <p className="py-6 text-muted">{notCheckedHints.finnhubDidNotAnswer}</p>
      ) : (
        <CompanyFigures companyInfo={companyInfo} finnhubHadNoFiguresHint={notCheckedHints.finnhubHadNoFigures} />
      )}
      {children}
    </div>
  );
}
