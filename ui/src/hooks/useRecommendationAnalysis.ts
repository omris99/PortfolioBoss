import { useState } from 'react';
import { analyzeRecommendations, errorMessageOf } from '../lib/apiClient';
import type { AnalysisRun } from '../types/portfolio';
import type { RecommendationAnalysisRun } from '../types/recommendations';

/**
 * Where the page's paid analysis stands. One run at a time on the page: the short list and its pick (`candidateId`
 * `null`), or one stock — every Analyze button reads the same state, so a stock's button in its row and in its drawer
 * agree, and the others wait while one runs.
 */
export type RecommendationAnalysisState =
  | { kind: 'idle' }
  | { kind: 'running'; candidateId: number | null }
  | { kind: 'done'; candidateId: number | null; run: RecommendationAnalysisRun }
  | { kind: 'failed'; candidateId: number | null; message: string };

/** What `RunOutcome` shows for one run: running, what it did and cost, or the API's reason it refused. */
export interface RunOutcomeFigures {
  isRunning: boolean;
  lastRun: AnalysisRun | null;
  errorMessage: string | null;
}

export function isAnalysisRunning(analysisState: RecommendationAnalysisState): boolean {
  return analysisState.kind === 'running';
}

/**
 * The state as the run of `candidateId` (`null`: the short list) sees it — nothing at all when the latest run was
 * another one's.
 */
export function runOutcomeOf(analysisState: RecommendationAnalysisState, candidateId: number | null): RunOutcomeFigures {
  if (analysisState.kind === 'idle' || analysisState.candidateId !== candidateId) {
    return { isRunning: false, lastRun: null, errorMessage: null };
  }
  return {
    isRunning: analysisState.kind === 'running',
    lastRun: analysisState.kind === 'done' ? analysisState.run.analysis : null,
    errorMessage: analysisState.kind === 'failed' ? analysisState.message : null,
  };
}

/**
 * Runs the page's paid analysis, then `onAnalyzed` (the lists' reload), so "done" shows only once the new analyses — and
 * the new recommendation — are on the page. A refusal keeps the server's words ("…FINNHUB_API_KEY is not set…").
 */
export function useRecommendationAnalysis(onAnalyzed: () => Promise<void>) {
  const [analysisState, setAnalysisState] = useState<RecommendationAnalysisState>({ kind: 'idle' });

  const runAnalysis = async (candidateId: number | null) => {
    setAnalysisState({ kind: 'running', candidateId });
    try {
      const run = await analyzeRecommendations(candidateId);
      await onAnalyzed();
      setAnalysisState({ kind: 'done', candidateId, run });
    } catch (error) {
      setAnalysisState({ kind: 'failed', candidateId, message: errorMessageOf(error) });
    }
  };

  return { analysisState, runAnalysis };
}
