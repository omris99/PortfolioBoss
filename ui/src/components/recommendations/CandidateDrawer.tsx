import { useState, type ReactNode } from 'react';
import { Check, X } from 'lucide-react';
import {
  EMPTY_VALUE,
  formatCorrelation,
  formatDate,
  formatMoney,
  formatPercent,
  formatSignedPercentOneDecimal,
} from '../../lib/format';
import { TAG_CLASS } from '../../lib/styles';
import type { Momentum } from '../../types/portfolio';
import type { Correlation, HotStock, HotStockLabel } from '../../types/recommendations';
import { DrawerFrame, KeyFigure, KeyFigureExtra, KeyFigures, type DrawerTab } from '../DrawerFrame';
import { useInvestors } from '../InvestorsContext';
import { MomentumPanel } from '../Momentum';
import {
  fromHighText,
  heldByText,
  isYoungStock,
  monthsInMarketText,
  NO_CORRELATION_REASON,
  sectorText,
} from './hotStockTexts';
import { InfoMark } from './InfoMark';
import { LIST_WORDS } from './ListHeading';
import { RecoveringTag } from './RiskSignTag';

type CandidateTab = 'summary' | 'momentum';

/** The other three tabs of the design — Analysts & news, Company, If I buy — come to their right with the analysis. */
const TABS: DrawerTab<CandidateTab>[] = [
  { key: 'summary', label: 'Summary' },
  { key: 'momentum', label: 'Momentum' },
];

/** The rule of the stock's list, beside its distance from the 50-day average. */
const LIST_RULES: Record<HotStockLabel, string> = {
  HOT: 'Hot: 0–15%',
  TOO_HOT: 'Too hot: above 15%',
  CORRECTING: 'Correcting: below it',
};

/** From 0.7 two stocks move together: the tick on each correlation's bar. */
const MOVES_TOGETHER_FROM = 0.7;

// ── the header ──────────────────────────────────────────────────────────────────────────────────

/** "+62.3%" with "12 months" under it — or "10 months" for a young stock —, and Recovering when below a year ago. */
function YearFigure({ stock }: { stock: HotStock }) {
  return (
    <KeyFigure
      label="Year"
      value={formatSignedPercentOneDecimal(stock.momentum.yearReturnPercent)}
      extra={
        <KeyFigureExtra>
          <span className="inline-flex items-center gap-1.5">
            {monthsInMarketText(stock.momentum)}
            {stock.recovering === true && <RecoveringTag />}
          </span>
        </KeyFigureExtra>
      }
    />
  );
}

/** The five figures under the title: last close, the 50-day average, the high, the year, the biggest fall. */
function CandidateKeyFigures({ stock }: { stock: HotStock }) {
  const momentum = stock.momentum;
  return (
    <KeyFigures>
      <KeyFigure
        label="Last close"
        value={formatMoney(momentum.lastClose)}
        extra={<KeyFigureExtra>close of {formatDate(momentum.asOf)}</KeyFigureExtra>}
      />
      <KeyFigure
        label={stock.label === 'CORRECTING' ? 'Under 50-day' : 'Above 50-day'}
        value={formatSignedPercentOneDecimal(stock.percentAboveSma50)}
        extra={<KeyFigureExtra>{LIST_WORDS[stock.label].title}</KeyFigureExtra>}
      />
      <KeyFigure
        label="From the high"
        value={fromHighText(momentum)}
        extra={
          <KeyFigureExtra>
            {stock.pulledBack ? <InfoMark kind="pulledBack" /> : `of ${formatMoney(momentum.high52Weeks)}`}
          </KeyFigureExtra>
        }
      />
      <YearFigure stock={stock} />
      <KeyFigure
        label="Biggest fall"
        value={formatPercent(momentum.maxDrawdownPercent)}
        extra={<KeyFigureExtra>this year</KeyFigureExtra>}
      />
    </KeyFigures>
  );
}

// ── the Summary tab ─────────────────────────────────────────────────────────────────────────────

