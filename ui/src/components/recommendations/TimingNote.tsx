import { CalendarClock } from 'lucide-react';
import type { EarningsDate } from '../../types/recommendations';

const MILLISECONDS_IN_A_DAY = 86_400_000;

/** When in the day the report comes, in words; `null` while Finnhub doesn't know. */
const EARNINGS_HOURS: Record<string, string> = {
  bmo: 'before the open',
  amc: 'after the close',
  dmh: 'during the session',
};

export function earningsHourText(earningsDate: EarningsDate): string | null {
  return earningsDate.hour === null ? null : (EARNINGS_HOURS[earningsDate.hour] ?? null);
}

/** "2026-11-12, after the close". */
export function earningsText(earningsDate: EarningsDate): string {
  const hour = earningsHourText(earningsDate);
  return hour === null ? earningsDate.date : `${earningsDate.date}, ${hour}`;
}

/** Whole days from today to a 'yyyy-MM-dd' date, on the calendar: 0 today, 1 tomorrow, below 0 before today. */
function daysFromToday(isoDate: string): number {
  const [year, month, day] = isoDate.split('-').map(Number);
  const now = new Date();
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((Date.UTC(year, month - 1, day) - today) / MILLISECONDS_IN_A_DAY);
}

/** "Earnings in 6 days", "Earnings tomorrow", "Earnings today" — or, for a scan read days later, "Earnings were due". */
function whenText(isoDate: string): string {
  const days = daysFromToday(isoDate);
  if (days < 0) return 'Earnings were due';
  if (days === 0) return 'Earnings today';
  if (days === 1) return 'Earnings tomorrow';
  return `Earnings in ${days} days`;
}

/**
 * Earnings within 14 days of the scan: "Earnings in 6 days: wait for the report of 2026-10-30" — `showsDateOnly`, in a
 * table's Earnings slot: "2026-10-30", the rest on hover. A timing note, not a risk sign: it never lowers a stock, so it
 * is outlined in the text color, never in the warning tone (DESIGN_BRIEF.md §8.5).
 */
export function TimingNote({ earningsDate, showsDateOnly }: { earningsDate: EarningsDate; showsDateOnly: boolean }) {
  const fullText = `${whenText(earningsDate.date)}: wait for the report of ${earningsText(earningsDate)}`;
  return (
    <span
      title={`${fullText}. A timing note, not a risk sign: it never lowers a stock.`}
      className={`inline-flex items-center gap-1 rounded-sm border border-strong bg-surface font-medium ${
        showsDateOnly ? 'h-[18px] whitespace-nowrap px-1 text-[11px]' : 'min-h-5 px-1.5 py-px text-caption'
      }`}
    >
      <CalendarClock size={showsDateOnly ? 12 : 14} className="shrink-0 text-muted" aria-hidden="true" />
      {showsDateOnly ? earningsDate.date : fullText}
    </span>
  );
}
