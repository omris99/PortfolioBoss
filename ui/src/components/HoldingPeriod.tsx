import { formatDayCount, formatHoldingPeriod } from '../lib/format';

/** Months are approximate in the short form, so hovering shows the exact number of days. */
export function HoldingPeriod({ days }: { days: number | null }) {
  const exactDayCount = days === null ? undefined : formatDayCount(days);
  return <span title={exactDayCount}>{formatHoldingPeriod(days)}</span>;
}