function SummarySection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mb-5">
      <h3 className="mb-2 text-label text-muted">{title}</h3>
      {children}
    </section>
  );
}

/** A row of a bordered list: what it is on the left, its value (and anything after it) on the right. */
function SummaryRow({ label, children }: { label: ReactNode; children: ReactNode }) {
  return (
    <li className="flex min-h-10 items-center justify-between gap-3 px-3 py-2">
      <span className="text-muted">{label}</span>
      <span className="inline-flex items-center gap-2 text-right">{children}</span>
    </li>
  );
}

/** A fact, not a score: the tick is grey. */
function FactMark({ holds }: { holds: boolean | null }) {
  return holds === true ? (
    <Check size={16} className="text-muted" aria-label="yes" />
  ) : (
    <X size={16} className="text-muted" aria-label="no" />
  );
}

/** The three facts that put the stock in its list. */
function WhyItIsInItsList({ stock }: { stock: HotStock }) {
  const momentum = stock.momentum;
  const isAboveSma200 = momentum.sma200 === null ? null : momentum.lastClose > momentum.sma200;
  const distanceFromSma50 = Math.abs(stock.percentAboveSma50).toFixed(1);
  const sma50Fact =
    stock.percentAboveSma50 >= 0
      ? `${distanceFromSma50}% above its 50-day average`
      : `${distanceFromSma50}% under its 50-day average`;
  return (
    <ul className="divide-y rounded-lg border">
      <li className="flex min-h-10 items-center gap-2 px-3 py-2">
        <FactMark holds={isAboveSma200} />
        Above its 200-day average
      </li>
      <li className="flex min-h-10 items-center gap-2 px-3 py-2">
        <FactMark holds={momentum.sma50AboveSma200} />
        50-day above 200-day
      </li>
      <li className="flex min-h-10 items-center gap-2 px-3 py-2">
        <FactMark holds />
        {sma50Fact}
        <span className="ml-auto text-caption text-faint">{LIST_RULES[stock.label]}</span>
      </li>
    </ul>
  );
}

/** "+27.9% over 10 months, since 2025-12-20" for a young stock; "+62.3% over 12 months" otherwise. */
function yearDetailText(momentum: Momentum): string {
  const months = `over ${monthsInMarketText(momentum)}`;
  return isYoungStock(momentum) ? `${months}, since ${formatDate(momentum.firstCloseDate)}` : months;
}

function TheYear({ stock }: { stock: HotStock }) {
  const momentum = stock.momentum;
  const belowHigh = `${momentum.percentBelowHigh52Weeks.toFixed(1)}% below ${formatMoney(momentum.high52Weeks)}`;
  return (
    <ul className="divide-y rounded-lg border">
      <SummaryRow label="From the year’s high">
        {stock.pulledBack && <InfoMark kind="pulledBack" />}
        {momentum.percentBelowHigh52Weeks > 0 ? belowHigh : `at the high, ${formatMoney(momentum.high52Weeks)}`}
      </SummaryRow>
      <SummaryRow label="Year">
        {stock.recovering === true && <RecoveringTag />}
        <span>
          {formatSignedPercentOneDecimal(momentum.yearReturnPercent)}{' '}
          <span className="text-muted">{yearDetailText(momentum)}</span>
        </span>
      </SummaryRow>
      <SummaryRow label="Biggest fall">{formatPercent(momentum.maxDrawdownPercent)}</SummaryRow>
    </ul>
  );
}

function TheStock({ stock }: { stock: HotStock }) {
  const investors = useInvestors();
  return (
    <ul className="divide-y rounded-lg border">
      <SummaryRow label="Sector (IB)">{sectorText(stock)}</SummaryRow>
      <SummaryRow label="Exchange">{stock.exchange}</SummaryRow>
      <SummaryRow label="In the portfolio">{heldByText(stock, investors) || 'Not held'}</SummaryRow>
    </ul>
  );
}

