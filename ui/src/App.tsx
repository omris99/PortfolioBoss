import type { ReactNode } from 'react';
import { Navigate, Route, Routes } from 'react-router';
import { AppShell, type LoadStatus } from './components/AppShell';
import { EmptyBox } from './components/EmptyBox';
import { InvestorsProvider } from './components/InvestorsContext';
import { usePortfolio } from './hooks/usePortfolio';
import { formatDateTime } from './lib/format';
import { ClosedPositionsPage } from './pages/ClosedPositionsPage';
import { InvestorsPage } from './pages/InvestorsPage';
import { OverviewPage } from './pages/OverviewPage';
import { PositionsPage } from './pages/PositionsPage';
import type { PortfolioSnapshot } from './types/portfolio';

function loadStatusOf(errorMessage: string | null, isLoading: boolean): LoadStatus {
  if (errorMessage) return 'error';
  if (isLoading) return 'loading';
  return 'ready';
}

/**
 * Loads the portfolio once for every page, and shows the page of the address: / the Overview, /positions (with
 * /positions/12 opening holding 12's row), /closed and /investors. Any other address goes to the Overview.
 */
function App() {
  const { snapshot, errorMessage, isLoading, reload } = usePortfolio();

  /** The page, once the first snapshot is in; until then, a box saying it is loading (or that nothing came). */
  const pageOnceLoaded = (renderPage: (loadedSnapshot: PortfolioSnapshot) => ReactNode): ReactNode =>
    snapshot === null ? <EmptyBox message={isLoading ? 'Loading the portfolio…' : 'No data.'} /> : renderPage(snapshot);

  return (
    // Every component below can read the investors with useInvestors(), without receiving them as a prop.
    <InvestorsProvider investors={snapshot?.investors ?? []}>
      <AppShell
        account={snapshot?.account ?? null}
        syncedAt={snapshot === null ? null : formatDateTime(snapshot.asOf)}
        loadStatus={loadStatusOf(errorMessage, isLoading)}
        errorMessage={errorMessage}
        hasData={snapshot !== null}
        onRetry={() => void reload()}
      >
        <Routes>
          <Route index element={pageOnceLoaded((loaded) => <OverviewPage snapshot={loaded} />)} />
          <Route
            path="positions/:holdingId?"
            element={pageOnceLoaded((loaded) => <PositionsPage snapshot={loaded} onDataChanged={reload} />)}
          />
          <Route
            path="closed"
            element={pageOnceLoaded((loaded) => (
              <ClosedPositionsPage closedPositions={loaded.closedPositions} onDataChanged={reload} />
            ))}
          />
          <Route
            path="investors"
            element={pageOnceLoaded((loaded) => <InvestorsPage investors={loaded.investors} onDataChanged={reload} />)}
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AppShell>
    </InvestorsProvider>
  );
}

export default App;
