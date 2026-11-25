import { useState, type ReactNode } from 'react';
import { ArrowRight } from 'lucide-react';
import {
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
import { TimingNoteWhenDue } from './companyInfo/TimingNote';
import {
  DrawerFrame,
  KeyFigure,
  KeyFigureExtra,
  KeyFigures,
  type DrawerNavigation,
  type DrawerTab,
} from './DrawerFrame';
import { SkeletonBar, SkeletonDot } from './firstLoad/SkeletonBar';
import { HOLDING_NOT_CHECKED_HINTS, HoldingSignsAndRisks } from './HoldingWarningSigns';
import { HoldingWarningBands } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable, isShared } from './InvestorSplitTable';
import { MomentumMark, MomentumPanel, passedCheckCount } from './Momentum';
import { sectorAndIndustryText } from './SectorCell';
import { SignalDot, SignalWords } from './Signal';
import { TradesPanel } from './TradesPanel';
import type { InvestmentsAndCash } from './graphs/investmentsAndCash';
import { WeightCalculatorPanel } from './weightCalculator/WeightCalculatorPanel';

export type HoldingTab = 'summary' | 'momentum' | 'analysis' | 'company' | 'trades' | 'weight' | 'byInvestor';

/** Each tab's name; Trades adds its count once the trades are known. */
const TAB_LABELS: Record<HoldingTab, string> = {
  summary: 'Summary',
  momentum: 'Momentum',
  analysis: 'Analysts & news',
  company: 'Company',
  trades: 'Trades',
  weight: 'Weight',
  byInvestor: 'By investor',
};

/** The five key figures' labels, the same while they load. */
const KEY_FIGURE_LABELS = {
  quantity: 'Qty',
  last: 'Last',
  marketValue: 'Market value',
  unrealizedPnl: 'Unrealized P&L',
  held: 'Held',
} as const;

/** The three summary cards' titles, the same while they load. */
const SUMMARY_CARD_TITLES = { momentum: 'Momentum', analysts: 'Analysts', news: 'News' } as const;

/** A tab under its name. */
function tabOf(key: HoldingTab): DrawerTab<HoldingTab> {
  return { key, label: TAB_LABELS[key] };
}

/** The drawer's name, what a screen reader announces when it opens: "NVDA details". */
export function holdingDrawerLabelOf(holding: Holding): string {
  return `${holding.symbol} details`;
}

/** What the analysts' target is on a holding: the median a scanned stock gets. */
const TARGET_RULE_FOR_A_HOLDING =
  'The median of 3 firms’ targets or more — each firm’s latest of the half year, from Dow Jones’s headlines —, ' +
  'against IB’s price at the last sync.';

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
      <KeyFigure
        label={KEY_FIGURE_LABELS.quantity}
        value={formatQuantity(holding.position)}
        extra={<QuantitySplitText holding={holding} />}
      />
      <KeyFigure label={KEY_FIGURE_LABELS.last} value={formatMoney(holding.marketPrice)} />
      <KeyFigure label={KEY_FIGURE_LABELS.marketValue} value={formatMoney(holding.marketValue)} />
      <KeyFigure
        label={KEY_FIGURE_LABELS.unrealizedPnl}
        value={formatSignedMoney(holding.unrealizedPnl)}
        valueColorClass={profitLossColorClass(holding.unrealizedPnl)}
        extra={
          <KeyFigureExtra colorClass={profitLossColorClass(holding.unrealizedPnlPercent)}>
            {formatSignedPercent(holding.unrealizedPnlPercent)}
          </KeyFigureExtra>
        }
      />
      <KeyFigure
        label={KEY_FIGURE_LABELS.held}
        value={formatHoldingPeriod(holding.holdingDays)}
        extra={holding.holdingDays !== null && <KeyFigureExtra>{formatDayCount(holding.holdingDays)}</KeyFigureExtra>}
      />
    </KeyFigures>
  );
}

/**
 * Under an open holding's key figures, everything that warns in one line: the Signal dot's warning signs and the company
 * risks, each group under its own label (round 6), then the timing note.
 */
function HoldingSignsLine({ holding }: { holding: Holding }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-[13px]">
      <HoldingSignsAndRisks holding={holding} />
      <TimingNoteWhenDue companyInfo={holding.companyInfo} />
    </div>
  );
}

// ── the Summary tab ─────────────────────────────────────────────────────────────────────────────

/**
 * Why the dot has its colour, in the one form of the signal in words — "Momentum weak · Analysts down" —, or why there
 * is none.
 */
function SignalReason({ holding }: { holding: Holding }) {
  if (holding.analysis !== null && holding.signal === null) {
    return <span className="text-unknown">No signal: fewer than two of the momentum, the analysts and the news are known</span>;
  }
  return <SignalWords holding={holding} />;
}

/** One of the three parts of the signal, with a link to its tab — none while the first load runs (`onDetails` `null`). */
function SummaryCard({
  title,
  onDetails,
  children,
}: {
  title: string;
  onDetails: (() => void) | null;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-lg border px-4 py-3">
      <span className="text-label text-muted">{title}</span>
      <div className="flex flex-1 flex-col items-start gap-1.5 text-[13px]">{children}</div>
      {onDetails !== null && (
        <button type="button" onClick={onDetails} className={`${LINK_BUTTON_CLASS} self-start`}>
          Details
          <ArrowRight size={12} />
        </button>
      )}
    </div>
  );
}

