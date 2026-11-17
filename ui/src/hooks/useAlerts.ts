import { useCallback, useEffect, useState } from 'react';
import type { Alerts } from '../types/alerts';

/**
 * Loads the bell's alerts and the settings on mount, and again whenever `portfolioSyncedAt` — the portfolio's `asOf` —
 * changes: the server checks the alerts on every sync, the startup's and Sync now's. `reload` loads them again after the
 * bell is opened and after the settings are saved. `null` until the first answer, and when the API couldn't be reached
 * (the top bar already says so); the previous alerts stay in place while reloading.
 */
export function useAlerts(portfolioSyncedAt: string | null) {
  const [alerts, setAlerts] = useState<Alerts | null>(null);

  const loadAlerts = useCallback(async () => {
    try {
      const response = await fetch('/api/alerts');
      if (response.ok) {
        setAlerts((await response.json()) as Alerts);
      }
    } catch {
      // the portfolio's load says the API can't be reached; the bell keeps what it had
    }
  }, []);

  // `portfolioSyncedAt` is not read inside: it is listed so that a new sync runs the load again.
  useEffect(() => {
    void loadAlerts();
  }, [loadAlerts, portfolioSyncedAt]);

  return { alerts, reload: loadAlerts };
}
