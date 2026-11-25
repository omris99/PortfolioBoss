import { formatDayOf, formatTimeOf } from '../../lib/format';

/** Whether two moments fall on the same calendar day, in this computer's time zone. */
function isSameCalendarDay(firstMoment: Date, secondMoment: Date): boolean {
  return (
    firstMoment.getFullYear() === secondMoment.getFullYear() &&
    firstMoment.getMonth() === secondMoment.getMonth() &&
    firstMoment.getDate() === secondMoment.getDate()
  );
}

/** The same time of day, a calendar day earlier. */
function theDayBefore(moment: Date): Date {
  const dayBefore = new Date(moment);
  dayBefore.setDate(moment.getDate() - 1);
  return dayBefore;
}

/**
 * When an alert came, at a row's right (round 6, the design system's AlertsPanel): "08:15" today, "Yesterday", or the
 * day, "22/11/2026" — counted in calendar days, not in hours.
 *
 * @param createdAt the alert's instant, as the API sends it
 * @param now       the time now (`useCurrentMinute`)
 */
export function alertTimeText(createdAt: string, now: Date): string {
  const created = new Date(createdAt);
  if (isSameCalendarDay(created, now)) return formatTimeOf(created);
  if (isSameCalendarDay(created, theDayBefore(now))) return 'Yesterday';
  return formatDayOf(created);
}
