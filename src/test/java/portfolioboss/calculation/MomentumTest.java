package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The momentum score of AI_ANALYSIS_TODO.md, decision 5, on made-up series of closes. A series has one close per
 * calendar day from {@link #START}, so the 250 closes of a year-long test end on 2026-05-08, and a month before that is
 * 2026-04-08 — the 220th close.
 */
class MomentumTest {

    private static final LocalDate START = LocalDate.of(2025, 9, 1);
    private static final int YEAR_OF_CLOSES = 250;
    /** SPY gains 0.3% a day in most tests: about 9.4% a month. */
    private final List<DailyClose> spyRisingFast = series(YEAR_OF_CLOSES, day -> 100 * Math.pow(1.003, day));

    @Test
    void aSteadyRiseThatBeatsTheMarketScoresFive() {
        List<DailyClose> spyRisingSlowly = series(YEAR_OF_CLOSES, day -> 100 * Math.pow(1.001, day));

        Momentum momentum = Momentum.of(series(YEAR_OF_CLOSES, day -> 100 * Math.pow(1.003, day)), spyRisingSlowly);

        assertThat(momentum.asOf()).isEqualTo(LocalDate.of(2026, 5, 8));
        assertThat(momentum.aboveSma20()).isTrue();
        assertThat(momentum.aboveSma50()).isTrue();
        assertThat(momentum.sma50AboveSma200()).isTrue();
        assertThat(momentum.nearHigh()).isTrue();
        assertThat(momentum.percentBelowHigh()).isCloseTo(0, within(1e-9));   // the last close is the high
        assertThat(momentum.beatsSpy()).isTrue();
        assertThat(momentum.score()).isEqualTo(5);
        assertThat(momentum.label()).isEqualTo(MomentumLabel.STRONG);
    }

    /** 2% down every day: 32% below the 20-day high, under every average, behind a rising SPY. */
    @Test
    void aSteadyFallScoresZero() {
        Momentum momentum = Momentum.of(series(YEAR_OF_CLOSES, day -> 100 * Math.pow(0.98, day)), spyRisingFast);

        assertThat(momentum.aboveSma20()).isFalse();
        assertThat(momentum.aboveSma50()).isFalse();
        assertThat(momentum.sma50AboveSma200()).isFalse();
        assertThat(momentum.nearHigh()).isFalse();
        assertThat(momentum.beatsSpy()).isFalse();
        assertThat(momentum.score()).isZero();
        assertThat(momentum.label()).isEqualTo(MomentumLabel.WEAK);
    }

    /**
     * Decision 5's example: flat at 100, a sharp rally to 140 over the last 48 days, then 12% down in two days (131.6,
     * then 123.2). Under the 20-day average (132.365), 12% below the high, +5.6% in a month against SPY's +9.4% — but
     * the sharp rally left the 50-day average low (120.696), and it is still above the 200-day one (105.174).
     */
    @Test
    void aTwoDayDropAfterASharpRallyFallsToNeutral() {
        Momentum momentum = Momentum.of(series(YEAR_OF_CLOSES, this::sharpRallyThenDrop), spyRisingFast);

        assertThat(momentum.lastClose()).isCloseTo(123.2, within(1e-9));
        assertThat(momentum.sma20()).isCloseTo(132.365, within(1e-9));    // (2,392.5 + 131.6 + 123.2) / 20
        assertThat(momentum.sma50()).isCloseTo(120.696, within(1e-9));    // (5,780 + 254.8) / 50
        assertThat(momentum.sma200()).isCloseTo(105.174, within(1e-9));   // (150 × 100 + 6,034.8) / 200
        assertThat(momentum.high20()).isCloseTo(140, within(1e-9));
        assertThat(momentum.percentBelowHigh()).isCloseTo(12, within(1e-9));
        assertThat(momentum.oneMonthReturnPercent()).isCloseTo(5.6, within(1e-9));   // 123.2 / 116.67 on 2026-04-08
        assertThat(momentum.aboveSma20()).isFalse();
        assertThat(momentum.aboveSma50()).isTrue();
        assertThat(momentum.sma50AboveSma200()).isTrue();
        assertThat(momentum.nearHigh()).isFalse();
        assertThat(momentum.beatsSpy()).isFalse();
        assertThat(momentum.score()).isEqualTo(2);
        assertThat(momentum.label()).isEqualTo(MomentumLabel.NEUTRAL);
    }

    /** The same 12% drop after a slow rise (100 to 130 in a year) also breaks the 50-day average: one point left. */
    @Test
    void theSameDropAfterASlowRiseIsWeak() {
        Momentum momentum = Momentum.of(series(YEAR_OF_CLOSES, this::slowRiseThenDrop), spyRisingFast);

        assertThat(momentum.aboveSma20()).isFalse();
        assertThat(momentum.aboveSma50()).isFalse();
        assertThat(momentum.sma50AboveSma200()).isTrue();
        assertThat(momentum.nearHigh()).isFalse();
        assertThat(momentum.beatsSpy()).isFalse();
        assertThat(momentum.score()).isEqualTo(1);
        assertThat(momentum.label()).isEqualTo(MomentumLabel.WEAK);
    }

    @Test
    void exactlyTenPercentBelowTheHighStillHoldsItsHeight() {
        Momentum tenPercentBelow = Momentum.of(series(25, day -> day < 24 ? 100 : 90), spyRisingFast);
        Momentum justMore = Momentum.of(series(25, day -> day < 24 ? 100 : 89.99), spyRisingFast);

        assertThat(tenPercentBelow.percentBelowHigh()).isCloseTo(10, within(1e-9));
        assertThat(tenPercentBelow.nearHigh()).isTrue();
        assertThat(justMore.nearHigh()).isFalse();
    }

    /** Built directly from the figures: all five checks hold, and then they fail one by one. */
    @Test
    void theLabelFollowsTheScore() {
        assertThat(momentumWith(90, 90, 80, 100, 3).score()).isEqualTo(5);
        assertThat(momentumWith(90, 90, 80, 120, 3).score()).isEqualTo(4);   // 17% below the high
        assertThat(momentumWith(90, 90, 80, 120, 6).score()).isEqualTo(3);   // SPY did better
        assertThat(momentumWith(110, 90, 80, 120, 6).score()).isEqualTo(2);  // under the 20-day average
        assertThat(momentumWith(110, 110, 80, 120, 6).score()).isEqualTo(1); // and the 50-day one
        assertThat(momentumWith(110, 110, 120, 120, 6).score()).isZero();    // and the 50 under the 200

        assertThat(momentumWith(90, 90, 80, 100, 3).label()).isEqualTo(MomentumLabel.STRONG);
        assertThat(momentumWith(90, 90, 80, 120, 3).label()).isEqualTo(MomentumLabel.STRONG);
        assertThat(momentumWith(90, 90, 80, 120, 6).label()).isEqualTo(MomentumLabel.NEUTRAL);
        assertThat(momentumWith(110, 90, 80, 120, 6).label()).isEqualTo(MomentumLabel.NEUTRAL);
        assertThat(momentumWith(110, 110, 80, 120, 6).label()).isEqualTo(MomentumLabel.WEAK);
        assertThat(momentumWith(110, 110, 120, 120, 6).label()).isEqualTo(MomentumLabel.WEAK);
    }

    /** A stock listed for less than 200 trading days: no 200-day average, so no score — the other checks still show. */
    @Test
    void fewerThan200ClosesLeaveTheScoreUnknown() {
        Momentum momentum = Momentum.of(series(199, day -> 100 + day), spyRisingFast);

        assertThat(momentum.sma200()).isNull();
        assertThat(momentum.sma50AboveSma200()).isNull();
        assertThat(momentum.aboveSma20()).isTrue();
        assertThat(momentum.aboveSma50()).isTrue();
        assertThat(momentum.nearHigh()).isTrue();
        assertThat(momentum.score()).isNull();
        assertThat(momentum.label()).isNull();
    }

    @Test
    void withoutSpyTheScoreIsUnknown() {
        Momentum momentum = Momentum.of(series(YEAR_OF_CLOSES, day -> 100 + day), List.of());

        assertThat(momentum.oneMonthReturnPercent()).isNotNull();
        assertThat(momentum.spyOneMonthReturnPercent()).isNull();
        assertThat(momentum.beatsSpy()).isNull();
        assertThat(momentum.score()).isNull();
        assertThat(momentum.label()).isNull();
    }

    /**
     * Monday 2026-10-05 against a month earlier, Saturday 2026-09-05: the Friday before it, 2026-09-04, is the start —
     * not the Monday after. The closes come in no particular order.
     */
    @Test
    void aMonthBackOnAWeekendStartsFromTheTradingDayBefore() {
        List<DailyClose> closes = List.of(
                new DailyClose(LocalDate.of(2026, 10, 5), 110),
                new DailyClose(LocalDate.of(2026, 9, 4), 100),
                new DailyClose(LocalDate.of(2026, 9, 7), 200));
        List<DailyClose> spyCloses = List.of(
                new DailyClose(LocalDate.of(2026, 9, 4), 500),
                new DailyClose(LocalDate.of(2026, 10, 5), 505));

        Momentum momentum = Momentum.of(closes, spyCloses);

        assertThat(momentum.asOf()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(momentum.oneMonthReturnPercent()).isCloseTo(10, within(1e-9));
        assertThat(momentum.spyOneMonthReturnPercent()).isCloseTo(1, within(1e-9));
        assertThat(momentum.beatsSpy()).isTrue();
    }

    @Test
    void closesThatDoNotReachBackAMonthHaveNoMonthlyReturn() {
        Momentum momentum = Momentum.of(series(20, day -> 100 + day), spyRisingFast);

        assertThat(momentum.oneMonthReturnPercent()).isNull();
        assertThat(momentum.beatsSpy()).isNull();
    }

    @Test
    void noClosesNoMomentum() {
        assertThat(Momentum.of(List.of(), spyRisingFast)).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** One close per calendar day from {@link #START}, the price given by the day's index. */
    private List<DailyClose> series(int days, IntToDoubleFunction priceOfDay) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            closes.add(new DailyClose(START.plusDays(day), priceOfDay.applyAsDouble(day)));
        }
        return closes;
    }

    /** Days 0–199 at 100, 200–247 up to 140 in equal steps, then 6% and 12% below 140. */
    private double sharpRallyThenDrop(int day) {
        if (day < 200) {
            return 100;
        }
        if (day <= 247) {
            return 100 + (day - 199) * 40.0 / 48;
        }
        return day == 248 ? 140 * 0.94 : 140 * 0.88;
    }

    /** Days 0–247 from 100 up to 130 in equal steps, then 6% and 12% below 130. */
    private double slowRiseThenDrop(int day) {
        if (day <= 247) {
            return 100 + day * 30.0 / 247;
        }
        return day == 248 ? 130 * 0.94 : 130 * 0.88;
    }

    /** A last close of 100 and a month's return of +5%, against the given figures. */
    private Momentum momentumWith(double sma20, double sma50, double sma200, double high20,
                                  double spyOneMonthReturnPercent) {
        return new Momentum(LocalDate.of(2026, 10, 6), 100, sma20, sma50, sma200, high20, 5.0,
                spyOneMonthReturnPercent);
    }
}
