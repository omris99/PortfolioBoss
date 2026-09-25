import { useCallback, useEffect, useState } from 'react';
import type { PortfolioSnapshot } from '../types/portfolio';

// The API serves the last sync even with TWS off, so TWS is not the thing to check here.
const API_UNREACHABLE_MESSAGE = 'Could not read the portfolio from the PortfolioBoss API. Is ./run.sh running?';

/**
 * Loads the portfolio snapshot once on mount. `reload` loads it again: after a failed load, and after every write
 * (sector, trades), since the API derives the dates and holding period on the server. The previous snapshot stays
 * in place while reloading, so the table does not disappear.
 */
export function usePortfolio() {
  const [snapshot, setSnapshot] = useState<PortfolioSnapshot | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadSnapshot = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/portfolio');
      if (!response.ok) {
        throw new Error(`The API answered HTTP ${response.status}`);
      }
      setSnapshot((await response.json()) as PortfolioSnapshot);
      setErrorMessage(null);
    } catch {
      setErrorMessage(API_UNREACHABLE_MESSAGE);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSnapshot();
  }, [loadSnapshot]);

  return { snapshot, errorMessage, isLoading, reload: loadSnapshot };
}
