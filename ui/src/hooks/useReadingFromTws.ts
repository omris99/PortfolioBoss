import { useState } from 'react';
import { ApiError, errorMessageOf } from '../lib/apiClient';

/** HTTP 409: another reading from TWS is running — not a failure, the button can be pressed again in a moment. */
const ANOTHER_READING_RUNS = 409;

/** Where a button that reads TWS stands — Scan again's or Sync now's —; each says it in its own words. */
export type ReadingFromTwsState =
  | { kind: 'idle' }
  | { kind: 'reading' }
  | { kind: 'done' }
  | { kind: 'busy'; message: string }
  | { kind: 'failed'; message: string };

/**
 * Runs one reading from TWS — `readFromTws`, Scan again's or Sync now's call to the server —, then `reloadAfterReading`,
 * so "done" shows only once the new data is on the page. A failure keeps the server's words: "TWS is not reachable…",
 * "no scan answered…"; a 409 is "busy", not a failure.
 */
export function useReadingFromTws(readFromTws: () => Promise<unknown>, reloadAfterReading: () => Promise<void>) {
  const [readingState, setReadingState] = useState<ReadingFromTwsState>({ kind: 'idle' });

  const startReading = async () => {
    setReadingState({ kind: 'reading' });
    try {
      await readFromTws();
      await reloadAfterReading();
      setReadingState({ kind: 'done' });
    } catch (error) {
      const message = errorMessageOf(error);
      const anotherReadingRuns = error instanceof ApiError && error.status === ANOTHER_READING_RUNS;
      setReadingState(anotherReadingRuns ? { kind: 'busy', message } : { kind: 'failed', message });
    }
  };

  return { readingState, startReading };
}
