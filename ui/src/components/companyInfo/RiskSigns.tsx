import { Fragment } from 'react';
import { formatSignedPercentOneDecimal } from '../../lib/format';
import type { CompanyFacts, CompanyInfo, RiskSign } from '../../types/portfolio';
import { Hint } from '../Hint';
import { LabeledValue } from '../LabeledValue';
import { TimingNoteWhenDue } from './TimingNote';

interface RiskSignWords {
  name: string;
  /** The rule that raises it, in words — which numbers raise a sign is the API's, these only describe it. */
  rule: string;
}

/**
 * The five risk signs (RECOMMENDATIONS_TODO.md, decision 5), in the brief's words, sentence case. On screen they are
 * "Company risks" since round 6 — the user's name, so that they can't be confused with the Signal dot's warning signs;
 * the code keeps the API's `RiskSign`.
 */
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
 * One of the five company risks as words, not a tag (round 6, DESIGN_BRIEF.md §11.6, rule 2): its name bold in the loss
 * tone, then with `figure` the figure that raised it — "Expensive, P/E 52". Its hint names the rule it broke.
 */
export function RiskSignTag({ sign, figure = null }: { sign: RiskSign; figure?: string | null }) {
  const { name, rule } = RISK_SIGN_WORDS[sign];
  // The name and the figure each stay whole, and the line may break between them: in a narrow slot (the Averaging
  // calculator's) "Losing money, net margin −3.8%" would otherwise run into the next one. So the hint sits on the
  // words themselves, which stay inline.
  return (
    <Hint text={`${name} · a company risk: ${figure === null ? '' : `${figure}, `}${rule}`} wrapInSpan={false}>
      <span className="rounded-sm text-negative">
        <span className="whitespace-nowrap font-semibold">{name}</span>
        {figure !== null && (
          <>
            , <span className="whitespace-nowrap">{figure}</span>
          </>
        )}
      </span>
    </Hint>
  );
}

/** Between two company risks in a line: a faint middle dot. */
function RiskSeparator() {
  return <span className="mx-1.5 text-faint">·</span>;
}

/** Lower than a year ago — one of the five risk signs, so it wears their tag everywhere. */
export function RecoveringTag() {
  return <RiskSignTag sign="RECOVERING" />;
}

/**
 * The words, in a hint, for the two ways a stock's signs go unchecked — which of the two it is, `RiskSigns` decides.
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
      {companyInfo.riskSigns.map((sign, signIndex) => (
        <Fragment key={sign}>
          {signIndex > 0 && <RiskSeparator />}
          <RiskSignTag sign={sign} figure={showFigures ? RISK_SIGN_FIGURES[sign](figuresBehindTheSigns) : null} />
        </Fragment>
      ))}
    </>
  );
}

function NotChecked({ hint }: { hint: string }) {
  return (
    <Hint text={hint}>
      <span className="text-muted">Not checked</span>
    </Hint>
  );
}

/**
 * A stock's company risks in a line, each with the figure that raised it when `showFigures`: "No company risks" when it
 * has none — in grey, not green: having none is not a forecast —, "Not checked" when Finnhub never answered or gave none
 * of its figures, `notCheckedHints` saying which in its hint.
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
    return (
      <Hint text="None of the five rules raised a company risk">
        <span className="text-muted">No company risks</span>
      </Hint>
    );
  }
  return (
    <span>
      <RiskSignTagList companyInfo={companyInfo} yearReturnPercent={yearReturnPercent} showFigures={showFigures} />
    </span>
  );
}

/** Under a scanned stock's key figures: "Company risks", each with its figure, then the timing note when it is due. */
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
    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-[13px]">
      <LabeledValue label="Company risks">
        <RiskSigns
          companyInfo={companyInfo}
          yearReturnPercent={yearReturnPercent}
          showFigures
          notCheckedHints={notCheckedHints}
        />
      </LabeledValue>
      <TimingNoteWhenDue companyInfo={companyInfo} />
    </div>
  );
}
