import { useCallback, useEffect, useState } from 'react';
import type { PortfolioSnapshot } from '../types/portfolio';

const API_UNREACHABLE_MESSAGE =
  'Could not read the portfolio from the PortfolioBoss API. Is ./run.sh running, with TWS logged in?';

/** Loads the portfolio snapshot once on mount; `retry` loads it again (e.g. after starting the API). */
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

  return { snapshot, errorMessage, isLoading, retry: loadSnapshot };
}
