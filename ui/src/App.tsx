import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { AppShell, type LoadStatus } from './components/AppShell';
import { EmptyBox } from './components/EmptyBox';
import { InvestorsProvider } from './components/InvestorsContext';
import { SectorOptionsList } from './components/SectorCell';
import { usePortfolio } from './hooks/usePortfolio';
import { useReadingFromTws } from './hooks/useReadingFromTws';
import { syncNow } from './lib/apiClient';
import { AveragingCalculatorPage } from './pages/AveragingCalculatorPage';
import { ClosedPositionsPage } from './pages/ClosedPositionsPage';
import { InvestorsPage } from './pages/InvestorsPage';
import { OverviewPage } from './pages/OverviewPage';
import { PositionsPage } from './pages/PositionsPage';
import { RecommendationsPage } from './pages/RecommendationsPage';
import { TaxesPage } from './pages/TaxesPage';
import type { PortfolioSnapshot } from './types/portfolio';

function loadStatusOf(errorMessage: string | null, isLoading: boolean): LoadStatus {
  if (errorMessage) return 'error';
  if (isLoading) return 'loading';
  return 'ready';
}

/**
 * Loads the portfolio once for every page, and shows the page of the address: / the Overview, /positions (with
 * /positions/12 opening holding 12's row), /averaging, /closed, /investors, /recommendations (with
 * /recommendations/40 opening a scanned stock's drawer) and /taxes. Any other address goes to the Overview.
 */
function App() {
  const { snapshot, errorMessage, isLoading, reload } = usePortfolio();
  // Sync now, in the top bar of every page: a new reading from TWS, then the portfolio loaded again.
  const { readingState: syncNowState, startReading: startSyncNow } = useReadingFromTws(syncNow, reload);

  /** When the first load failed, every page says so; the error band above it says why. */
  const firstLoadFailed = snapshot === null && !isLoading;
  const noDataBox = <EmptyBox message="No data." />;

  /**
   * The Overview and Positions, with their skeletons while the first load runs (round 6): `renderPage` gets `null`
   * until the first snapshot is in.
   */
  const pageWithSkeleton = (renderPage: (snapshotOrNull: PortfolioSnapshot | null) => ReactNode): ReactNode =>
    firstLoadFailed ? noDataBox : renderPage(snapshot);

  /**
   * Every other page, once the first snapshot is in; until then nothing under the top bar, which says "Loading…" — the
   * first load takes a moment, and the design draws a skeleton only for the Overview, Positions and a holding's drawer.
   */
  const pageOnceLoaded = (renderPage: (loadedSnapshot: PortfolioSnapshot) => ReactNode): ReactNode => {
    if (snapshot !== null) return renderPage(snapshot);
    return firstLoadFailed ? noDataBox : null;
  };

  return (
    // Every component below can read the investors with useInvestors(), without receiving them as a prop.
    <InvestorsProvider investors={snapshot?.investors ?? []}>
      <AppShell
        account={snapshot?.account ?? null}
        portfolioSyncedAt={snapshot?.asOf ?? null}
        loadStatus={loadStatusOf(errorMessage, isLoading)}
        errorMessage={errorMessage}
        hasData={snapshot !== null}
        onRetry={() => void reload()}
        syncNowState={syncNowState}
        onSyncNow={() => void startSyncNow()}
      >
        {snapshot !== null && (
          <SectorOptionsList
            sectors={[
              ...snapshot.holdings.map((holding) => holding.sector),
              ...snapshot.closedPositions.map((closedPosition) => closedPosition.sector),
            ]}
          />
        )}
        <Routes>
          <Route index element={pageWithSkeleton((snapshotOrNull) => <OverviewPage snapshot={snapshotOrNull} />)} />
          <Route
            path="positions/:holdingId?"
            element={pageWithSkeleton((snapshotOrNull) => (
              <PositionsPage snapshot={snapshotOrNull} onDataChanged={reload} />
            ))}
          />
          <Route
            path="averaging"
            element={pageOnceLoaded((loaded) => <AveragingCalculatorPage snapshot={loaded} />)}
          />
          <Route
            path="closed"
            element={pageOnceLoaded((loaded) => (
              <ClosedPositionsPage
                closedPositions={loaded.closedPositions}
                holdings={loaded.holdings}
                onDataChanged={reload}
              />
            ))}
          />
          <Route
            path="investors"
            element={pageOnceLoaded((loaded) => <InvestorsPage investors={loaded.investors} onDataChanged={reload} />)}
          />
          {/* After the portfolio too: the page names the investors and their cash, quotes the analysis's cost and
              weighs a purchase against the holdings — all of which come with it. */}
          <Route
            path="recommendations/:candidateId?"
            element={pageOnceLoaded((loaded) => <RecommendationsPage snapshot={loaded} />)}
          />
          {/* After the portfolio too: its sync's time loads the taxes again, since a sync downloads IB's reports and
              the rates still missing. */}
          <Route
            path="taxes"
            element={pageOnceLoaded((loaded) => <TaxesPage portfolioSyncedAt={loaded.asOf} />)}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell>
    </InvestorsProvider>
  );
}

export default App;
