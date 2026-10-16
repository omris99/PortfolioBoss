import { useState } from 'react';
import * as Popover from '@radix-ui/react-popover';
import { Check, LoaderCircle, ScanSearch, TriangleAlert } from 'lucide-react';
import { analyzeHoldings, errorMessageOf } from '../lib/apiClient';
import { EXPLAINED_CLASS, PRIMARY_BUTTON_CLASS, SECONDARY_BUTTON_CLASS, SMALL_SECONDARY_BUTTON_CLASS } from '../lib/styles';
import type { AnalysisRun, EstimatedAnalysisCost } from '../types/portfolio';

// ── what a run is expected to cost ──────────────────────────────────────────────────────────────

interface CostEstimate {
  /** "0.29" */
  dollars: string;
  credits: number;
}

/** The API's average cost of one stock times `stockCount`; `null` while no stock has been analyzed yet. */
function costEstimateOf(estimate: EstimatedAnalysisCost | null, stockCount: number): CostEstimate | null {
  if (estimate === null) return null;
  return {
    dollars: (estimate.costUsdPerStock * stockCount).toFixed(2),
    credits: Math.round(estimate.tavilyCreditsPerStock * stockCount),
  };
}

/** "about $0.29 and 35 Tavily credits for 7 stocks (by the last 7 analyses)". */
function estimatedCostText(estimate: EstimatedAnalysisCost | null, stockCount: number): string | null {
  const cost = costEstimateOf(estimate, stockCount);
  if (estimate === null || cost === null) return null;
  const stocks = stockCount === 1 ? '' : ` for ${stockCount} stocks`;
  return `about $${cost.dollars} and ${cost.credits} Tavily credits${stocks} (by the last ${estimate.basedOnAnalyses} analyses)`;
}

/** What a button does and what it costs, on hover. */
function hintOf(subject: string, estimate: EstimatedAnalysisCost | null, stockCount: number): string {
  const action = `Search the web for the analyst ratings and news of ${subject}, and have Claude read them`;
  const costText = estimatedCostText(estimate, stockCount);
  return costText === null ? `${action}. What it costs shows once a stock has been analyzed.` : `${action}: ${costText}.`;
}

// ── a run ───────────────────────────────────────────────────────────────────────────────────────

/**
 * Runs a paid analysis of `holdingIds` (`null`: every open holding), then reloads the portfolio. Shared by the toolbar's
 * Analyze and a holding's own "Analyze MSFT".
 */
function useAnalysisRun(holdingIds: number[] | null, onDataChanged: () => Promise<void>) {
  const [isRunning, setIsRunning] = useState(false);
  const [lastRun, setLastRun] = useState<AnalysisRun | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const run = async () => {
    setIsRunning(true);
    setLastRun(null);
    setErrorMessage(null);
    try {
      const analysisRun = await analyzeHoldings(holdingIds);
      setLastRun(analysisRun);
      await onDataChanged();
    } catch (error) {
      setErrorMessage(errorMessageOf(error));
    } finally {
      setIsRunning(false);
    }
  };

  return { isRunning, lastRun, errorMessage, run };
}

/** "Failed: AEVA, TTWO", each one's reason on hover. They keep their previous analysis. */
function FailedStocks({ run }: { run: AnalysisRun }) {
  if (run.failed.length === 0) return null;
  const symbols = run.failed.map((failure) => failure.symbol).join(', ');
  const reasons = run.failed.map((failure) => `${failure.symbol}: ${failure.message}`).join('\n');
  return (
    <span title={reasons} className={`text-negative ${EXPLAINED_CLASS}`}>
      · Failed: {symbols}
    </span>
  );
}

/**
 * Next to the button: that it is running, then what the run did and cost — "✓ Analyzed 7 · 35 credits · $0.30" — or the
 * API's reason when it refused (a missing key, say).
 */
function RunOutcome({
  isRunning,
  runningText,
  lastRun,
  errorMessage,
}: {
  isRunning: boolean;
  /** Said while running; `null` when the button already says it. */
  runningText: string | null;
  lastRun: AnalysisRun | null;
  errorMessage: string | null;
}) {
  if (isRunning) {
    return runningText === null ? null : <span className="whitespace-nowrap text-[13px] text-muted">{runningText}</span>;
  }
  if (errorMessage !== null) {
    return (
      <span className="inline-flex items-center gap-1.5 text-[13px] text-negative">
        <TriangleAlert size={14} className="shrink-0" />
        {errorMessage}
      </span>
    );
  }
  if (lastRun === null) return null;
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap text-[13px] text-muted">
      {lastRun.failed.length === 0 && <Check size={14} className="text-positive" />}
      Analyzed {lastRun.analyzed} · {lastRun.tavilyCredits} credits · ${lastRun.costUsd.toFixed(2)}
      <FailedStocks run={lastRun} />
    </span>
  );
}

