import { formatDayOf, formatTimeOf } from '../../lib/format';

const MINUTE_MS = 60_000;
const DAY_MS = 86_400_000;

/** Past this many days the sentence gives the date itself. */
const DAYS_TOLD_AS_A_COUNT = 30;

/** Whole calendar days from one moment's day to another's, in this computer's time zone: 23:50 to 00:10 is one day. */
function calendarDaysBetween(earlier: Date, later: Date): number {
  const earlierDay = Date.UTC(earlier.getFullYear(), earlier.getMonth(), earlier.getDate());
  const laterDay = Date.UTC(later.getFullYear(), later.getMonth(), later.getDate());
  return Math.round((laterDay - earlierDay) / DAY_MS);
}

/**
 * When the figures were read from TWS, in words, for the top bar (DESIGN_BRIEF.md §11, round 6): "Synced just now",
 * "Synced 12 min ago", "Synced today at 19:10", "Synced yesterday at 19:10", "Synced 2 days ago", then "Synced on
 * 02/09/2026". The exact time is in the bar's hint.
 */
export function syncSentence(syncedAt: Date, now: Date): string {
  const minutesAgo = Math.round((now.getTime() - syncedAt.getTime()) / MINUTE_MS);
  const daysAgo = calendarDaysBetween(syncedAt, now);
  if (minutesAgo < 1) return 'Synced just now';
  if (minutesAgo < 60) return `Synced ${minutesAgo} min ago`;
  if (daysAgo === 0) return `Synced today at ${formatTimeOf(syncedAt)}`;
  if (daysAgo === 1) return `Synced yesterday at ${formatTimeOf(syncedAt)}`;
  if (daysAgo < DAYS_TOLD_AS_A_COUNT) return `Synced ${daysAgo} days ago`;
  return `Synced on ${formatDayOf(syncedAt)}`;
}
