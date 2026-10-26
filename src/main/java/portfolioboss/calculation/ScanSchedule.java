package portfolioboss.calculation;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When {@code ./run.sh} scans for hot stocks again (RECOMMENDATIONS_TODO.md, decision 3): once a trading day — only when
 * the US market has closed since the last scan, since before that IB has nothing new to rank. Measured in New York, not
 * in the local calendar: the market closes late in the evening here, and a morning scan would otherwise block the one
 * after a whole trading day. The market's holidays are not known, so a holiday counts as a trading day: one extra scan,
 * the same as the day before.
 *
 * @param lastScannedAt when the latest stored scan ran; {@code null} before the first one
 * @param now           the moment of the check
 */
public record ScanSchedule(Instant lastScannedAt, Instant now) {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

    /** Due before the first scan, and when the latest market close came after the last scan. */
    public boolean isScanDue() {
        return lastScannedAt == null || lastScannedAt.isBefore(latestMarketClose().toInstant());
    }

    /** The latest weekday 16:00 in New York at or before {@code now}. */
    public ZonedDateTime latestMarketClose() {
        ZonedDateTime nowInNewYork = now.atZone(NEW_YORK);
        LocalDate closeDate = nowInNewYork.toLocalTime().isBefore(MARKET_CLOSE)
                ? nowInNewYork.toLocalDate().minusDays(1)
                : nowInNewYork.toLocalDate();
        while (isWeekend(closeDate)) {
            closeDate = closeDate.minusDays(1);
        }
        return closeDate.atTime(MARKET_CLOSE).atZone(NEW_YORK);
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
