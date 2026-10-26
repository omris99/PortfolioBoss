package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Once a trading day, by the New York close (RECOMMENDATIONS_TODO.md, decision 3). The times are in UTC: in October 2026
 * New York is 4 hours behind it, so the 16:00 close is 20:00 UTC — and from 1 November, 5 hours behind, 21:00 UTC. The
 * week runs from Monday 19 October to Friday 23 October.
 */
class ScanScheduleTest {

    @Test
    void theFirstScanIsAlwaysDue() {
        assertThat(new ScanSchedule(null, Instant.parse("2026-10-24T12:00:00Z")).isScanDue()).isTrue();
    }

    /** Scanned on Tuesday at 03:00 in New York: until that day's close IB has nothing newer. */
    @Test
    void aMorningScanIsEnoughUntilTheMarketCloses() {
        Instant tuesdayMorningScan = Instant.parse("2026-10-20T07:00:00Z");

        assertThat(new ScanSchedule(tuesdayMorningScan, Instant.parse("2026-10-20T19:59:00Z")).isScanDue()).isFalse();
        assertThat(new ScanSchedule(tuesdayMorningScan, Instant.parse("2026-10-20T20:00:00Z")).isScanDue()).isTrue();
    }

    /** Scanned on Friday after the close: no new scan on Saturday, on Sunday, or on Monday before its close. */
    @Test
    void noNewScanOverTheWeekend() {
        Instant fridayEveningScan = Instant.parse("2026-10-23T20:30:00Z");

        assertThat(new ScanSchedule(fridayEveningScan, Instant.parse("2026-10-24T12:00:00Z")).isScanDue()).isFalse();
        assertThat(new ScanSchedule(fridayEveningScan, Instant.parse("2026-10-25T12:00:00Z")).isScanDue()).isFalse();
        assertThat(new ScanSchedule(fridayEveningScan, Instant.parse("2026-10-26T07:00:00Z")).isScanDue()).isFalse();
    }

    /** Scanned on Friday during the session: Friday's close came after it, so the weekend scans once. */
    @Test
    void aScanDuringFridaysSessionIsFollowedByOneAfterItsClose() {
        Instant fridaySessionScan = Instant.parse("2026-10-23T14:00:00Z");

        assertThat(new ScanSchedule(fridaySessionScan, Instant.parse("2026-10-24T12:00:00Z")).isScanDue()).isTrue();
    }

    @Test
    void theLatestCloseOfAWeekendIsFridays() {
        ScanSchedule onSunday = new ScanSchedule(null, Instant.parse("2026-10-25T12:00:00Z"));

        assertThat(onSunday.latestMarketClose().toInstant()).isEqualTo(Instant.parse("2026-10-23T20:00:00Z"));
    }

    /** New York's clocks go back on 1 November 2026: the close is then 21:00 UTC. */
    @Test
    void theCloseFollowsNewYorksClockChange() {
        ScanSchedule mondayAfterTheChange = new ScanSchedule(null, Instant.parse("2026-11-02T21:30:00Z"));

        assertThat(mondayAfterTheChange.latestMarketClose().toInstant()).isEqualTo(Instant.parse("2026-11-02T21:00:00Z"));
    }
}
