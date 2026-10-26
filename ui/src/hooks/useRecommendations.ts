import { useCallback, useEffect, useState } from 'react';
import type { Recommendations } from '../types/recommendations';

const API_UNREACHABLE_MESSAGE = 'Could not reach the PortfolioBoss API. Is ./run.sh running?';

/**
 * Loads the latest scan's lists once on mount; `reload` loads them again, after Scan again. The API answers 503 until a
 * scan has ever been stored — no scan yet, which is not an error. The previous lists stay in place while reloading.
 */
export function useRecommendations() {
  const [recommendations, setRecommendations] = useState<Recommendations | null>(null);
  const [hasNoScanYet, setHasNoScanYet] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const loadRecommendations = useCallback(async () => {
    setIsLoading(true);
    try {
      const response = await fetch('/api/recommendations');
      if (response.status === 503) {
        setRecommendations(null);
        setHasNoScanYet(true);
      } else if (response.ok) {
        setRecommendations((await response.json()) as Recommendations);
        setHasNoScanYet(false);
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

  useEffect(() => {
    void loadRecommendations();
  }, [loadRecommendations]);

  return { recommendations, hasNoScanYet, errorMessage, isLoading, reload: loadRecommendations };
}
