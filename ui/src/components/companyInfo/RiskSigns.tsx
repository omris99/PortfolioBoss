import { EMPTY_VALUE, formatSignedPercentOneDecimal } from '../../lib/format';
import { WARNING_SIGN_TAG_CLASS } from '../../lib/styles';
import type { CompanyFacts, CompanyInfo, RiskSign } from '../../types/portfolio';
import { TimingNoteWhenDue } from './TimingNote';

interface RiskSignWords {
  name: string;
  /** The rule that raises it, in words — which numbers raise a sign is the API's, these only describe it. */
  rule: string;
}

/** The five risk signs (RECOMMENDATIONS_TODO.md, decision 5), in the brief's words, sentence case. */
const RISK_SIGN_WORDS: Record<RiskSign, RiskSignWords> = {
  LOSES_MONEY: { name: 'Losing money', rule: 'a net margin below 0 over the last 12 months' },
  HIGH_DEBT: { name: 'High debt', rule: 'debt more than twice the equity' },
  EXPENSIVE: { name: 'Expensive', rule: 'a P/E above 50' },
  REVENUE_FALLING: { name: 'Revenue falling', rule: 'less revenue over the last 12 months than the 12 before' },
  RECOVERING: {
    name: 'Recovering',
    rule: 'lower than a year ago. After a crash the averages sink with the price, so a rebound looks like a trend fast.',
  },
};

/** What a sign's figure is read from: Finnhub's four figures, and for Recovering the year's return from the closes. */
interface FiguresBehindTheSigns {
  companyFacts: CompanyFacts;
  yearReturnPercent: number | null;
}

/** "P/E 52", "debt 2.4× equity": the figure that raised the sign; `null` when the API sent none. */
const RISK_SIGN_FIGURES: Record<RiskSign, (figures: FiguresBehindTheSigns) => string | null> = {
  LOSES_MONEY: ({ companyFacts }) =>
    companyFacts.netMarginPercent === null
      ? null
      : `net margin ${formatSignedPercentOneDecimal(companyFacts.netMarginPercent)}`,
  HIGH_DEBT: ({ companyFacts }) =>
    companyFacts.debtToEquity === null ? null : `debt ${companyFacts.debtToEquity.toFixed(1)}× equity`,
  EXPENSIVE: ({ companyFacts }) => (companyFacts.peRatio === null ? null : `P/E ${Math.round(companyFacts.peRatio)}`),
  REVENUE_FALLING: ({ companyFacts }) =>
    companyFacts.revenueGrowthPercent === null
      ? null
      : `revenue ${formatSignedPercentOneDecimal(companyFacts.revenueGrowthPercent)}`,
  RECOVERING: ({ yearReturnPercent }) =>
    yearReturnPercent === null ? null : `year ${formatSignedPercentOneDecimal(yearReturnPercent)}`,
};

/**
 * One of the five risk signs, in the warning-sign tone, in sentence case — never capitals. With `figure`, the figure
 * that raised it follows: "Expensive · P/E 52". Its hover names the rule it broke.
 */
export function RiskSignTag({ sign, figure = null }: { sign: RiskSign; figure?: string | null }) {
  const { name, rule } = RISK_SIGN_WORDS[sign];
  return (
    <span title={`${name} · a risk sign: ${figure === null ? '' : `${figure}, `}${rule}`} className={WARNING_SIGN_TAG_CLASS}>
      {name}
      {figure !== null && <span className="font-normal">&nbsp;· {figure}</span>}
    </span>
  );
}

/** Lower than a year ago — one of the five risk signs, so it wears their tag everywhere. */
export function RecoveringTag() {
  return <RiskSignTag sign="RECOVERING" />;
}

/**
 * The words, on hover, for the two ways a stock's signs go unchecked — which of the two it is, `RiskSigns` decides.
 * They differ by stock: a holding's Finnhub is asked about on every run, a scanned stock's only on Analyze.
 */
export interface NotCheckedHints {
  /** Finnhub never answered: the stock has no company info. */
  finnhubDidNotAnswer: string;
  /** Finnhub answered, but with none of the four figures. */
  finnhubHadNoFigures: string;
}

/** Why the stock's risk signs are unknown, in `notCheckedHints`' words; `null` when they were checked. */
export function notCheckedHintOf(companyInfo: CompanyInfo | null, notCheckedHints: NotCheckedHints): string | null {
  if (companyInfo === null) return notCheckedHints.finnhubDidNotAnswer;
  if (companyInfo.riskSigns === null) return notCheckedHints.finnhubHadNoFigures;
  return null;
}

/**
 * The tags of a stock's risk signs, each with the figure that raised it when `showFigures` — `yearReturnPercent` is
 * Recovering's —, with no row of their own, so that a row of other warning signs can hold them too; nothing when it
 * has none, or they were never checked.
 */
export function RiskSignTagList({
  companyInfo,
  yearReturnPercent,
  showFigures,
}: {
  companyInfo: CompanyInfo | null;
  yearReturnPercent: number | null;
  showFigures: boolean;
}) {
  if (companyInfo === null || companyInfo.riskSigns === null) return null;
  const figuresBehindTheSigns = { companyFacts: companyInfo.companyFacts, yearReturnPercent };
  return (
    <>
      {companyInfo.riskSigns.map((sign) => (
        <RiskSignTag
          key={sign}
          sign={sign}
          figure={showFigures ? RISK_SIGN_FIGURES[sign](figuresBehindTheSigns) : null}
        />
      ))}
    </>
  );
}

function NotChecked({ hint }: { hint: string }) {
  return (
    <span title={hint} className="text-muted">
      Not checked
    </span>
  );
}

/**
 * A stock's risk signs in a row, each with the figure that raised it when `showFigures`: "—" when it has none — not
 * green: having none is not a forecast —, "Not checked" when Finnhub never answered or gave none of its figures,
 * `notCheckedHints` saying which on hover.
 */
export function RiskSigns({
  companyInfo,
  yearReturnPercent,
  showFigures,
  notCheckedHints,
}: {
  companyInfo: CompanyInfo | null;
  yearReturnPercent: number | null;
  showFigures: boolean;
  notCheckedHints: NotCheckedHints;
}) {
  const notCheckedHint = notCheckedHintOf(companyInfo, notCheckedHints);
  if (notCheckedHint !== null) return <NotChecked hint={notCheckedHint} />;
  if ((companyInfo?.riskSigns ?? []).length === 0) {
    return <span title="No risk signs: none of the five rules raised one">{EMPTY_VALUE}</span>;
  }
  return (
    <span className="inline-flex flex-wrap gap-1">
      <RiskSignTagList companyInfo={companyInfo} yearReturnPercent={yearReturnPercent} showFigures={showFigures} />
    </span>
  );
}

/** Under a drawer's key figures: "Risk signs", each with its figure, then the timing note when the report is due. */
export function RiskSignsLine({
  companyInfo,
  yearReturnPercent,
  notCheckedHints,
}: {
  companyInfo: CompanyInfo | null;
  yearReturnPercent: number | null;
  notCheckedHints: NotCheckedHints;
}) {
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-caption">
      <span className="text-muted">Risk signs</span>
      <RiskSigns
        companyInfo={companyInfo}
        yearReturnPercent={yearReturnPercent}
        showFigures
        notCheckedHints={notCheckedHints}
      />
      <TimingNoteWhenDue companyInfo={companyInfo} />
    </div>
  );
}
