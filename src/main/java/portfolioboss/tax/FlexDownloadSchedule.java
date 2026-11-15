package portfolioboss.tax;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which periods of IB's Flex reports a sync downloads (CAPITAL_GAINS_TAX_TODO.md, session 1.4): the days not stored
 * yet, up to the last weekday before today — IB prepares a report once a day, so today's trades aren't in it yet —, in
 * periods of at most 365 days, IB's limit, the oldest first.
 * <ul>
 *   <li>Nothing stored yet: from the first of January of the year of the earliest trade typed in by hand — at the latest
 *       the earliest buy still held, which the tax's FIFO needs —; without any typed trade, the last 365 days.</li>
 *   <li>Stored before: from a week before the last day stored, so that a row IB added or corrected a few days late is
 *       read again. A stored row with the same IB id is replaced, never doubled.</li>
 *   <li>Stored up to the last weekday: nothing — so only a day's first sync waits for IB to prepare a report.</li>
 * </ul>
 *
 * <p>A period starts and ends on a weekday: IB refuses one that starts or ends on a Saturday or a Sunday with 1003
 * "Statement is not available" — all four such periods of the first live runs were refused (2026-11-15), and the user
 * confirmed the rule by hand on IB's site. A weekend at a period's start is skipped rather than read: IB books nothing on
 * it. The market's holidays are not known; a period that starts or ends on one may be refused too.
 *
 * @param lastStoredDate         the last day of the stored reports; {@code null} before the first download
 * @param earliestTypedTradeDate the date of the earliest trade typed in by hand, of a holding or of a manual position;
 *                               {@code null} without any
 * @param today                  the sync's day
 */
record FlexDownloadSchedule(LocalDate lastStoredDate, LocalDate earliestTypedTradeDate, LocalDate today) {

    /** IB's limit: a report covers at most 365 days, both ends included. Not a calendar year: a leap year has 366. */
    private static final int MAX_DAYS_PER_REPORT = 365;
    /** How far back from the last day stored an update starts. */
    private static final int DAYS_READ_AGAIN = 7;

    protected List<FlexReportPeriod> periodsDue() {
        LocalDate lastDayToRead = latestWeekdayOnOrBefore(today.minusDays(1));
        if (lastStoredDate != null && !lastStoredDate.isBefore(lastDayToRead)) {
            return List.of();
        }
        List<FlexReportPeriod> periodsDue = new ArrayList<>();
        LocalDate periodStart = firstWeekdayOnOrAfter(firstDayDue());
        while (!periodStart.isAfter(lastDayToRead)) {
            LocalDate periodEnd =
                    latestWeekdayOnOrBefore(earlierOf(periodStart.plusDays(MAX_DAYS_PER_REPORT - 1), lastDayToRead));
            periodsDue.add(new FlexReportPeriod(periodStart, periodEnd));
            periodStart = firstWeekdayOnOrAfter(periodEnd.plusDays(1));
        }
        return periodsDue;
    }

    private LocalDate firstDayDue() {
        if (lastStoredDate != null) {
            return lastStoredDate.minusDays(DAYS_READ_AGAIN);
        }
        if (earliestTypedTradeDate != null) {
            return earliestTypedTradeDate.withDayOfYear(1);
        }
        return today.minusDays(MAX_DAYS_PER_REPORT);
    }

    /** The date itself on a weekday; on a Saturday or a Sunday, the Friday before. */
    private LocalDate latestWeekdayOnOrBefore(LocalDate date) {
        LocalDate weekday = date;
        while (isWeekend(weekday)) {
            weekday = weekday.minusDays(1);
        }
        return weekday;
    }

    /** The date itself on a weekday; on a Saturday or a Sunday, the Monday after. */
    private LocalDate firstWeekdayOnOrAfter(LocalDate date) {
        LocalDate weekday = date;
        while (isWeekend(weekday)) {
            weekday = weekday.plusDays(1);
        }
        return weekday;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private LocalDate earlierOf(LocalDate oneDate, LocalDate otherDate) {
        return oneDate.isBefore(otherDate) ? oneDate : otherDate;
    }
}
