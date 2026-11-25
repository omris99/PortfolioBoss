import { useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { useDocumentTitle } from '../hooks/useDocumentTitle';
import { idFromAddress, OPENED_FROM_TABLE, wasOpenedFromTable } from '../lib/drawerAddress';
import { openHoldingsOf } from '../lib/holdings';
import { CARD_CLASS } from '../lib/styles';
import type { Holding, PortfolioSnapshot } from '../types/portfolio';
import { AnalyzeEveryHoldingControl } from '../components/AnalyzeControls';
import { ColumnsMenu, useVisibleColumnKeys } from '../components/ColumnsMenu';
import { DrawerShell } from '../components/DrawerShell';
import { EmptyBox } from '../components/EmptyBox';
import { SkeletonBar } from '../components/firstLoad/SkeletonBar';
import { weightsTabsOf } from '../components/graphs/investmentsAndCash';
import {
  HoldingDrawer,
  holdingDrawerLabelOf,
  HoldingDrawerSkeleton,
  tabToOpenOn,
  weightsInvestorIdToOpenOn,
} from '../components/HoldingDrawer';
import { needsAttention } from '../components/HoldingWarnings';
import { PageHeader } from '../components/PageHeader';
import {
  DEFAULT_SORT,
  POSITION_COLUMNS,
  PositionsTable,
  PositionsTableSkeleton,
  sortHoldings,
  type SortState,
} from '../components/PositionsTable';
import { Switch } from '../components/Switch';
import { WarnMark } from '../components/WarnMark';

/** The drawer's name while the first load runs: its holding's symbol comes with the data. */
const DRAWER_LABEL_WHILE_LOADING = 'Holding details';

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

/** "8 open holdings" under the title, a grey bar while it loads. */
function openHoldingsCountText(snapshot: PortfolioSnapshot | null): ReactNode {
  if (snapshot === null) return <SkeletonBar width={110} height={12} />;
  const openHoldingCount = openHoldingsOf(snapshot.holdings).length;
  return openHoldingCount === 1 ? '1 open holding' : `${openHoldingCount} open holdings`;
}

/** The card's top row: what acts on the table at its left, what filters and shows it at its right. */
function Toolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-4 border-b px-5 py-3">{children}</div>;
}

/**
 * The Positions page (/positions): one card with the toolbar — Analyze, the attention line, Show closed, Columns — and
 * the table; at /positions/12, holding 12's drawer over it. The open holding lives in the address, so the Overview and
 * the closed positions can link straight to it and a refresh keeps it open.
 *
 * `snapshot` is `null` during the first load (round 6): the card then shows its head and grey rows, and a drawer opened
 * from the address its frame with grey bars. The drawer keeps one shell from then on — the data replaces only what is
 * inside it, and ‹ › only the holding —, so it slides in once and leaves once.
 */
export function PositionsPage({
  snapshot,
  onDataChanged,
}: {
  snapshot: PortfolioSnapshot | null;
  onDataChanged: () => Promise<void>;
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const openHoldingId = idFromAddress(useParams().holdingId);
  const [showClosed, setShowClosed] = useState(false);
  const [sortState, setSortState] = useState<SortState>(DEFAULT_SORT);
  const [visibleColumnKeys, setVisibleColumnKeys] = useVisibleColumnKeys(POSITION_COLUMNS);

  const allHoldings = snapshot?.holdings ?? [];
  const openHoldings = openHoldingsOf(allHoldings);
  const closedCount = allHoldings.length - openHoldings.length;
  const openHolding = allHoldings.find((holding) => holding.id === openHoldingId) ?? null;
  useDocumentTitle('Positions', openHolding?.symbol ?? null);
  // A link to a closed holding shows the closed holdings, or its row would be hidden behind its drawer.
  const showsClosed = showClosed || openHolding?.status === 'CLOSED';
  const rows = sortHoldings(showsClosed ? allHoldings : openHoldings, sortState);
  // During the first load any holding's address opens the drawer; once loaded, only a holding that exists.
  const showsDrawer = openHoldingId !== null && (snapshot === null || openHolding !== null);

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

  const columnsMenu = (
    <ColumnsMenu columns={POSITION_COLUMNS} visibleKeys={visibleColumnKeys} onChange={setVisibleColumnKeys} />
  );

  const positionsCardWhileLoading = (
    <section className={CARD_CLASS}>
      <Toolbar>
        <span className="flex-1" />
        {columnsMenu}
      </Toolbar>
      <PositionsTableSkeleton visibleColumnKeys={visibleColumnKeys} sortState={sortState} onSort={setSortState} />
    </section>
  );

  const positionsCard = (loadedSnapshot: PortfolioSnapshot) => (
    <section className={CARD_CLASS}>
      <Toolbar>
        {openHoldings.length > 0 && (
          <AnalyzeEveryHoldingControl
            stockCount={openHoldings.length}
            estimatedAnalysisCost={loadedSnapshot.estimatedAnalysisCost}
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
        {columnsMenu}
      </Toolbar>
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
        />
      )}
    </section>
  );

  const drawerContent = (loadedSnapshot: PortfolioSnapshot, holding: Holding) => (
    <HoldingDrawer
      key={holding.id}
      holding={holding}
      closedPositions={loadedSnapshot.closedPositions.filter((closedPosition) => closedPosition.holdingId === holding.id)}
      estimatedAnalysisCost={loadedSnapshot.estimatedAnalysisCost}
      weightsTabs={weightsTabsOf(openHoldings, loadedSnapshot.totalCashValue, loadedSnapshot.investors)}
      initialWeightsInvestorId={weightsInvestorIdToOpenOn(location.state)}
      syncedAt={loadedSnapshot.asOf}
      initialTab={tabToOpenOn(location.state)}
      navigation={{
        previousLabel: neighbourOf(rows, holding.id, -1).symbol,
        nextLabel: neighbourOf(rows, holding.id, 1).symbol,
        onPrevious: () => stepTo(neighbourOf(rows, holding.id, -1)),
        onNext: () => stepTo(neighbourOf(rows, holding.id, 1)),
      }}
      onDataChanged={onDataChanged}
    />
  );

  return (
    <>
      <PageHeader title="Positions" description={openHoldingsCountText(snapshot)} />

      {snapshot === null
        ? positionsCardWhileLoading
        : allHoldings.length === 0
          ? <EmptyBox message="No positions reported." />
          : positionsCard(snapshot)}

      {showsDrawer && (
        <DrawerShell
          ariaLabel={openHolding === null ? DRAWER_LABEL_WHILE_LOADING : holdingDrawerLabelOf(openHolding)}
          onClose={closeDrawer}
        >
          {snapshot === null || openHolding === null ? <HoldingDrawerSkeleton /> : drawerContent(snapshot, openHolding)}
        </DrawerShell>
      )}
    </>
  );
}
