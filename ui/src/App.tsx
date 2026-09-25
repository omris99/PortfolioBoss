import { useState } from 'react';
import { RefreshCw, TriangleAlert } from 'lucide-react';
import { PositionsTable } from './components/PositionsTable';
import { SummaryBar } from './components/SummaryBar';
import { usePortfolio } from './hooks/usePortfolio';
import type { Holding, PortfolioSnapshot } from './types/portfolio';

type LoadStatus = 'loading' | 'ready' | 'error';

const STATUS_DOT_COLORS: Record<LoadStatus, string> = {
  loading: 'bg-amber-400',
  ready: 'bg-emerald-400',
  error: 'bg-rose-400',
};

function loadStatusOf(errorMessage: string | null, isLoading: boolean): LoadStatus {
  if (errorMessage) return 'error';
  if (isLoading) return 'loading';
  return 'ready';
}

/**
 * The API also sends the holdings TWS no longer reports (CLOSED), whose sector and trades are kept. They are hidden
 * unless "Show closed" is on, and never counted in the summary.
 */
function openHoldingsOf(snapshot: PortfolioSnapshot | null): Holding[] {
  if (snapshot === null) return [];
  return snapshot.holdings.filter((holding) => holding.status === 'OPEN');
}

function ShowClosedToggle({
  closedCount,
  showClosed,
  onChange,
}: {
  closedCount: number;
  showClosed: boolean;
  onChange: (showClosed: boolean) => void;
}) {
  return (
    <label className="flex cursor-pointer items-center gap-1.5 text-xs text-slate-400">
      <input
        type="checkbox"
        checked={showClosed}
        onChange={(event) => onChange(event.target.checked)}
        className="accent-emerald-500"
      />
      Show closed ({closedCount})
    </label>
  );
}

function EmptyBox({ message }: { message: string }) {
  return (
    <div className="rounded-xl border border-dashed border-slate-800 py-12 text-center">
      <p className="text-sm text-slate-500">{message}</p>
    </div>
  );
}

function ErrorBanner({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-rose-700/50 bg-rose-500/10 px-4 py-3 text-sm text-rose-200"
    >
      <div className="flex items-center gap-2">
        <TriangleAlert size={16} className="shrink-0" />
        {message}
      </div>
      <button
        type="button"
        onClick={onRetry}
        className="flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-800/50 px-3 py-1.5 text-[11px] font-medium text-slate-300 transition-all hover:border-emerald-500/50 hover:bg-emerald-500/10 hover:text-emerald-400"
      >
        <RefreshCw size={14} />
        Retry
      </button>
    </div>
  );
}

function PositionsContent({
  snapshot,
  visibleHoldings,
  isLoading,
  onDataChanged,
}: {
  snapshot: PortfolioSnapshot | null;
  visibleHoldings: Holding[];
  isLoading: boolean;
  onDataChanged: () => Promise<void>;
}) {
  if (snapshot === null) {
    return <EmptyBox message={isLoading ? 'Loading holdings…' : 'No data.'} />;
  }
  if (visibleHoldings.length === 0) {
    return <EmptyBox message="No open positions reported." />;
  }
  return <PositionsTable holdings={visibleHoldings} onDataChanged={onDataChanged} />;
}

function App() {
  const { snapshot, errorMessage, isLoading, reload } = usePortfolio();
  const [showClosed, setShowClosed] = useState(false);

  const openHoldings = openHoldingsOf(snapshot);
  const allHoldings = snapshot?.holdings ?? [];
  const closedCount = allHoldings.length - openHoldings.length;
  const visibleHoldings = showClosed ? allHoldings : openHoldings;
  const loadStatus = loadStatusOf(errorMessage, isLoading);
  const sectionSubtitle = snapshot
    ? `Account ${snapshot.account} · last synced from TWS ${new Date(snapshot.asOf).toLocaleString()}`
    : 'The portfolio as of the last sync from TWS.';

  return (
    <div className="min-h-full">
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
        <header>
          <h1 className="text-xl font-semibold text-slate-100">PortfolioBoss</h1>
          <p className="mt-1 text-xs text-slate-400">
            Read-only against Interactive Brokers · sector and trades are stored locally.
          </p>
        </header>

        {errorMessage && <ErrorBanner message={errorMessage} onRetry={() => void reload()} />}

        {snapshot && <SummaryBar snapshot={snapshot} openHoldings={openHoldings} />}

        <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-sm font-semibold text-slate-100">Positions</div>
              <div className="mt-1 text-xs text-slate-400">{sectionSubtitle}</div>
            </div>
            <div className="flex items-center gap-4">
              <ShowClosedToggle closedCount={closedCount} showClosed={showClosed} onChange={setShowClosed} />
              <div className="flex items-center gap-1 text-xs">
                <span className={`inline-block h-2 w-2 rounded-full ${STATUS_DOT_COLORS[loadStatus]}`} />
                <span className="capitalize text-slate-300">{loadStatus}</span>
              </div>
            </div>
          </div>

          <div className="mt-4">
            <PositionsContent
              snapshot={snapshot}
              visibleHoldings={visibleHoldings}
              isLoading={isLoading}
              onDataChanged={reload}
            />
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
