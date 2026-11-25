import { useEffect, useState } from 'react';

const MINUTE_MS = 60_000;

/** The time now, renewed every minute: what keeps "Synced 12 min ago" true while the page stays open. */
export function useCurrentMinute(): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), MINUTE_MS);
    return () => window.clearInterval(timer);
  }, []);
  return now;
}
