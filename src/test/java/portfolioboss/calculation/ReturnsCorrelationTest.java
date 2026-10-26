package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ib.DailyClose;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The correlation of daily returns of RECOMMENDATIONS_TODO.md, decision 7, on made-up series with one close per calendar
 * day from {@link #START}. The prices wave up and down so that the daily returns differ from day to day.
 */
class ReturnsCorrelationTest {

    private static final LocalDate START = LocalDate.of(2025, 10, 1);
    private static final int YEAR_OF_CLOSES = 250;

    @Test
    void aStockMovesExactlyWithItself() {
        List<DailyClose> closes = wavingCloses(0, YEAR_OF_CLOSES);

        ReturnsCorrelation correlation = new ReturnsCorrelation(closes, closes);

        assertThat(correlation.coefficient()).isCloseTo(1, within(1e-9));
        assertThat(correlation.movesTogether()).isTrue();
    }

    /** Every day the other stock moves by the same percent the other way. */
    @Test
    void oppositeDailyReturnsAreMinusOne() {
        List<DailyClose> closes = wavingCloses(0, YEAR_OF_CLOSES);

        ReturnsCorrelation correlation = new ReturnsCorrelation(closes, oppositeOf(closes));

        assertThat(correlation.coefficient()).isCloseTo(-1, within(1e-9));
        assertThat(correlation.movesTogether()).isFalse();
    }

    /** Days 0–249 against days 151–249 (99 shared dates) and days 150–249 (100). */
    @Test
    void fewerThan100SharedDatesHaveNoFigure() {
        List<DailyClose> closes = wavingCloses(0, YEAR_OF_CLOSES);

        ReturnsCorrelation ninetyNineShared = new ReturnsCorrelation(closes, wavingCloses(151, YEAR_OF_CLOSES));
        ReturnsCorrelation hundredShared = new ReturnsCorrelation(closes, wavingCloses(150, YEAR_OF_CLOSES));

        assertThat(ninetyNineShared.coefficient()).isNull();
        assertThat(ninetyNineShared.movesTogether()).isNull();
        assertThat(hundredShared.coefficient()).isCloseTo(1, within(1e-9));
    }

    /**
     * The other stock has the same prices but no close on every third day: those days are left out of both, and each
     * return runs over the same days in both — so they still move exactly together. Comparing each stock's own
     * consecutive closes instead would set returns of different days side by side.
     */
    @Test
    void aDateMissingFromOneIsLeftOutOfBoth() {
        List<DailyClose> closes = wavingCloses(0, YEAR_OF_CLOSES);
        List<DailyClose> withEveryThirdDayMissing = closes.stream()
                .filter(dailyClose -> ChronoUnit.DAYS.between(START, dailyClose.date()) % 3 != 2)
                .toList();

        ReturnsCorrelation correlation = new ReturnsCorrelation(closes, withEveryThirdDayMissing);

        assertThat(correlation.coefficient()).isCloseTo(1, within(1e-9));
    }

    @Test
    void aPriceThatNeverMovedHasNoFigure() {
        List<DailyClose> flatCloses = new ArrayList<>();
        for (int day = 0; day < YEAR_OF_CLOSES; day++) {
            flatCloses.add(new DailyClose(START.plusDays(day), 50));
        }

        ReturnsCorrelation correlation = new ReturnsCorrelation(wavingCloses(0, YEAR_OF_CLOSES), flatCloses);

        assertThat(correlation.coefficient()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** One close a day from day {@code firstDay} to the day before {@code endDay}, waving between about 90 and 160. */
    private List<DailyClose> wavingCloses(int firstDay, int endDay) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = firstDay; day < endDay; day++) {
            closes.add(new DailyClose(START.plusDays(day), 100 + 10 * Math.sin(day / 3.0) + day * 0.2));
        }
        return closes;
    }

    /** Starts at 50 and moves each day by the same percent as {@code closes}, the other way. */
    private List<DailyClose> oppositeOf(List<DailyClose> closes) {
        List<DailyClose> oppositeCloses = new ArrayList<>();
        double oppositeClose = 50;
        oppositeCloses.add(new DailyClose(closes.getFirst().date(), oppositeClose));
        for (int dayIndex = 1; dayIndex < closes.size(); dayIndex++) {
            double dailyReturn = closes.get(dayIndex).close() / closes.get(dayIndex - 1).close() - 1;
            oppositeClose = oppositeClose * (1 - dailyReturn);
            oppositeCloses.add(new DailyClose(closes.get(dayIndex).date(), oppositeClose));
        }
        return oppositeCloses;
    }
}
