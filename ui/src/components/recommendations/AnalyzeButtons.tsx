import type { KeyboardEvent, MouseEvent } from 'react';
import { LoaderCircle, ScanSearch, TriangleAlert } from 'lucide-react';
import {
  isAnalysisRunning,
  runOutcomeOf,
  type RecommendationAnalysisState,
} from '../../hooks/useRecommendationAnalysis';
import { PRIMARY_BUTTON_CLASS, SECONDARY_BUTTON_CLASS, SMALL_SECONDARY_BUTTON_CLASS } from '../../lib/styles';
import type { EstimatedAnalysisCost } from '../../types/portfolio';
import type { HotStock } from '../../types/recommendations';
import { ConfirmBeforeRun, CostSentence, estimatedCostText, RunningIcon, RunOutcome } from '../AnalyzeControls';

/**
 * The Recommendations page's paid buttons (DESIGN_BRIEF.md §8.4): Analyze & recommend for the short list, and Analyze
 * for one stock — in its row, after a confirm, and in its drawer, at once. One run at a time on the page: while one
 * runs, every other button waits.
 */

const ANOTHER_RUN_HINT = 'Another analysis is running: wait for it to finish.';

/**
 * The short list the full run analyzes, counted from the API's marks (RECOMMENDATIONS_TODO.md, decision 14): the first
 * 10 hot stocks whose analysts' target is 15% or more above the price — `withEnoughUpside` more when over 10 have one.
 */
export interface ShortListCounts {
  analyzed: number;
  withEnoughUpside: number;
}

export function shortListCountsOf(hotStocks: HotStock[]): ShortListCounts {
  return {
    analyzed: hotStocks.filter((stock) => stock.inShortList).length,
    withEnoughUpside: hotStocks.filter((stock) => stock.analystTarget.hasEnoughUpside).length,
  };
}

/**
 * "the 7 hot stocks whose analysts' target is 15% or more above the price" — or "the first 10 of the 12 …" when more
 * have one than the run takes.
 */
export function shortListWords(shortList: ShortListCounts): string {
  const whoseTarget = 'whose analysts’ target is 15% or more above the price';
  if (shortList.withEnoughUpside > shortList.analyzed) {
    return `the first ${shortList.analyzed} of the ${shortList.withEnoughUpside} hot stocks ${whoseTarget}`;
  }
  if (shortList.analyzed === 1) return `the one hot stock ${whoseTarget}`;
  return `the ${shortList.analyzed} hot stocks ${whoseTarget}`;
}

/** What one stock's Analyze does and costs, on hover. */
function analyzeOneHint(symbol: string, estimate: EstimatedAnalysisCost | null): string {
  const action =
    `Search the web for ${symbol}’s analyst ratings and news, have Claude read them, and ask Finnhub for its ` +
    'company figures';
  const costText = estimatedCostText(estimate, 1);
  const cost = costText === null ? '. What it costs shows once a stock has been analyzed' : `: ${costText}`;
  return `${action}${cost}. No new recommendation.`;
}

/** Whether `stock`'s own run failed: the API refused it, or the run answered with the stock among its failures. */
function failureOf(analysisState: RecommendationAnalysisState, stock: HotStock): string | null {
  if (analysisState.kind === 'idle' || analysisState.candidateId !== stock.candidateId) return null;
  if (analysisState.kind === 'failed') return analysisState.message;
  if (analysisState.kind !== 'done') return null;
  const failure = analysisState.run.analysis.failed.find((failedStock) => failedStock.symbol === stock.symbol);
  return failure === undefined ? null : failure.message;
}

// ── the short list ──────────────────────────────────────────────────────────────────────────────

/** What Analyze & recommend does and costs, on hover — or why it waits. */
function shortListHintOf(
  shortList: ShortListCounts,
  estimate: EstimatedAnalysisCost | null,
  isOtherRunRunning: boolean,
): string {
  if (shortList.analyzed === 0) {
    return 'No hot stock has the analysts’ target 15% or more above its price: nothing to analyze.';
  }
  if (isOtherRunRunning) return ANOTHER_RUN_HINT;
  const costText =
    estimatedCostText(estimate, shortList.analyzed) ?? 'what it costs shows once a stock has been analyzed';
  return `Analyze ${shortListWords(shortList)}, and pick up to 3 by the rules: ${costText}.`;
}

function ShortListConfirmText({
  shortList,
  estimate,
}: {
  shortList: ShortListCounts;
  estimate: EstimatedAnalysisCost | null;
}) {
  return (
    <>
      <p className="text-body font-semibold">Analyze {shortListWords(shortList)}?</p>
      <p className="mt-1 text-muted">
        <CostSentence estimate={estimate} stockCount={shortList.analyzed} />
        It searches the web for each one’s analyst ratings and news and asks Finnhub for its company figures; then the
        code picks up to 3. It takes about a minute.
      </p>
    </>
  );
}

/**
 * Analyze & recommend — paid, so a click first asks. `isPrimary`: the button of the card's invitation before any
 * recommendation; secondary in its head afterwards. Its outcome sits to its left.
 */
