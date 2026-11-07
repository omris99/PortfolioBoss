import { useState, type ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import {
  EMPTY_VALUE,
  formatHoldingPeriod,
  formatDayCount,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import { LINK_BUTTON_CLASS, TAG_CLASS } from '../lib/styles';
import type { ClosedPosition, EstimatedAnalysisCost, Holding } from '../types/portfolio';
import { HoldingAnalysisPanel, RatingChip, SentimentChip, TargetWithUpside, TrendChip } from './AnalysisPanel';
import { AnalystTargetSection } from './AnalystTarget';
import { CompanyPanel } from './companyInfo/CompanyPanel';
import { notCheckedHintOf, RiskSignTagList, type NotCheckedHints } from './companyInfo/RiskSigns';
import { TimingNoteWhenDue } from './companyInfo/TimingNote';
import { DrawerFrame, KeyFigure, KeyFigureExtra, KeyFigures, type DrawerTab } from './DrawerFrame';
import { HoldingWarningBands } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable, isShared } from './InvestorSplitTable';
import { MomentumChip, MomentumPanel, passedCheckCount } from './Momentum';
import { sectorAndIndustryText } from './SectorCell';
import { SignalDot, SignalWarningSignTags, warningSignsOf } from './Signal';
import { TradesPanel } from './TradesPanel';
import type { InvestmentsAndCash } from './graphs/investmentsAndCash';
import { WeightCalculatorPanel } from './weightCalculator/WeightCalculatorPanel';

export type HoldingTab = 'summary' | 'momentum' | 'analysis' | 'company' | 'trades' | 'weight' | 'byInvestor';

/**
 * Why a holding's risk signs are unknown: Finnhub is asked on every run that reaches TWS, about the holdings in dollars
 * only (MY_HOLDINGS_TODO.md, decision 10).
 */
const HOLDING_NOT_CHECKED_HINTS: NotCheckedHints = {
  finnhubDidNotAnswer:
    'No answer from Finnhub: it is asked on every run that reaches TWS, about the holdings in dollars only — on ' +
    'another exchange IB’s symbol isn’t Finnhub’s —, and only when its key is set.',
  finnhubHadNoFigures: 'Finnhub has none of the company’s four figures, so the risk signs could not be checked.',
};

/** What the analysts' target is on a holding: the median a scanned stock gets. */
const TARGET_RULE_FOR_A_HOLDING =
  'The median of 3 firms’ targets or more — each firm’s latest of the half year, from Dow Jones’s headlines —, ' +
  'against IB’s price at the last sync.';

const NO_WARNING_SIGN_HINT = 'No warning signs: none of the Signal dot’s, and none of the five risk signs';

/** A link's state that opens the drawer on a tab other than Summary: the Overview's weight bars open it on Weight. */
export interface OpenOnTab {
  openOnTab: HoldingTab;
  /** On Weight, whose weights to show first: the investor whose bar was clicked; `null` or missing for Portfolio. */
  weightsInvestorId?: number | null;
}

function openOnTabOf(state: unknown): Partial<OpenOnTab> {
  return typeof state === 'object' && state !== null ? (state as Partial<OpenOnTab>) : {};
}

/** The tab a link asked the drawer to open on, if any; the drawer checks that the holding has it. */
export function tabToOpenOn(state: unknown): HoldingTab | null {
  const { openOnTab } = openOnTabOf(state);
  return typeof openOnTab === 'string' ? openOnTab : null;
}

/** The investor whose weights a link asked the Weight tab to show first; `null` for Portfolio. */
export function weightsInvestorIdToOpenOn(state: unknown): number | null {
  const { weightsInvestorId } = openOnTabOf(state);
  return typeof weightsInvestorId === 'number' ? weightsInvestorId : null;
}

// ── the header's figures ────────────────────────────────────────────────────────────────────────

/** "Me 20 · Dana 20" under the quantity, while another investor holds part of it. */
function QuantitySplitText({ holding }: { holding: Holding }) {
  const investors = useInvestors();
  const accountOwner = accountOwnerOf(investors);
  const othersHoldSome = holding.investorQuantities.some((part) => part.investorId !== accountOwner?.id);
  if (!othersHoldSome) return null;
  const split = holding.investorQuantities
    .map((part) => `${investorNameOf(investors, part.investorId)} ${formatQuantity(part.quantity)}`)
    .join(' · ');
  return <KeyFigureExtra>{split}</KeyFigureExtra>;
}

function HoldingKeyFigures({ holding }: { holding: Holding }) {
  return (
    <KeyFigures>
      <KeyFigure label="Qty" value={formatQuantity(holding.position)} extra={<QuantitySplitText holding={holding} />} />
      <KeyFigure label="Last" value={formatMoney(holding.marketPrice)} />
      <KeyFigure label="Market value" value={formatMoney(holding.marketValue)} />
      <KeyFigure
        label="Unrealized P&L"
        value={formatSignedMoney(holding.unrealizedPnl)}
        valueColorClass={profitLossColorClass(holding.unrealizedPnl)}
        extra={
          <KeyFigureExtra colorClass={profitLossColorClass(holding.unrealizedPnlPercent)}>
            {formatSignedPercent(holding.unrealizedPnlPercent)}
          </KeyFigureExtra>
        }
      />
      <KeyFigure
        label="Held"
        value={formatHoldingPeriod(holding.holdingDays)}
        extra={holding.holdingDays !== null && <KeyFigureExtra>{formatDayCount(holding.holdingDays)}</KeyFigureExtra>}
      />
    </KeyFigures>
  );
}

/**
 * Under an open holding's key figures, everything that warns in one row (MY_HOLDINGS_TODO.md, session 3): the Signal
 * dot's warning signs, then the risk signs with their figures — "—" when there are none —, whether the risk signs
 * could be checked, and the timing note. Shown together only: the dot still counts its own three.
 */
function HoldingWarningSignsLine({ holding }: { holding: Holding }) {
  const companyInfo = holding.companyInfo;
  const riskSignsNotCheckedHint = notCheckedHintOf(companyInfo, HOLDING_NOT_CHECKED_HINTS);
  const warningSignCount = warningSignsOf(holding).length + (companyInfo?.riskSigns ?? []).length;
  return (
    <div className="flex flex-wrap items-center gap-1.5 text-caption">
      <span className="text-muted">Warning signs</span>
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
      <TimingNoteWhenDue companyInfo={companyInfo} />
    </div>
  );
}

// ── the Summary tab ─────────────────────────────────────────────────────────────────────────────

/** "Signal YELLOW: news negative" — why the dot has its colour — or why there is none. */
function SignalReason({ holding }: { holding: Holding }) {
  if (holding.analysis === null) return <span className="text-unknown">Not analyzed yet</span>;
  if (holding.signal === null) {
    return <span className="text-unknown">No signal: fewer than two of the momentum, the analysts and the news are known</span>;
  }
  const warningSigns = warningSignsOf(holding);
  return (
    <span>
      <b className="font-semibold">Signal {holding.signal}</b>:{' '}
      {warningSigns.length === 0 ? 'no warning sign' : warningSigns.join(', ').toLowerCase()}
    </span>
  );
}

/** One of the three parts of the signal, with a link to its tab. */
function SummaryCard({ title, onDetails, children }: { title: string; onDetails: () => void; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border px-4 py-3">
      <span className="text-label text-muted">{title}</span>
      <div className="flex flex-1 flex-col items-start gap-1.5 text-[13px]">{children}</div>
      <button type="button" onClick={onDetails} className={`${LINK_BUTTON_CLASS} self-start`}>
        Details
        <ArrowRight size={12} />
      </button>
    </div>
  );
}

function NotAnalyzedYet() {
  return <span className="text-unknown">Not analyzed yet</span>;
}

/**
 * The drawer's first tab: why the dot has its colour, then the momentum, the analysts and the news side by side, each
 * with a link to its own tab.
 */
function SignalSummary({ holding, onTabChange }: { holding: Holding; onTabChange: (tab: HoldingTab) => void }) {
  const { momentum, analysis } = holding;
  return (
    <div>
      <p className="mb-4 flex items-center gap-2">
        <SignalDot holding={holding} />
        <SignalReason holding={holding} />
      </p>
      <div className="grid grid-cols-3 gap-3">
        <SummaryCard title="Momentum" onDetails={() => onTabChange('momentum')}>
          <MomentumChip momentum={momentum} />
          {momentum !== null && <span className="text-muted">{passedCheckCount(momentum)} of 5 checks pass</span>}
        </SummaryCard>
        <SummaryCard title="Analysts" onDetails={() => onTabChange('analysis')}>
          {analysis === null ? (
            <NotAnalyzedYet />
          ) : (
            <>
              {analysis.consensus === null ? (
                <span className="text-unknown">No consensus</span>
              ) : (
                <>
                  <RatingChip rating={analysis.consensus.rating} />
                  <TargetWithUpside consensus={analysis.consensus} />
                </>
              )}
              <span className="inline-flex items-center gap-1.5">
                <span className="text-muted">Trend</span>
                <TrendChip trend={analysis.analystTrend} />
              </span>
            </>
          )}
        </SummaryCard>
        <SummaryCard title="News" onDetails={() => onTabChange('analysis')}>
          {analysis === null ? (
            <NotAnalyzedYet />
          ) : (
            <>
              <SentimentChip sentiment={analysis.sentiment} />
              {analysis.sentimentReason !== null && <span className="text-muted">{analysis.sentimentReason}</span>}
            </>
          )}
        </SummaryCard>
      </div>
    </div>
  );
}

// ── the drawer ──────────────────────────────────────────────────────────────────────────────────

/**
 * A holding's details over the positions table (/positions/:holdingId): the key figures, its warning and — open — its
 * warning signs in the header, then Summary · Momentum · Analysts & news · Company · Trades · Weight · By investor —
 * Company and Weight only on an open holding (a closed one's company info and analysts' target are no longer read), By
 * investor only when another investor is involved. It opens on `initialTab` when the holding has that tab, else on
 * Summary; the page gives it a new `key` per holding, so ‹ › start each holding there again. `closedPositions` are the
 * holding's own, of every investor; `weightsTabs`, `initialWeightsInvestorId` and `syncedAt` are for the weight
 * calculator.
 */
export function HoldingDrawer({
  holding,
  closedPositions,
  estimatedAnalysisCost,
  weightsTabs,
  initialWeightsInvestorId,
  syncedAt,
  initialTab,
  previousLabel,
  nextLabel,
  onPrevious,
  onNext,
  onClose,
  onDataChanged,
}: {
  holding: Holding;
  closedPositions: ClosedPosition[];
  estimatedAnalysisCost: EstimatedAnalysisCost | null;
  weightsTabs: InvestmentsAndCash[];
  initialWeightsInvestorId: number | null;
  syncedAt: string;
  initialTab: HoldingTab | null;
  previousLabel: string;
  nextLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
  onDataChanged: () => Promise<void>;
}) {
  const investors = useInvestors();
  const position = { kind: 'holding' as const, holding };
  const tradeCount = holding.trades.length;
  const isOpen = holding.status === 'OPEN';

  const tabs: DrawerTab<HoldingTab>[] = [
    { key: 'summary', label: 'Summary' },
    { key: 'momentum', label: 'Momentum' },
    { key: 'analysis', label: 'Analysts & news' },
    ...(isOpen ? [{ key: 'company' as const, label: 'Company' }] : []),
    { key: 'trades', label: tradeCount === 0 ? 'Trades' : `Trades (${tradeCount})` },
    ...(isOpen ? [{ key: 'weight' as const, label: 'Weight' }] : []),
    ...(isShared(position, closedPositions, investors) ? [{ key: 'byInvestor' as const, label: 'By investor' }] : []),
  ];
  const hasInitialTab = tabs.some((tab) => tab.key === initialTab);
  const [activeTab, setActiveTab] = useState<HoldingTab>(hasInitialTab && initialTab !== null ? initialTab : 'summary');

  const panelOf = (tab: HoldingTab): ReactNode => {
    switch (tab) {
      case 'summary':
        return <SignalSummary holding={holding} onTabChange={setActiveTab} />;
      case 'momentum':
        return <MomentumPanel momentum={holding.momentum} />;
      case 'analysis':
        return (
          <>
            {isOpen && <AnalystTargetSection analystTarget={holding.analystTarget} ruleText={TARGET_RULE_FOR_A_HOLDING} />}
            <HoldingAnalysisPanel
              holding={holding}
              estimatedAnalysisCost={estimatedAnalysisCost}
              onDataChanged={onDataChanged}
            />
          </>
        );
      case 'company':
        return <CompanyPanel companyInfo={holding.companyInfo} notCheckedHints={HOLDING_NOT_CHECKED_HINTS} />;
      case 'trades':
        return <TradesPanel owner={position} onDataChanged={onDataChanged} />;
      case 'weight':
        return (
          <WeightCalculatorPanel
            holding={holding}
            tabs={weightsTabs}
            initialInvestorId={initialWeightsInvestorId}
            syncedAt={syncedAt}
          />
        );
      case 'byInvestor':
        return <InvestorSplitTable position={position} closedPositions={closedPositions} />;
    }
  };

  return (
    <DrawerFrame
      ariaLabel={`${holding.symbol} details`}
      dot={<SignalDot holding={holding} size={12} />}
      title={holding.symbol}
      currency={holding.currency}
      tags={holding.status === 'CLOSED' && <span className={TAG_CLASS}>closed</span>}
      subline={sectorAndIndustryText(holding)}
      figures={<HoldingKeyFigures holding={holding} />}
      warning={
        <>
          <HoldingWarningBands warnings={holding.warnings} />
          {isOpen && <HoldingWarningSignsLine holding={holding} />}
        </>
      }
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      previousLabel={previousLabel}
      nextLabel={nextLabel}
      onPrevious={onPrevious}
      onNext={onNext}
      onClose={onClose}
    >
      {panelOf(activeTab)}
    </DrawerFrame>
  );
}
