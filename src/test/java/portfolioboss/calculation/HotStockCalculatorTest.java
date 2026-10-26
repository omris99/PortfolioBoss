package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.model.HotStockLabel;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The rule of RECOMMENDATIONS_TODO.md, decision 1, on figures made up directly. Every stock closes at 100, above its
 * 20-day average and at its 20-day high, +5% in a month — so above the 50-day average and with the long trend it scores
 * 5 against SPY's +3%, and 4 against SPY's +6%.
 */
class HotStockCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 16);
    /** Under every 50-day average below, so the long trend holds unless a test says otherwise. */
    private static final double SMA200_UNDER_THE_TREND = 80;
    private static final double SPY_BEHIND_THE_STOCK = 3;
    private static final double SPY_AHEAD_OF_THE_STOCK = 6;

    @Test
    void upTo15PercentAboveThe50DayAverageIsHot() {
        HotStockCalculator atTheAverage = stockWith(100);
        HotStockCalculator exactly15PercentAbove = stockWith(100 / 1.15);
        HotStockCalculator justOver15PercentAbove = stockWith(100 / 1.1501);
        HotStockCalculator justUnderTheAverage = stockWith(100.01);

        assertThat(atTheAverage.percentAboveSma50()).isCloseTo(0, within(1e-9));
        assertThat(atTheAverage.label()).isEqualTo(HotStockLabel.HOT);
        assertThat(exactly15PercentAbove.percentAboveSma50()).isCloseTo(15, within(1e-9));
        assertThat(exactly15PercentAbove.label()).isEqualTo(HotStockLabel.HOT);
        assertThat(justOver15PercentAbove.label()).isEqualTo(HotStockLabel.TOO_HOT);
        assertThat(justUnderTheAverage.percentAboveSma50()).isNegative();
        assertThat(justUnderTheAverage.label()).isEqualTo(HotStockLabel.CORRECTING);
    }

    @Test
    void withoutTheLongTrendTheStockIsInNoList() {
        HotStockCalculator closeUnderThe200DayAverage = stock(102, 101.0, 100, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator fiftyDayUnderThe200Day = stock(90, 95.0, 100, SPY_BEHIND_THE_STOCK, 20.0);

        assertThat(closeUnderThe200DayAverage.hasLongUpwardTrend()).isFalse();
        assertThat(closeUnderThe200DayAverage.label()).isNull();
        assertThat(fiftyDayUnderThe200Day.hasLongUpwardTrend()).isFalse();
        assertThat(fiftyDayUnderThe200Day.label()).isNull();
    }

    /** A stock with fewer than 200 closes has no 200-day average: no way to know the trend, so no list. */
    @Test
    void without200ClosesTheTrendIsUnknown() {
        HotStockCalculator youngStock = stock(95, null, 100, SPY_BEHIND_THE_STOCK, 20.0);

        assertThat(youngStock.hasLongUpwardTrend()).isNull();
        assertThat(youngStock.label()).isNull();
        assertThat(youngStock.pulledBack()).isFalse();
    }

    @Test
    void pulledBackIs5To15PercentBelowTheYearHigh() {
        assertThat(hotStockWithHigh52Weeks(100 / 0.95).pulledBack()).isTrue();     // exactly 5% below
        assertThat(hotStockWithHigh52Weeks(100 / 0.85).pulledBack()).isTrue();     // exactly 15% below
        assertThat(hotStockWithHigh52Weeks(100 / 0.951).pulledBack()).isFalse();   // 4.9%: still near the high
        assertThat(hotStockWithHigh52Weeks(100 / 0.849).pulledBack()).isFalse();   // 15.1%: more than a small pullback
        assertThat(hotStockWithHigh52Weeks(100).pulledBack()).isFalse();           // at the high
    }

    /** Only HOT stocks pulled back: a TOO_HOT one 10% below its year high is not marked. */
    @Test
    void onlyHotStocksArePulledBack() {
        HotStockCalculator tooHotStock = stock(70, SMA200_UNDER_THE_TREND - 20, 100 / 0.9, SPY_BEHIND_THE_STOCK, 20.0);

        assertThat(tooHotStock.label()).isEqualTo(HotStockLabel.TOO_HOT);
        assertThat(tooHotStock.pulledBack()).isFalse();
    }

    @Test
    void recoveringIsLowerThanAYearAgo() {
        assertThat(stockWithYearReturn(-10.0).recovering()).isTrue();
        assertThat(stockWithYearReturn(0.0).recovering()).isFalse();
        assertThat(stockWithYearReturn(25.0).recovering()).isFalse();
        assertThat(stockWithYearReturn(null).recovering()).isNull();
    }

    /**
     * HOT first — the ones that pulled back (the higher score first), then the rest by score and by how little they
     * stretched —, then TOO_HOT and CORRECTING, each closest to the 50-day average first, and a stock in no list last.
     */
    @Test
    void theOrderOnThePage() {
        HotStockCalculator pulledBackScoringFive = stock(95, SMA200_UNDER_THE_TREND, 110, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator pulledBackScoringFour = stock(95, SMA200_UNDER_THE_TREND, 110, SPY_AHEAD_OF_THE_STOCK, 20.0);
        HotStockCalculator twoPercentAbove = stock(98, SMA200_UNDER_THE_TREND, 101, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator tenPercentAbove = stock(100 / 1.1, SMA200_UNDER_THE_TREND, 100, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator tooHotNearTheAverage = stock(85, SMA200_UNDER_THE_TREND, 100, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator tooHotFarAbove = stock(70, 60.0, 100, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator correctingJustUnder = stock(102, 90.0, 120, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator correctingFarUnder = stock(110, 90.0, 130, SPY_BEHIND_THE_STOCK, 20.0);
        HotStockCalculator withoutTheTrend = stock(90, 120.0, 130, SPY_BEHIND_THE_STOCK, 20.0);

        List<HotStockCalculator> inPageOrder = List.of(withoutTheTrend, correctingFarUnder, tenPercentAbove,
                        tooHotFarAbove, pulledBackScoringFour, correctingJustUnder, twoPercentAbove,
                        tooHotNearTheAverage, pulledBackScoringFive).stream()
                .sorted(HotStockCalculator::comparePlaceInList)
                .toList();

        assertThat(inPageOrder).containsExactly(pulledBackScoringFive, pulledBackScoringFour, twoPercentAbove,
                tenPercentAbove, tooHotNearTheAverage, tooHotFarAbove, correctingJustUnder, correctingFarUnder,
                withoutTheTrend);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private HotStockCalculator stockWith(double sma50) {
        return stock(sma50, SMA200_UNDER_THE_TREND, 100, SPY_BEHIND_THE_STOCK, 20.0);
    }

    /** 5% above its 50-day average: HOT. */
    private HotStockCalculator hotStockWithHigh52Weeks(double high52Weeks) {
        return stock(100 / 1.05, SMA200_UNDER_THE_TREND, high52Weeks, SPY_BEHIND_THE_STOCK, 20.0);
    }

    private HotStockCalculator stockWithYearReturn(Double yearReturnPercent) {
        return stock(95, SMA200_UNDER_THE_TREND, 100, SPY_BEHIND_THE_STOCK, yearReturnPercent);
    }

    /** A last close of 100, a 20-day average of 99 and a 20-day high of 100, +5% in a month, with the given figures. */
    private HotStockCalculator stock(double sma50, Double sma200, double high52Weeks, double spyOneMonthReturnPercent,
                                     Double yearReturnPercent) {
        Momentum momentum = new Momentum(AS_OF, 100, 99.0, sma50, sma200, 100.0, 5.0, spyOneMonthReturnPercent,
                AS_OF.minusYears(1).plusDays(1), high52Weeks, yearReturnPercent, 10);
        return new HotStockCalculator(momentum);
    }
}
