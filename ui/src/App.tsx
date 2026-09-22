import { RefreshCw, TriangleAlert } from 'lucide-react';
import { PositionsTable } from './components/PositionsTable';
import { SummaryBar } from './components/SummaryBar';
import { usePortfolio } from './hooks/usePortfolio';
import type { PortfolioSnapshot } from './types/portfolio';

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

function PositionsContent({ snapshot, isLoading }: { snapshot: PortfolioSnapshot | null; isLoading: boolean }) {
  if (snapshot === null) {
    return <EmptyBox message={isLoading ? 'Loading holdings…' : 'No data.'} />;
  }
  if (snapshot.holdings.length === 0) {
    return <EmptyBox message="No open positions reported." />;
  }
  return <PositionsTable holdings={snapshot.holdings} />;
}

function App() {
  const { snapshot, errorMessage, isLoading, retry } = usePortfolio();

  const loadStatus = loadStatusOf(errorMessage, isLoading);
  const sectionSubtitle = snapshot
    ? `Account ${snapshot.account} · snapshot taken ${new Date(snapshot.asOf).toLocaleString()}`
    : 'Snapshot read from TWS when the API started.';

  return (
    <div className="min-h-full">
      <main className="mx-auto flex w-full max-w-6xl flex-col gap-4 p-6">
        <header>
          <h1 className="text-xl font-semibold text-slate-100">PortfolioBoss</h1>
          <p className="mt-1 text-xs text-slate-400">Read-only view of your Interactive Brokers holdings.</p>
        </header>

        {errorMessage && <ErrorBanner message={errorMessage} onRetry={() => void retry()} />}

        {snapshot && <SummaryBar snapshot={snapshot} />}

        <section className="rounded-xl border border-slate-800 bg-slate-900/40 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <div className="text-sm font-semibold text-slate-100">Positions</div>
              <div className="mt-1 text-xs text-slate-400">{sectionSubtitle}</div>
            </div>
            <div className="flex items-center gap-1 text-xs">
              <span className={`inline-block h-2 w-2 rounded-full ${STATUS_DOT_COLORS[loadStatus]}`} />
              <span className="capitalize text-slate-300">{loadStatus}</span>
            </div>
          </div>

          <div className="mt-4">
            <PositionsContent snapshot={snapshot} isLoading={isLoading} />
          </div>
        </section>
      </main>
    </div>
  );
}

export default App;
