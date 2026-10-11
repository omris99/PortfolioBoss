import { useState, type ReactNode } from 'react';
import {
  EMPTY_VALUE,
  formatDayCount,
  formatHoldingPeriod,
  formatMoney,
  formatQuantity,
  formatSignedMoney,
  formatSignedPercent,
  profitLossColorClass,
} from '../lib/format';
import type { ClosedPosition } from '../types/portfolio';
import { ClosedPositionTags } from './ClosedPositionsTable';
import { DrawerFrame, KeyFigure, KeyFigureExtra, KeyFigures, type DrawerTab } from './DrawerFrame';
import { hasSeveralInvestors, investorNameOf, useInvestors } from './InvestorsContext';
import { InvestorSplitTable, isShared, type SplitPosition } from './InvestorSplitTable';
import { ManualPositionDetails } from './ManualPositionForms';
import { ReadOnlyTradesPanel, TradesPanel } from './TradesPanel';
import { WarningBand } from './WarnMark';

type ClosedTab = 'details' | 'trades' | 'byInvestor';

/** "Technology · Investor Dana · Bought 2024-11-04 → sold 2026-06-18 · Commission 10.00" under the symbol. */
function sublineOf(closedPosition: ClosedPosition, investorName: string | null): string {
  return [
    closedPosition.sector ?? 'No sector',
    investorName === null ? null : `Investor ${investorName}`,
    `Bought ${closedPosition.openDate} → sold ${closedPosition.closeDate}`,
    `Commission ${formatMoney(closedPosition.commissions)}`,
  ]
    .filter((part) => part !== null)
    .join(' · ');
}

/** The line under the realized P&L: its percent and currency, or why there is none. */
function RealizedPnlExtra({ closedPosition }: { closedPosition: ClosedPosition }) {
  if (closedPosition.realizedPnl === null) {
    return (
      <KeyFigureExtra>{closedPosition.warning !== null ? 'sold more than bought' : 'a price is missing'}</KeyFigureExtra>
    );
  }
  return (
    <KeyFigureExtra colorClass={profitLossColorClass(closedPosition.realizedPnlPercent)}>
      {formatSignedPercent(closedPosition.realizedPnlPercent)} · {closedPosition.currency}
    </KeyFigureExtra>
  );
}

function ClosedKeyFigures({ closedPosition }: { closedPosition: ClosedPosition }) {
  return (
    <KeyFigures>
      <KeyFigure
        label="Qty sold"
        value={formatQuantity(closedPosition.quantity)}
        extra={
          closedPosition.remainingQuantity > 0 && (
            <KeyFigureExtra>{formatQuantity(closedPosition.remainingQuantity)} still held</KeyFigureExtra>
          )
        }
      />
      <KeyFigure label="Avg buy" value={formatMoney(closedPosition.averageBuyPrice)} />
      <KeyFigure label="Avg sell" value={formatMoney(closedPosition.averageSellPrice)} />
      <KeyFigure
        label="Realized P&L"
        value={closedPosition.realizedPnl === null ? EMPTY_VALUE : formatSignedMoney(closedPosition.realizedPnl)}
        valueColorClass={profitLossColorClass(closedPosition.realizedPnl)}
        extra={<RealizedPnlExtra closedPosition={closedPosition} />}
      />
      <KeyFigure
        label="Held"
        value={formatHoldingPeriod(closedPosition.holdingDays)}
        extra={<KeyFigureExtra>{formatDayCount(closedPosition.holdingDays)}</KeyFigureExtra>}
      />
    </KeyFigures>
  );
}

/**
 * A closed position's details over the Closed table, on the same frame as a holding's drawer with only the tabs that
 * apply. A holding's sold period has one: its trades, read-only, with a link to correct them in Positions. A manual
 * position has Details (editable, and Delete position), Trades (add, correct, delete) and, when shared, By investor.
 * `closedPositions` are every closed position, for the manual position's split by investor.
 */
export function ClosedDrawer({
  closedPosition,
  closedPositions,
  previousLabel,
  nextLabel,
  onPrevious,
  onNext,
  onClose,
  onDataChanged,
}: {
  closedPosition: ClosedPosition;
  closedPositions: ClosedPosition[];
  previousLabel: string;
  nextLabel: string;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
  onDataChanged: () => Promise<void>;
}) {
  const investors = useInvestors();
  const { manualPositionId, holdingId } = closedPosition;
  const tradesLabel = `Trades (${closedPosition.trades.length})`;
  const splitPosition: SplitPosition = { kind: 'manualPosition', currency: closedPosition.currency };
  const manualPositionsClosedPositions = closedPositions.filter(
    (otherClosedPosition) => manualPositionId !== null && otherClosedPosition.manualPositionId === manualPositionId,
  );
  const showsByInvestor = manualPositionId !== null && isShared(splitPosition, manualPositionsClosedPositions, investors);

  const tabs: DrawerTab<ClosedTab>[] =
    manualPositionId === null
      ? [{ key: 'trades', label: tradesLabel }]
      : [
          { key: 'details', label: 'Details' },
          { key: 'trades', label: tradesLabel },
          ...(showsByInvestor ? [{ key: 'byInvestor' as const, label: 'By investor' }] : []),
        ];
  const [activeTab, setActiveTab] = useState<ClosedTab>(tabs[0].key);

  const panelOf = (tab: ClosedTab): ReactNode => {
    // A holding's sold period: always the one read-only tab, its holding always known.
    if (manualPositionId === null) {
      return (
        holdingId !== null && (
          <ReadOnlyTradesPanel trades={closedPosition.trades} symbol={closedPosition.symbol} holdingId={holdingId} />
        )
      );
    }
    switch (tab) {
      case 'details':
        return (
          <ManualPositionDetails
            manualPositionId={manualPositionId}
            closedPosition={closedPosition}
            onDataChanged={onDataChanged}
          />
        );
      case 'trades':
        return (
          <TradesPanel
            owner={{
              kind: 'manualPosition',
              manualPositionId,
              symbol: closedPosition.symbol,
              trades: closedPosition.trades,
            }}
            onDataChanged={onDataChanged}
          />
        );
      case 'byInvestor':
        return <InvestorSplitTable position={splitPosition} closedPositions={manualPositionsClosedPositions} />;
    }
  };

  const investorName = hasSeveralInvestors(investors) ? investorNameOf(investors, closedPosition.investorId) : null;
  return (
    <DrawerFrame
      ariaLabel={`${closedPosition.symbol} closed position`}
      title={closedPosition.symbol}
      currency={closedPosition.currency}
      tags={<ClosedPositionTags closedPosition={closedPosition} />}
      subline={sublineOf(closedPosition, investorName)}
      figures={<ClosedKeyFigures closedPosition={closedPosition} />}
      warning={closedPosition.warning !== null && <WarningBand level="real">{closedPosition.warning}</WarningBand>}
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