/** A 0–1 bar, with a tick at 0.7 where moving together starts; a negative correlation draws no bar. */
function CorrelationBar({ coefficient }: { coefficient: number }) {
  const filledPercent = Math.max(0, Math.min(1, coefficient)) * 100;
  return (
    <span aria-hidden="true" className="relative h-1.5 flex-1 rounded-full bg-subtle">
      <span
        className="absolute inset-y-0 left-0 rounded-full bg-[var(--color-text-faint)]"
        style={{ width: `${filledPercent}%` }}
      />
      <span className="absolute -top-[3px] h-3 w-px bg-line-strong" style={{ left: `${MOVES_TOGETHER_FROM * 100}%` }} />
    </span>
  );
}

function CorrelationRow({ correlation }: { correlation: Correlation }) {
  return (
    <li className="flex min-h-10 items-center gap-3 px-3 py-2">
      <span className="w-14 font-semibold">{correlation.symbol}</span>
      <CorrelationBar coefficient={correlation.coefficient} />
      <span className="w-12 text-right">{formatCorrelation(correlation.coefficient)}</span>
      <span className="w-[118px]">{correlation.movesTogether && <InfoMark kind="movesTogether" />}</span>
    </li>
  );
}

function MovesWithYourHoldings({ correlations }: { correlations: Correlation[] }) {
  if (correlations.length === 0) {
    return <p className="text-muted">{EMPTY_VALUE} {NO_CORRELATION_REASON}</p>;
  }
  return (
    <>
      <ul className="divide-y rounded-lg border">
        {correlations.map((correlation) => (
          <CorrelationRow key={correlation.holdingId} correlation={correlation} />
        ))}
      </ul>
      <p className="mt-2 text-caption text-faint">
        1 = they move exactly together, 0 = unrelated. From 0.7 a purchase adds little spread to what you hold.
      </p>
    </>
  );
}

function SummaryPanel({ stock }: { stock: HotStock }) {
  return (
    <>
      <SummarySection title={`Why it is ${LIST_WORDS[stock.label].title.toLowerCase()}`}>
        <WhyItIsInItsList stock={stock} />
      </SummarySection>
      <SummarySection title="The year">
        <TheYear stock={stock} />
      </SummarySection>
      <SummarySection title="The stock">
        <TheStock stock={stock} />
      </SummarySection>
      <SummarySection title="Moves with your holdings">
        <MovesWithYourHoldings correlations={stock.correlations} />
      </SummarySection>
    </>
  );
}

// ── the drawer ──────────────────────────────────────────────────────────────────────────────────

/**
 * A scanned stock's drawer (/recommendations/:candidateId) — the same frame as a holding's. ‹ › step through Hot, Too
 * hot and Correcting in that order; ✕, Esc or the veil close it.
 */
export function CandidateDrawer({
  stock,
  previousSymbol,
  nextSymbol,
  onPrevious,
  onNext,
  onClose,
}: {
  stock: HotStock;
  previousSymbol: string;
  nextSymbol: string;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
}) {
  const investors = useInvestors();
  const [activeTab, setActiveTab] = useState<CandidateTab>('summary');
  const heldBy = heldByText(stock, investors);
  return (
    <DrawerFrame
      ariaLabel={`${stock.symbol}, ${LIST_WORDS[stock.label].title}`}
      title={stock.symbol}
      currency="USD"
      tags={
        <>
          <span className={TAG_CLASS}>{LIST_WORDS[stock.label].title}</span>
          {heldBy !== '' && <span className={TAG_CLASS}>In the portfolio: {heldBy}</span>}
        </>
      }
      subline={`${stock.companyName ?? EMPTY_VALUE} · ${sectorText(stock)}`}
      figures={<CandidateKeyFigures stock={stock} />}
      tabs={TABS}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      previousLabel={previousSymbol}
      nextLabel={nextSymbol}
      onPrevious={onPrevious}
      onNext={onNext}
      onClose={onClose}
    >
      {activeTab === 'summary' ? <SummaryPanel stock={stock} /> : <MomentumPanel momentum={stock.momentum} />}
    </DrawerFrame>
  );
}
