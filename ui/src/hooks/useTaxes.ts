import { useCallback, useEffect, useState } from 'react';
import type { Taxes } from '../types/taxes';

const API_UNREACHABLE_MESSAGE = 'Could not reach the PortfolioBoss API. Is ./run.sh running?';

/**
 * Loads the Taxes page's figures on mount, and again whenever `portfolioSyncedAt` — the portfolio's `asOf` — changes: a
 * sync downloads the days of IB's reports and the Bank of Israel's rates still missing. `reload` loads them again after a
 * confirmation. The API answers 503 until a sync has ever been stored — no sync yet, which is not an error. The previous
 * figures stay in place while reloading.
 */
export function useTaxes(portfolioSyncedAt: string) {
  const [taxes, setTaxes] = useState<Taxes | null>(null);
  const [hasNoSyncYet, setHasNoSyncYet] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadTaxes = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/taxes');
      if (response.status === 503) {
        setTaxes(null);
        setHasNoSyncYet(true);
      } else if (response.ok) {
        setTaxes((await response.json()) as Taxes);
        setHasNoSyncYet(false);
      } else {
        throw new Error(`The API answered HTTP ${response.status}`);
      }
      setErrorMessage(null);
    } catch {
      setErrorMessage(API_UNREACHABLE_MESSAGE);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // `portfolioSyncedAt` is not read inside: it is listed so that a new sync runs the load again.
  useEffect(() => {
    void loadTaxes();
  }, [loadTaxes, portfolioSyncedAt]);

  return { taxes, hasNoSyncYet, errorMessage, isLoading, reload: loadTaxes };
}
