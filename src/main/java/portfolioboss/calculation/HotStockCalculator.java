package portfolioboss.calculation;

import portfolioboss.model.HotStockLabel;

import java.util.Comparator;

/**
 * Whether a scanned stock is hot (RECOMMENDATIONS_TODO.md, decision 1). First a long upward trend: the last close above
 * the 200-day average, and the 50-day average above the 200-day one. Then where the last close stands against the
 * 50-day average decides the list: the trend is what makes a stock interesting, how far it has stretched decides
 * whether to look now or wait. A rule of its own, from the same closes as the momentum score, which it leaves as it is.
 *
 * @param momentum the stock's figures on its last close
 */
public record HotStockCalculator(Momentum momentum) {

    /** Up to 15% above the 50-day average is HOT, more is TOO_HOT — an opening number (decision 1). */
    private static final double MOST_PERCENT_ABOVE_SMA50_FOR_HOT = 15;
    /** 5–15% below the year's highest close: a small pullback behind it, the trend whole. Opening numbers too. */
    private static final double LEAST_PERCENT_BELOW_HIGH_52_WEEKS_FOR_PULLED_BACK = 5;
    private static final double MOST_PERCENT_BELOW_HIGH_52_WEEKS_FOR_PULLED_BACK = 15;
    /** So that exactly 15% (or 5%) counts despite floating-point rounding, as in {@link Momentum}. */
    private static final double ROUNDING_TOLERANCE = 1e-9;

    /** {@code null} without a long upward trend, or without the 200 closes to know: then the stock is in no list. */
    public HotStockLabel label() {
        if (!Boolean.TRUE.equals(hasLongUpwardTrend())) {
            return null;
        }
        double percentAboveSma50 = percentAboveSma50();
        if (percentAboveSma50 < 0) {
            return HotStockLabel.CORRECTING;
        }
        return percentAboveSma50 <= MOST_PERCENT_ABOVE_SMA50_FOR_HOT + ROUNDING_TOLERANCE
                ? HotStockLabel.HOT
                : HotStockLabel.TOO_HOT;
    }

    /** The last close above the 200-day average, and the 50-day one above it; {@code null} under 200 closes. */
    public Boolean hasLongUpwardTrend() {
        if (momentum.sma50() == null || momentum.sma200() == null) {
            return null;
        }
        return momentum.lastClose() > momentum.sma200() && momentum.sma50AboveSma200();
    }

    /** How far the last close is above the 50-day average, in percent — below 0 when it is under it. */
    public Double percentAboveSma50() {
        return momentum.sma50() == null ? null : (momentum.lastClose() / momentum.sma50() - 1) * 100;
    }

    /** Only within HOT: 5–15% below the year's highest close (both inclusive). */
    public boolean pulledBack() {
        double percentBelowHigh52Weeks = momentum.percentBelowHigh52Weeks();
        return label() == HotStockLabel.HOT
                && percentBelowHigh52Weeks >= LEAST_PERCENT_BELOW_HIGH_52_WEEKS_FOR_PULLED_BACK - ROUNDING_TOLERANCE
                && percentBelowHigh52Weeks <= MOST_PERCENT_BELOW_HIGH_52_WEEKS_FOR_PULLED_BACK + ROUNDING_TOLERANCE;
    }

    /**
     * Lower than a year ago — or than its first close, for a stock that has traded less than a year. A mark, not a
     * filter: after a crash the 200-day average sinks with the price, so a rebound passes the long-trend check fast.
     * {@code null} when the year's return is unknown.
     */
    public Boolean recovering() {
        Double yearReturnPercent = momentum.yearReturnPercent();
        return yearReturnPercent == null ? null : yearReturnPercent < 0;
    }

    /**
     * The order on the page, usable as a {@code Comparator} ({@code HotStockCalculator::comparePlaceInList}): the lists
     * in the order of {@link HotStockLabel}, the stocks in no list last. Within HOT, the ones that pulled back first,
     * then the higher momentum score (an unknown one last), then the less stretched above the 50-day average (decision
     * 1). Within TOO_HOT and CORRECTING, the closest to the 50-day average first: the nearest to becoming HOT.
     */
    public int comparePlaceInList(HotStockCalculator otherStock) {
        Comparator<HotStockLabel> listOrder = Comparator.nullsLast(Comparator.naturalOrder());
        int listComparison = listOrder.compare(label(), otherStock.label());
        if (listComparison != 0 || label() == null) {
            return listComparison;
        }
        Comparator<HotStockCalculator> orderWithinList =
                label() == HotStockLabel.HOT ? orderWithinHot() : closestToSma50First();
        return orderWithinList.compare(this, otherStock);
    }

    private Comparator<HotStockCalculator> orderWithinHot() {
        // false sorts before true, so "not pulled back" puts the ones that did pull back first
        Comparator<HotStockCalculator> pulledBackFirst = Comparator.comparing(stock -> !stock.pulledBack());
        Comparator<HotStockCalculator> higherScoreFirst = Comparator.comparing(
                stock -> stock.momentum().score(), Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<HotStockCalculator> lessStretchedFirst = Comparator.comparing(HotStockCalculator::percentAboveSma50);
        return pulledBackFirst.thenComparing(higherScoreFirst).thenComparing(lessStretchedFirst);
    }

    private Comparator<HotStockCalculator> closestToSma50First() {
        return Comparator.comparing(stock -> Math.abs(stock.percentAboveSma50()));
    }
}
