import { useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { idFromAddress, OPENED_FROM_TABLE, wasOpenedFromTable } from '../lib/drawerAddress';
import { openHoldingsOf } from '../lib/holdings';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';
import { AnalyzeEveryHoldingControl } from '../components/AnalyzeControls';
import { ColumnsMenu, useVisibleColumnKeys } from '../components/ColumnsMenu';
import { EmptyBox } from '../components/EmptyBox';
import { weightsTabsOf } from '../components/graphs/investmentsAndCash';
import { HoldingDrawer, tabToOpenOn, weightsInvestorIdToOpenOn } from '../components/HoldingDrawer';
import { needsAttention } from '../components/HoldingWarnings';
import { PageHeader } from '../components/PageHeader';
import { DEFAULT_SORT, POSITION_COLUMNS, PositionsTable, sortHoldings, type SortState } from '../components/PositionsTable';
import { Switch } from '../components/Switch';
import { WarnMark } from '../components/WarnMark';

/**
 * "2 holdings need attention", and "(1 closed, hidden)" while the closed ones are hidden; nothing when none does. It
 * counts every holding, not only the shown ones: "closed without a sell" is only ever on a closed holding.
 */
function AttentionLine({ allHoldings, showsClosed }: { allHoldings: Holding[]; showsClosed: boolean }) {
  const holdingsNeedingAttention = allHoldings.filter(needsAttention);
  if (holdingsNeedingAttention.length === 0) return null;
  const count = holdingsNeedingAttention.length;
  const hiddenCount = showsClosed
    ? 0
    : holdingsNeedingAttention.filter((holding) => holding.status === 'CLOSED').length;
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-[13px]">
      <WarnMark level="real" />
      <span className="font-medium text-warn-real">
        {count === 1 ? '1 holding needs attention' : `${count} holdings need attention`}
      </span>
      {hiddenCount > 0 && <span className="text-muted">({hiddenCount} closed, hidden)</span>}
    </span>
  );
}

/** The row `step` away from `holdingId` in the table's order, round the ends. */
function neighbourOf(rows: Holding[], holdingId: number, step: number): Holding {
  const index = rows.findIndex((holding) => holding.id === holdingId);
  return rows[(index + step + rows.length) % rows.length];
}

/**
 * The Positions page (/positions): one card with the toolbar — Analyze, the attention line, Show closed, Columns — and
 * the table; at /positions/12, holding 12's drawer over it. The open holding lives in the address, so the Overview and
 * the closed positions can link straight to it and a refresh keeps it open.
 */
export function PositionsPage({
  snapshot,
  onDataChanged,
}: {
  snapshot: PortfolioSnapshot;
  onDataChanged: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const openHoldingId = idFromAddress(useParams().holdingId);
  const [showClosed, setShowClosed] = useState(false);
  const [sortState, setSortState] = useState<SortState>(DEFAULT_SORT);
  const [visibleColumnKeys, setVisibleColumnKeys] = useVisibleColumnKeys(POSITION_COLUMNS);

  const allHoldings = snapshot.holdings;
  const openHoldings = openHoldingsOf(allHoldings);
  const closedCount = allHoldings.length - openHoldings.length;
  const openHolding = allHoldings.find((holding) => holding.id === openHoldingId) ?? null;
  // A link to a closed holding shows the closed holdings, or its row would be hidden behind its drawer.
  const showsClosed = showClosed || openHolding?.status === 'CLOSED';
  const rows = sortHoldings(showsClosed ? allHoldings : openHoldings, sortState);

  const openDrawer = (holdingId: number) =>
    navigate(`/positions/${holdingId}`, { state: OPENED_FROM_TABLE });
  const closeDrawer = () => {
    if (wasOpenedFromTable(location.state)) {
      navigate(-1);
    } else {
      navigate('/positions', { replace: true });
    }
  };
  // ‹ › replace the address rather than adding a step per holding to the browser's history.
  const stepTo = (holding: Holding) => navigate(`/positions/${holding.id}`, { replace: true, state: location.state });

  /** Hiding the closed holdings closes the drawer too, when it is one of theirs. */
  const handleShowClosedChange = (shows: boolean) => {
    setShowClosed(shows);
    if (!shows && openHolding?.status === 'CLOSED') navigate('/positions', { replace: true });
  };

  return (
    <>
      <PageHeader title="Positions">
        <span className="text-caption text-faint">
          {openHoldings.length === 1 ? '1 open holding' : `${openHoldings.length} open holdings`}
        </span>
      </PageHeader>

      {allHoldings.length === 0 ? (
        <EmptyBox message="No positions reported." />
      ) : (
        <section className={CARD_CLASS}>
          <div className="flex flex-wrap items-center gap-4 border-b px-5 py-3">
            {openHoldings.length > 0 && (
              <AnalyzeEveryHoldingControl
                stockCount={openHoldings.length}
                estimatedAnalysisCost={snapshot.estimatedAnalysisCost}
                onDataChanged={onDataChanged}
              />
            )}
            <span className="flex-1" />
            <AttentionLine allHoldings={allHoldings} showsClosed={showsClosed} />
            {closedCount > 0 && (
              <Switch checked={showsClosed} onChange={handleShowClosedChange}>
                Show closed ({closedCount})
              </Switch>
            )}
            <ColumnsMenu columns={POSITION_COLUMNS} visibleKeys={visibleColumnKeys} onChange={setVisibleColumnKeys} />
          </div>
          {rows.length === 0 ? (
            <p className="px-5 py-10 text-center text-muted">No open positions reported.</p>
          ) : (
            <PositionsTable
              rows={rows}
              visibleColumnKeys={visibleColumnKeys}
              sortState={sortState}
              onSort={setSortState}
              selectedHoldingId={openHoldingId}
              onOpen={openDrawer}
              onDataChanged={onDataChanged}
            />
          )}
        </section>
      )}

      {openHolding !== null && (
        <HoldingDrawer
          key={openHolding.id}
          holding={openHolding}
          closedPositions={snapshot.closedPositions.filter((closedPosition) => closedPosition.holdingId === openHolding.id)}
          estimatedAnalysisCost={snapshot.estimatedAnalysisCost}
          weightsTabs={weightsTabsOf(openHoldings, snapshot.totalCashValue, snapshot.investors)}
          initialWeightsInvestorId={weightsInvestorIdToOpenOn(location.state)}
          syncedAt={snapshot.asOf}
          initialTab={tabToOpenOn(location.state)}
          previousLabel={neighbourOf(rows, openHolding.id, -1).symbol}
          nextLabel={neighbourOf(rows, openHolding.id, 1).symbol}
          onPrevious={() => stepTo(neighbourOf(rows, openHolding.id, -1))}
          onNext={() => stepTo(neighbourOf(rows, openHolding.id, 1))}
          onClose={closeDrawer}
          onDataChanged={onDataChanged}
        />
      )}
    </>
  );
}
