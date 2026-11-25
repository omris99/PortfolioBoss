import { formatDayCount, formatHoldingPeriod } from '../lib/format';
import { Hint } from './Hint';

/** Months are approximate in the short form, so its hint has the exact number of days. */
export function HoldingPeriod({ days }: { days: number | null }) {
  if (days === null) return <span>{formatHoldingPeriod(days)}</span>;
  return <Hint text={formatDayCount(days)}>{formatHoldingPeriod(days)}</Hint>;
}
