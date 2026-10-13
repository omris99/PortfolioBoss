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
import { AnalysisPanel, RatingChip, SentimentChip, TargetWithUpside, TrendChip } from './AnalysisPanel';
import { DrawerFrame, KeyFigure, KeyFigureExtra, KeyFigures, type DrawerTab } from './DrawerFrame';
import { HoldingWarningBands } from './HoldingWarnings';
import { accountOwnerOf, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable, isShared } from './InvestorSplitTable';
import { MomentumChip, MomentumPanel, passedCheckCount } from './Momentum';
import { SectorCell } from './SectorCell';
import { SignalDot, warningSignsOf } from './Signal';
import { TradesPanel } from './TradesPanel';
import type { WholeAccount } from './graphs/portfolioWeights';
import { WeightCalculatorPanel } from './weightCalculator/WeightCalculatorPanel';

export type HoldingTab = 'summary' | 'momentum' | 'analysis' | 'trades' | 'weight' | 'byInvestor';

/** A link's state that opens the drawer on a tab other than Summary: the Overview's weight bars open it on Weight. */
export interface OpenOnTab {
  openOnTab: HoldingTab;
}

/** The tab a link asked the drawer to open on, if any; the drawer checks that the holding has it. */
export function tabToOpenOn(state: unknown): HoldingTab | null {
  if (typeof state !== 'object' || state === null) return null;
  const { openOnTab } = state as Partial<OpenOnTab>;
  return typeof openOnTab === 'string' ? openOnTab : null;
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
 * A holding's details over the positions table (/positions/:holdingId): the key figures and its warning in the header,
 * then Summary · Momentum · Analysts & news · Trades · Weight · By investor — Weight only on an open holding, By
 * investor only when another investor is involved. It opens on `initialTab` when the holding has that tab, else on
 * Summary; the page gives it a new `key` per holding, so ‹ › start each holding there again. `closedPositions` are the
 * holding's own, of every investor; `wholeAccount` and `syncedAt` are for the weight calculator.
 */
export function HoldingDrawer({
  holding,
  closedPositions,
  estimatedAnalysisCost,
  wholeAccount,
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
  wholeAccount: WholeAccount | null;
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

  const tabs: DrawerTab<HoldingTab>[] = [
    { key: 'summary', label: 'Summary' },
    { key: 'momentum', label: 'Momentum' },
    { key: 'analysis', label: 'Analysts & news' },
    { key: 'trades', label: tradeCount === 0 ? 'Trades' : `Trades (${tradeCount})` },
    ...(holding.status === 'OPEN' ? [{ key: 'weight' as const, label: 'Weight' }] : []),
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
          <AnalysisPanel holding={holding} estimatedAnalysisCost={estimatedAnalysisCost} onDataChanged={onDataChanged} />
        );
      case 'trades':
        return <TradesPanel owner={position} onDataChanged={onDataChanged} />;
      case 'weight':
        return <WeightCalculatorPanel holding={holding} wholeAccount={wholeAccount} syncedAt={syncedAt} />;
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
      subline={<SectorCell holding={holding} onDataChanged={onDataChanged} />}
      figures={<HoldingKeyFigures holding={holding} />}
      warning={<HoldingWarningBands warnings={holding.warnings} />}
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
