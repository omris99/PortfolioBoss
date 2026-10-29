import { EMPTY_VALUE, formatSignedPercentOneDecimal } from '../../lib/format';
import type { HotStock, RiskSign } from '../../types/recommendations';

interface RiskSignWords {
  name: string;
  /** The rule that raises it, in words — which numbers raise a sign is the API's, these only describe it. */
  rule: string;
}

/** The five risk signs (RECOMMENDATIONS_TODO.md, decision 5), in the brief's words, sentence case. */
export const RISK_SIGN_WORDS: Record<RiskSign, RiskSignWords> = {
  LOSES_MONEY: { name: 'Losing money', rule: 'a net margin below 0 over the last 12 months' },
  HIGH_DEBT: { name: 'High debt', rule: 'debt more than twice the equity' },
  EXPENSIVE: { name: 'Expensive', rule: 'a P/E above 50' },
  REVENUE_FALLING: { name: 'Revenue falling', rule: 'less revenue over the last 12 months than the 12 before' },
  RECOVERING: {
    name: 'Recovering',
    rule: 'lower than a year ago. After a crash the averages sink with the price, so a rebound looks like a trend fast.',
  },
};

/** "P/E 52", "debt 2.4× equity": the figure that raised the sign; `null` when the API sent none. */
const RISK_SIGN_FIGURES: Record<RiskSign, (stock: HotStock) => string | null> = {
  LOSES_MONEY: (stock) =>
    stock.companyFacts?.netMarginPercent == null
      ? null
      : `net margin ${formatSignedPercentOneDecimal(stock.companyFacts.netMarginPercent)}`,
  HIGH_DEBT: (stock) =>
    stock.companyFacts?.debtToEquity == null ? null : `debt ${stock.companyFacts.debtToEquity.toFixed(1)}× equity`,
  EXPENSIVE: (stock) =>
    stock.companyFacts?.peRatio == null ? null : `P/E ${Math.round(stock.companyFacts.peRatio)}`,
  REVENUE_FALLING: (stock) =>
    stock.companyFacts?.revenueGrowthPercent == null
      ? null
      : `revenue ${formatSignedPercentOneDecimal(stock.companyFacts.revenueGrowthPercent)}`,
  RECOVERING: (stock) =>
    stock.momentum.yearReturnPercent === null
      ? null
      : `year ${formatSignedPercentOneDecimal(stock.momentum.yearReturnPercent)}`,
};

export function riskSignFigureOf(sign: RiskSign, stock: HotStock): string | null {
  return RISK_SIGN_FIGURES[sign](stock);
}

/**
 * One of the five risk signs, in the warning-sign tone, in sentence case — never capitals. With `figure`, the figure
 * that raised it follows: "Expensive · P/E 52". Its hover names the rule it broke.
 */
export function RiskSignTag({ sign, figure = null }: { sign: RiskSign; figure?: string | null }) {
  const { name, rule } = RISK_SIGN_WORDS[sign];
  return (
    <span
      title={`${name} · a risk sign: ${figure === null ? '' : `${figure}, `}${rule}`}
      className="inline-flex h-[18px] items-center whitespace-nowrap rounded-sm bg-negative-soft px-[5px] text-[11px] font-medium text-negative"
    >
      {name}
      {figure !== null && <span className="font-normal">&nbsp;· {figure}</span>}
    </span>
  );
}

/** Lower than a year ago — one of the five risk signs, so it wears their tag everywhere. */
export function RecoveringTag() {
  return <RiskSignTag sign="RECOVERING" />;
}

const NOT_CHECKED_HINT =
  'Finnhub gave none of the company’s figures, so four of the five signs were never checked — and it can’t be picked.';

/**
 * A stock's risk signs in a row, each with its figure when `showFigures`: "No risk signs" when it has none — muted, not
 * green: having none is not a forecast —, "Not checked" without Finnhub's figures, "—" while never analyzed.
 */
export function RiskSigns({ stock, showFigures }: { stock: HotStock; showFigures: boolean }) {
  if (stock.analysis === null) {
    return <span title="Not analyzed yet: the signs come with the analysis">{EMPTY_VALUE}</span>;
  }
  if (stock.riskSigns === null) {
    return (
      <span title={NOT_CHECKED_HINT} className="text-muted">
        Not checked
      </span>
    );
  }
  if (stock.riskSigns.length === 0) return <span className="text-muted">No risk signs</span>;
  return (
    <span className="inline-flex flex-wrap gap-1">
      {stock.riskSigns.map((sign) => (
        <RiskSignTag key={sign} sign={sign} figure={showFigures ? riskSignFigureOf(sign, stock) : null} />
      ))}
    </span>
  );
}
