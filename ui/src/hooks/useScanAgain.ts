import { useState } from 'react';
import { ApiError, errorMessageOf, scanAgain } from '../lib/apiClient';

/** HTTP 409: another reading from TWS is running — not a failure, the button can be pressed again in a moment. */
const ANOTHER_READING_RUNS = 409;

/** Where Scan again stands: its status next to the button says it in words (DESIGN_BRIEF.md §8.4). */
export type ScanAgainState =
  | { kind: 'idle' }
  | { kind: 'scanning' }
  | { kind: 'done' }
  | { kind: 'busy'; message: string }
  | { kind: 'failed'; message: string };

/**
 * Runs Scan again, then `onScanned` (the lists' reload), so "done" shows only once the new scan is on the page. A
 * failure keeps the server's words — "TWS is not reachable…", "no scan answered…".
 */
export function useScanAgain(onScanned: () => Promise<void>) {
  const [scanAgainState, setScanAgainState] = useState<ScanAgainState>({ kind: 'idle' });

  const runScanAgain = async () => {
    setScanAgainState({ kind: 'scanning' });
    try {
      await scanAgain();
      await onScanned();
      setScanAgainState({ kind: 'done' });
    } catch (error) {
      const message = errorMessageOf(error);
      const anotherReadingRuns = error instanceof ApiError && error.status === ANOTHER_READING_RUNS;
      setScanAgainState(anotherReadingRuns ? { kind: 'busy', message } : { kind: 'failed', message });
    }
  };

  return { scanAgainState, runScanAgain };
}
