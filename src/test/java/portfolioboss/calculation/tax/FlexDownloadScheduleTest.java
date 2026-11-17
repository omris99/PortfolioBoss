package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.flex.FlexReportPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which periods a sync downloads (CAPITAL_GAINS_TAX_TODO.md, session 1.4). The sync is on Sunday 15 November 2026, so
 * the reports reach Friday the 13th: IB has nothing of today's yet, and refuses a period that starts or ends on a
 * Saturday or a Sunday.
 */
class FlexDownloadScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate LAST_WEEKDAY = LocalDate.of(2026, 11, 13);

    /**
     * 2024 is a leap year: its first period of 365 days ends on 30 December, and the next one starts on the 31st — a
     * report of the whole calendar year would be 366 days, over IB's limit.
     */
    @Test
    void theFirstDownloadStartsOnTheFirstOfJanuaryOfTheEarliestTypedTradesYear() {
        List<FlexReportPeriod> periodsDue =
                new FlexDownloadSchedule(null, LocalDate.of(2024, 3, 5), TODAY).periodsDue();

        assertThat(periodsDue).containsExactly(
                new FlexReportPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 30)),
                new FlexReportPeriod(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 12, 30)),
                new FlexReportPeriod(LocalDate.of(2025, 12, 31), LAST_WEEKDAY));
    }

    /** The first of January 2022 was a Saturday. */
    @Test
    void aFirstOfJanuaryOnAWeekendStartsOnTheMondayAfter() {
        List<FlexReportPeriod> periodsDue =
                new FlexDownloadSchedule(null, LocalDate.of(2022, 3, 5), TODAY).periodsDue();

        assertThat(periodsDue.get(0).fromDate()).isEqualTo(LocalDate.of(2022, 1, 3));
    }

    /** IB refuses a period that starts or ends on a Saturday or a Sunday (the user checked it on IB's site). */
    @Test
    void everyPeriodStartsAndEndsOnAWeekday() {
        List<FlexReportPeriod> periodsDue =
                new FlexDownloadSchedule(null, LocalDate.of(2021, 6, 1), TODAY).periodsDue();

        assertThat(periodsDue).allSatisfy(period -> {
            assertThat(isWeekday(period.fromDate())).as("%s starts on a weekday", period).isTrue();
            assertThat(isWeekday(period.toDate())).as("%s ends on a weekday", period).isTrue();
        });
    }

    @Test
    void noPeriodIsLongerThan365DaysBothEndsIncluded() {
        List<FlexReportPeriod> periodsDue =
                new FlexDownloadSchedule(null, LocalDate.of(2021, 6, 1), TODAY).periodsDue();

        assertThat(periodsDue).allSatisfy(period ->
                assertThat(ChronoUnit.DAYS.between(period.fromDate(), period.toDate()) + 1).isLessThanOrEqualTo(365));
    }

    /** 365 days back is Saturday 15 November 2025: the period starts on the Monday after. */
    @Test
    void withoutATypedTradeTheFirstDownloadIsTheLast365Days() {
        assertThat(new FlexDownloadSchedule(null, null, TODAY).periodsDue())
                .containsExactly(new FlexReportPeriod(LocalDate.of(2025, 11, 17), LAST_WEEKDAY));
    }

    /** A row IB added or corrected a few days late is read again; the store replaces it rather than doubling it. */
    @Test
    void anUpdateReadsAgainTheWeekBeforeTheLastDayStored() {
        assertThat(new FlexDownloadSchedule(LocalDate.of(2026, 11, 10), null, TODAY).periodsDue())
                .containsExactly(new FlexReportPeriod(LocalDate.of(2026, 11, 3), LAST_WEEKDAY));
    }

    /** The period IB refused in the live run of 2026-11-15, which ended on Saturday the 14th, now ends on the 13th. */
    @Test
    void anUpdateOnASundayEndsOnTheFridayBefore() {
        assertThat(new FlexDownloadSchedule(LocalDate.of(2025, 11, 28), null, TODAY).periodsDue())
                .containsExactly(new FlexReportPeriod(LocalDate.of(2025, 11, 21), LAST_WEEKDAY));
    }

    @Test
    void theEarliestTypedTradeDoesNotMatterOnceReportsAreStored() {
        assertThat(new FlexDownloadSchedule(LocalDate.of(2026, 11, 10), LocalDate.of(2020, 1, 1), TODAY).periodsDue())
                .containsExactly(new FlexReportPeriod(LocalDate.of(2026, 11, 3), LAST_WEEKDAY));
    }

    /** IB prepares a report once a day: the syncs after the day's first don't wait for it — nor a weekend's syncs. */
    @Test
    void nothingIsDueOnceTheStoredReportsReachTheLastWeekday() {
        assertThat(new FlexDownloadSchedule(LAST_WEEKDAY, null, TODAY).periodsDue()).isEmpty();
    }

    /** A week back from Sunday 1 June 2025 is a Sunday too: the period starts on the Monday after. */
    @Test
    void reportsStoredMoreThanAYearAgoAreDownloadedInSeveralPeriods() {
        assertThat(new FlexDownloadSchedule(LocalDate.of(2025, 6, 1), null, TODAY).periodsDue()).containsExactly(
                new FlexReportPeriod(LocalDate.of(2025, 5, 26), LocalDate.of(2026, 5, 25)),
                new FlexReportPeriod(LocalDate.of(2026, 5, 26), LAST_WEEKDAY));
    }

    /** On the first of January, with the earliest typed trade from that very day, there is no earlier day to read. */
    @Test
    void aFirstTypedTradeFromTodayOnTheFirstOfJanuaryLeavesNothingToRead() {
        LocalDate firstOfJanuary = LocalDate.of(2027, 1, 1);

        assertThat(new FlexDownloadSchedule(null, firstOfJanuary, firstOfJanuary).periodsDue()).isEmpty();
    }

    private boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