export function AnalyzeShortListControl({
  shortList,
  estimate,
  analysisState,
  onAnalyze,
  isPrimary,
}: {
  shortList: ShortListCounts;
  estimate: EstimatedAnalysisCost | null;
  analysisState: RecommendationAnalysisState;
  onAnalyze: (candidateId: number | null) => void;
  isPrimary: boolean;
}) {
  const stockCount = shortList.analyzed;
  const outcome = runOutcomeOf(analysisState, null);
  const isOtherRunRunning = isAnalysisRunning(analysisState) && !outcome.isRunning;
  return (
    <span className="inline-flex min-w-0 items-center gap-3">
      <RunOutcome
        isRunning={outcome.isRunning}
        runningText={`${stockCount} stocks · about a minute`}
        lastRun={outcome.lastRun}
        errorMessage={outcome.errorMessage}
      />
      <ConfirmBeforeRun
        trigger={
          <button
            type="button"
            disabled={stockCount === 0 || isAnalysisRunning(analysisState)}
            title={shortListHintOf(shortList, estimate, isOtherRunRunning)}
            className={`shrink-0 ${isPrimary ? PRIMARY_BUTTON_CLASS : SECONDARY_BUTTON_CLASS}`}
          >
            <RunningIcon isRunning={outcome.isRunning} size={16} />
            {outcome.isRunning ? 'Analyzing…' : 'Analyze & recommend'}
          </button>
        }
        confirmLabel={`Analyze ${stockCount} stocks`}
        onConfirm={() => onAnalyze(null)}
        align="end"
      >
        <ShortListConfirmText shortList={shortList} estimate={estimate} />
      </ConfirmBeforeRun>
    </span>
  );
}

// ── one stock ───────────────────────────────────────────────────────────────────────────────────

/** A click or a key on the row's button — or in its confirm, which React counts as inside it — is not the row's. */
function keepFromTheRow(event: MouseEvent | KeyboardEvent) {
  event.stopPropagation();
}

/** The row's button on hover: what it does and costs — or that it runs, waits for another run, or failed and why. */
function rowHintOf(
  stock: HotStock,
  estimate: EstimatedAnalysisCost | null,
  analysisState: RecommendationAnalysisState,
  failure: string | null,
): string {
  if (runOutcomeOf(analysisState, stock.candidateId).isRunning) return `Analyzing ${stock.symbol}…`;
  if (isAnalysisRunning(analysisState)) return ANOTHER_RUN_HINT;
  if (failure !== null) return `The last analysis of ${stock.symbol} failed: ${failure}`;
  return analyzeOneHint(stock.symbol, estimate);
}

/** The row's icon while its own run runs or failed: the spinner, or the triangle with the reason on hover. */
function RowAnalyzeIcon({ isRunning, failure }: { isRunning: boolean; failure: string | null }) {
  if (isRunning) {
    return (
      <LoaderCircle size={16} className="animate-spin text-accent motion-reduce:animate-none" aria-hidden="true" />
    );
  }
  if (failure !== null) return <TriangleAlert size={16} className="text-negative" aria-hidden="true" />;
  return <ScanSearch size={16} aria-hidden="true" />;
}

/**
 * A row's Analyze (the lists' last column): a small icon button whose hover gives the estimate; a click asks first,
 * toward the left. Running: the spinner. Failed: the triangle, the reason on hover. Its clicks never open the row.
 */
export function RowAnalyzeButton({
  stock,
  estimate,
  analysisState,
  onAnalyze,
}: {
  stock: HotStock;
  estimate: EstimatedAnalysisCost | null;
  analysisState: RecommendationAnalysisState;
  onAnalyze: (candidateId: number | null) => void;
}) {
  const isRunning = runOutcomeOf(analysisState, stock.candidateId).isRunning;
  const failure = failureOf(analysisState, stock);
  return (
    <span onClick={keepFromTheRow} onKeyDown={keepFromTheRow} className="inline-flex">
      <ConfirmBeforeRun
        trigger={
          <button
            type="button"
            aria-label={`Analyze ${stock.symbol}`}
            title={rowHintOf(stock, estimate, analysisState, failure)}
            disabled={isAnalysisRunning(analysisState)}
            className="inline-flex h-7 w-7 items-center justify-center rounded-md text-muted hover:bg-accent-soft hover:text-accent disabled:cursor-default disabled:hover:bg-transparent disabled:hover:text-muted"
          >
            <RowAnalyzeIcon isRunning={isRunning} failure={failure} />
          </button>
        }
        confirmLabel={`Analyze ${stock.symbol}`}
        onConfirm={() => onAnalyze(stock.candidateId)}
        align="end"
      >
        <p className="text-body font-semibold">Analyze {stock.symbol}?</p>
        <p className="mt-1 text-muted">
          <CostSentence estimate={estimate} stockCount={1} />
          No new recommendation: the stock’s own analysis only.
        </p>
      </ConfirmBeforeRun>
    </span>
  );
}

/** "Analyze KRVN" in the stock's drawer: one stock, about five cents, so it runs at once, as a holding's does. */
export function AnalyzeCandidateButton({
  stock,
  estimate,
  analysisState,
  onAnalyze,
}: {
  stock: HotStock;
  estimate: EstimatedAnalysisCost | null;
  analysisState: RecommendationAnalysisState;
  onAnalyze: (candidateId: number | null) => void;
}) {
  const outcome = runOutcomeOf(analysisState, stock.candidateId);
  const isOtherRunRunning = isAnalysisRunning(analysisState) && !outcome.isRunning;
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <RunOutcome
        isRunning={outcome.isRunning}
        runningText={null}
        lastRun={outcome.lastRun}
        errorMessage={outcome.errorMessage}
      />
      <button
        type="button"
        onClick={() => onAnalyze(stock.candidateId)}
        disabled={isAnalysisRunning(analysisState)}
        title={isOtherRunRunning ? ANOTHER_RUN_HINT : analyzeOneHint(stock.symbol, estimate)}
        className={SMALL_SECONDARY_BUTTON_CLASS}
      >
        <RunningIcon isRunning={outcome.isRunning} size={14} />
        {outcome.isRunning ? 'Analyzing…' : `Analyze ${stock.symbol}`}
      </button>
    </span>
  );
}
