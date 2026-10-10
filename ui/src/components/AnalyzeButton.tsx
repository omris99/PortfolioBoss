import { useState } from 'react';
import { LoaderCircle, Sparkles } from 'lucide-react';
import { analyzeHoldings, errorMessageOf } from '../lib/apiClient';
import type { AnalysisRun } from '../types/portfolio';

/** "Analyzed 7 · 21 credits · $0.21": the stocks stored, Tavily's credits and Claude's dollars. */
function runSummaryOf(run: AnalysisRun): string {
  return `Analyzed ${run.analyzed} · ${run.tavilyCredits} credits · $${run.costUsd.toFixed(2)}`;
}

/** "Failed: AEVA, TTWO", each one's reason on hover. They keep their previous analysis. */
function FailedStocks({ run }: { run: AnalysisRun }) {
  if (run.failed.length === 0) return null;
  const symbols = run.failed.map((failure) => failure.symbol).join(', ');
  const reasons = run.failed.map((failure) => `${failure.symbol}: ${failure.message}`).join('\n');
  return (
    <span title={reasons} className="text-rose-400">
      · Failed: {symbols}
    </span>
  );
}

/**
 * Starts a paid analysis of the analyst ratings and news — of `holdingIds`, or of every open holding (`null`) — and
 * reloads the portfolio when it is done. Shows "Analyzing…" meanwhile (tens of seconds), then what the run did and cost,
 * or the API's reason when it refused, e.g. a missing API key.
 */
export function AnalyzeButton({
  holdingIds,
  label,
  hint,
  onDataChanged,
}: {
  holdingIds: number[] | null;
  label: string;
  /** What will be analyzed, and what it costs. */
  hint: string;
  onDataChanged: () => Promise<void>;
}) {
  const [isRunning, setIsRunning] = useState(false);
  const [lastRun, setLastRun] = useState<AnalysisRun | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleClick = async () => {
    setIsRunning(true);
    setLastRun(null);
    setErrorMessage(null);
    try {
      const run = await analyzeHoldings(holdingIds);
      setLastRun(run);
      await onDataChanged();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsRunning(false);
    }
  };

  return (
    <div className="flex flex-wrap items-center gap-2 text-xs">
      <button
        type="button"
        title={hint}
        onClick={() => void handleClick()}
        disabled={isRunning}
        className="flex items-center gap-1 rounded-md border border-emerald-500/50 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300 transition-colors hover:bg-emerald-500/20 disabled:opacity-60"
      >
        {isRunning ? <LoaderCircle size={12} className="animate-spin" /> : <Sparkles size={12} />}
        {isRunning ? 'Analyzing…' : label}
      </button>
      {lastRun && (
        <span className="text-slate-400">
          {runSummaryOf(lastRun)} <FailedStocks run={lastRun} />
        </span>
      )}
      {errorMessage && <span className="text-rose-400">{errorMessage}</span>}
    </div>
  );
}