/** The signal's line over the three cards, the same while it loads. */
const SIGNAL_LINE_CLASS = 'mb-4 flex items-center gap-2';
/** The three summary cards side by side. */
const SUMMARY_CARDS_CLASS = 'grid grid-cols-3 gap-3';

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
      <p className={SIGNAL_LINE_CLASS}>
        <SignalDot holding={holding} />
        <SignalReason holding={holding} />
      </p>
      <div className={SUMMARY_CARDS_CLASS}>
        <SummaryCard title={SUMMARY_CARD_TITLES.momentum} onDetails={() => onTabChange('momentum')}>
          <MomentumMark momentum={momentum} />
          {momentum !== null && <span className="text-muted">{passedCheckCount(momentum)} of 5 checks pass</span>}
        </SummaryCard>
        <SummaryCard title={SUMMARY_CARD_TITLES.analysts} onDetails={() => onTabChange('analysis')}>
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
        <SummaryCard title={SUMMARY_CARD_TITLES.news} onDetails={() => onTabChange('analysis')}>
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
 * Summary; the page gives it a new `key` per holding, so ‹ › start each holding there again — inside the one
 * `DrawerShell` the page keeps open, which closes it. `closedPositions` are the holding's own, of every investor;
 * `weightsTabs`, `initialWeightsInvestorId` and `syncedAt` are for the weight calculator.
 */
export function HoldingDrawer({
  holding,
  closedPositions,
  estimatedAnalysisCost,
  weightsTabs,
  initialWeightsInvestorId,
  syncedAt,
  initialTab,
  navigation,
  onDataChanged,
}: {
  holding: Holding;
  closedPositions: ClosedPosition[];
  estimatedAnalysisCost: EstimatedAnalysisCost | null;
  weightsTabs: InvestmentsAndCash[];
  initialWeightsInvestorId: number | null;
  syncedAt: string;
  initialTab: HoldingTab | null;
  navigation: DrawerNavigation;
  onDataChanged: () => Promise<void>;
}) {
  const investors = useInvestors();
  const position = { kind: 'holding' as const, holding };
  const tradeCount = holding.trades.length;
  const isOpen = holding.status === 'OPEN';

  const tabs: DrawerTab<HoldingTab>[] = [
    tabOf('summary'),
    tabOf('momentum'),
    tabOf('analysis'),
    ...(isOpen ? [tabOf('company')] : []),
    { key: 'trades', label: tradeCount === 0 ? TAB_LABELS.trades : `${TAB_LABELS.trades} (${tradeCount})` },
    ...(isOpen ? [tabOf('weight')] : []),
    ...(isShared(position, closedPositions, investors) ? [tabOf('byInvestor')] : []),
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
      dot={<SignalDot holding={holding} size={12} />}
      title={holding.symbol}
      currency={holding.currency}
      tags={holding.status === 'CLOSED' && <span className={TAG_CLASS}>closed</span>}
      subline={sectorAndIndustryText(holding)}
      figures={<HoldingKeyFigures holding={holding} />}
      warning={
        <>
          <HoldingWarningBands warnings={holding.warnings} />
          {isOpen && <HoldingSignsLine holding={holding} />}
        </>
      }
      tabs={tabs}
      activeTab={activeTab}
      onTabChange={setActiveTab}
      navigation={navigation}
    >
      {panelOf(activeTab)}
    </DrawerFrame>
  );
}

/** The tabs an open holding has, which the first load shows: By investor comes only with the data. */
const TABS_OF_AN_OPEN_HOLDING: DrawerTab<HoldingTab>[] = (
  ['summary', 'momentum', 'analysis', 'company', 'trades', 'weight'] as const
).map(tabOf);

/**
 * The drawer of a holding opened from its address (/positions/12) during the first load (round 6): the same frame, with
 * grey bars for the dot, the symbol — the address has only the holding's id — and the sector, the five key figures with
 * their labels, the tabs, and Summary's signal line and three cards. ‹ › wait for the list; ✕ closes it.
 */
export function HoldingDrawerSkeleton() {
  return (
    <DrawerFrame
      dot={<SkeletonDot size={12} />}
      title={<SkeletonBar width={80} height={20} />}
      subline={<SkeletonBar width={180} height={12} />}
      figures={
        <KeyFigures>
          {Object.values(KEY_FIGURE_LABELS).map((label) => (
            <KeyFigure key={label} label={label} value={<SkeletonBar width={72} height={16} />} />
          ))}
        </KeyFigures>
      }
      tabs={TABS_OF_AN_OPEN_HOLDING}
      activeTab="summary"
      onTabChange={() => {}}
      navigation={null}
    >
      <div aria-busy="true">
        <p className={SIGNAL_LINE_CLASS}>
          <SkeletonDot size={10} />
          <SkeletonBar width={240} height={12} />
        </p>
        <div className={SUMMARY_CARDS_CLASS}>
          {Object.values(SUMMARY_CARD_TITLES).map((title) => (
            <SummaryCard key={title} title={title} onDetails={null}>
              <SkeletonBar width={72} height={20} />
              <SkeletonBar width="80%" height={12} />
              <SkeletonBar width="60%" height={12} />
            </SummaryCard>
          ))}
        </div>
      </div>
    </DrawerFrame>
  );
}