function RunningIcon({ isRunning, size }: { isRunning: boolean; size: number }) {
  return isRunning ? (
    <LoaderCircle size={size} className="animate-spin motion-reduce:animate-none" />
  ) : (
    <ScanSearch size={size} />
  );
}

// ── the two buttons ─────────────────────────────────────────────────────────────────────────────

/** The confirm popover's text: the stocks, what it should cost, and that it takes a while. */
function ConfirmText({ stockCount, estimate }: { stockCount: number; estimate: EstimatedAnalysisCost | null }) {
  const cost = costEstimateOf(estimate, stockCount);
  return (
    <>
      <p className="text-body font-semibold">Analyze {stockCount} open holdings?</p>
      <p className="mt-1 text-muted">
        {cost === null ? (
          'What it costs shows once a stock has been analyzed. '
        ) : (
          <>
            About <b className="font-semibold text-ink">${cost.dollars}</b> and{' '}
            <b className="font-semibold text-ink">{cost.credits} search credits</b>.{' '}
          </>
        )}
        It searches the web for each one’s analyst ratings and news, and takes tens of seconds.
      </p>
    </>
  );
}

/**
 * The toolbar's Analyze: every open holding, paid, so a click first asks — "Analyze 7 open holdings? About $0.30 and
 * 35 search credits…" — and Esc or a click outside cancels. Then "Analyzing…", then what the run did.
 */
export function AnalyzeEveryHoldingControl({
  stockCount,
  estimatedAnalysisCost,
  onDataChanged,
}: {
  stockCount: number;
  estimatedAnalysisCost: EstimatedAnalysisCost | null;
  onDataChanged: () => Promise<void>;
}) {
  const [isConfirming, setIsConfirming] = useState(false);
  const { isRunning, lastRun, errorMessage, run } = useAnalysisRun(null, onDataChanged);

  const startRun = () => {
    setIsConfirming(false);
    void run();
  };

  return (
    <div className="inline-flex min-w-0 items-center gap-3">
      <Popover.Root open={isConfirming} onOpenChange={setIsConfirming}>
        <Popover.Trigger
          disabled={isRunning}
          title={hintOf('every open holding', estimatedAnalysisCost, stockCount)}
          className={PRIMARY_BUTTON_CLASS}
        >
          <RunningIcon isRunning={isRunning} size={16} />
          {isRunning ? 'Analyzing…' : 'Analyze'}
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content
            align="start"
            sideOffset={6}
            aria-label="Confirm the analysis"
            className="z-30 w-[320px] rounded-md border bg-surface-raised px-4 py-3 text-[13px] leading-[18px] text-ink shadow-raised"
          >
            <ConfirmText stockCount={stockCount} estimate={estimatedAnalysisCost} />
            <div className="mt-3 flex gap-2">
              <button type="button" onClick={startRun} className={PRIMARY_BUTTON_CLASS}>
                Analyze {stockCount} holdings
              </button>
              <Popover.Close className={SECONDARY_BUTTON_CLASS}>Cancel</Popover.Close>
            </div>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>
      <RunOutcome
        isRunning={isRunning}
        runningText={`${stockCount} holdings · this takes tens of seconds`}
        lastRun={lastRun}
        errorMessage={errorMessage}
      />
    </div>
  );
}

/** "Analyze MSFT" in a holding's drawer: one stock, about $0.04, so it runs at once. */
export function AnalyzeHoldingButton({
  holdingId,
  symbol,
  estimatedAnalysisCost,
  onDataChanged,
}: {
  holdingId: number;
  symbol: string;
  estimatedAnalysisCost: EstimatedAnalysisCost | null;
  onDataChanged: () => Promise<void>;
}) {
  const { isRunning, lastRun, errorMessage, run } = useAnalysisRun([holdingId], onDataChanged);
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <RunOutcome isRunning={isRunning} runningText={null} lastRun={lastRun} errorMessage={errorMessage} />
      <button
        type="button"
        onClick={() => void run()}
        disabled={isRunning}
        title={hintOf(symbol, estimatedAnalysisCost, 1)}
        className={SMALL_SECONDARY_BUTTON_CLASS}
      >
        <RunningIcon isRunning={isRunning} size={14} />
        {isRunning ? 'Analyzing…' : `Analyze ${symbol}`}
      </button>
    </span>
  );
}
