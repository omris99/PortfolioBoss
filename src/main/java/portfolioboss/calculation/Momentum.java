package portfolioboss.calculation;

import portfolioboss.ib.DailyClose;
import portfolioboss.model.MomentumLabel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * A stock's momentum on its last close (AI_ANALYSIS_TODO.md, decision 5): five checks on a year of daily closes, a
 * point for each one that holds. The components are the raw figures; the checks, the score and the label are derived
 * from them, so they never fall out of step. A figure there are not enough closes for is {@code null}, and so is every
 * check that needs it — the score and the label exist only when all five checks do. Never a guess.
 *
 * <p>Every average is a simple one (SMA) of closes; a month is a calendar month, not a number of bars. The year's
 * figures — {@code high52Weeks}, {@code yearReturnPercent}, {@code maxDrawdownPercent} — are over every close given,
 * which is a year: what IB sends (RECOMMENDATIONS_TODO.md, session 1, where they also decide whether a scanned stock is
 * hot).
 *
 * @param asOf                     the date of the last close
 * @param sma20                    the average of the last 20 closes ({@code sma50}, {@code sma200} likewise)
 * @param high20                   the highest of the last 20 closes
 * @param oneMonthReturnPercent    the last close against the last close on or before the same day a month earlier
 * @param spyOneMonthReturnPercent SPY over the same month, up to the same date
 * @param firstCloseDate           the date of the first close: about a year back, or later for a stock that has traded
 *                                 less than a year — see {@link #monthsInMarket()}
 * @param high52Weeks              the highest close of the year
 * @param yearReturnPercent        the last close against the first one: over the year, or over the months the stock has
 *                                 traded; {@code null} when the first close is not above 0
 * @param maxDrawdownPercent       the biggest fall from a close to a later, lower one, in percent of the higher one: how
 *                                 far the stock fell at its worst this year (0 if it never fell)
 */
public record Momentum(
        LocalDate asOf,
        double lastClose,
        Double sma20,
        Double sma50,
        Double sma200,
        Double high20,
        Double oneMonthReturnPercent,
        Double spyOneMonthReturnPercent,
        LocalDate firstCloseDate,
        double high52Weeks,
        Double yearReturnPercent,
        double maxDrawdownPercent) {

    /** A close at most 10% below the 20-day high (inclusive) still holds its height. */
    private static final double NEAR_HIGH_RATIO = 0.9;
    /** So that exactly 10% below counts despite floating-point rounding. */
    private static final double ROUNDING_TOLERANCE = 1e-9;
    /**
     * IB's year of closes starts the trading day after the same date a year earlier (in the real database: 17.10.2025
     * for a sync on 16.10.2026), and a weekend or a holiday pushes it a few days later still. A first close up to a week
     * after that date is a full year.
     */
    private static final int FULL_YEAR_TOLERANCE_DAYS = 7;
    private static final int MONTHS_IN_A_YEAR = 12;

    /**
     * {@code null} when there is not a single close: nothing to measure. The two lists may come in any order. Static
     * because it is what builds a {@code Momentum} from closes, like {@code HoldingHistory.of}.
     */
    public static Momentum of(List<DailyClose> closes, List<DailyClose> spyCloses) {
        if (closes.isEmpty()) {
            return null;
        }
        DailyCloses stockCloses = new DailyCloses(closes);
        DailyClose lastClose = stockCloses.last();
        LocalDate oneMonthEarlier = lastClose.date().minusMonths(1);
        return new Momentum(
                lastClose.date(),
                lastClose.close(),
                stockCloses.averageOfLast(20),
                stockCloses.averageOfLast(50),
                stockCloses.averageOfLast(200),
                stockCloses.highestOfLast(20),
                stockCloses.returnPercent(oneMonthEarlier, lastClose.date()),
                new DailyCloses(spyCloses).returnPercent(oneMonthEarlier, lastClose.date()),
                stockCloses.first().date(),
                stockCloses.highestOfAll(),
                stockCloses.returnSinceFirstClosePercent(),
                stockCloses.largestFallFromPeakPercent());
    }

    // ── the five checks ─────────────────────────────────────────────────────────────────────────

    /** Short-term trend: the immediate momentum is positive, not a free fall. */
    public Boolean aboveSma20() {
        return sma20 == null ? null : lastClose > sma20;
    }

    /** Medium-term trend. */
    public Boolean aboveSma50() {
        return sma50 == null ? null : lastClose > sma50;
    }

    /** The broad, year-long trend is up. */
    public Boolean sma50AboveSma200() {
        return sma50 == null || sma200 == null ? null : sma50 > sma200;
    }

    /** Holds its height: at most 10% below the highest close of the last 20 trading days. */
    public Boolean nearHigh() {
        return high20 == null ? null : lastClose >= high20 * NEAR_HIGH_RATIO - ROUNDING_TOLERANCE;
    }

    /** Leads the market now — over the last month, not half a year ago. */
    public Boolean beatsSpy() {
        return oneMonthReturnPercent == null || spyOneMonthReturnPercent == null
                ? null
                : oneMonthReturnPercent > spyOneMonthReturnPercent;
    }

    // ── what the UI shows ───────────────────────────────────────────────────────────────────────

    /** How far the last close is below the 20-day high, in percent (0 at the high). */
    public Double percentBelowHigh() {
        return high20 == null ? null : (1 - lastClose / high20) * 100;
    }

    /** How far the last close is below the year's highest close, in percent (0 at the high). */
    public double percentBelowHigh52Weeks() {
        return (1 - lastClose / high52Weeks) * 100;
    }

    /**
     * How many months {@code yearReturnPercent} covers: 12 for a full year — IB sends no more, so a stock that has
     * traded for ten years shows 12 too —, otherwise the whole months from the first close to the last, the months a
     * young stock has been in the market.
     */
    public int monthsInMarket() {
        LocalDate latestFirstCloseDateOfAFullYear = asOf.minusYears(1).plusDays(FULL_YEAR_TOLERANCE_DAYS);
        if (!firstCloseDate.isAfter(latestFirstCloseDateOfAFullYear)) {
            return MONTHS_IN_A_YEAR;
        }
        return (int) ChronoUnit.MONTHS.between(firstCloseDate, asOf);
    }

    /** How many of the five checks hold; {@code null} if any of them is unknown. */
    public Integer score() {
        List<Boolean> checks = Arrays.asList(aboveSma20(), aboveSma50(), sma50AboveSma200(), nearHigh(), beatsSpy());
        if (checks.contains(null)) {
            return null;
        }
        return (int) checks.stream().filter(Boolean::booleanValue).count();
    }

    public MomentumLabel label() {
        Integer score = score();
        if (score == null) {
            return null;
        }
        if (score >= 4) {
            return MomentumLabel.STRONG;
        }
        return score >= 2 ? MomentumLabel.NEUTRAL : MomentumLabel.WEAK;
    }

    // ── the figures ─────────────────────────────────────────────────────────────────────────────

    /**
     * The closes of one contract, sorted by date once, and the figures {@link #of} takes from them. A figure there are
     * not enough closes for is {@code null}.
     */
    private record DailyCloses(List<DailyClose> byDate) {

        private DailyCloses {
            byDate = byDate.stream().sorted(Comparator.comparing(DailyClose::date)).toList();
        }

        private DailyClose first() {
            return byDate.getFirst();
        }

        private DailyClose last() {
            return byDate.getLast();
        }

        private double highestOfAll() {
            return byDate.stream().mapToDouble(DailyClose::close).max().orElseThrow();
        }

        /** Walks the closes in date order, keeping the highest so far; each close's fall from it is a drawdown. */
        private double largestFallFromPeakPercent() {
            double highestCloseSoFar = first().close();
            double largestFallPercent = 0;
            for (DailyClose dailyClose : byDate) {
                highestCloseSoFar = Math.max(highestCloseSoFar, dailyClose.close());
                if (highestCloseSoFar > 0) {
                    double fallFromHighestPercent = (1 - dailyClose.close() / highestCloseSoFar) * 100;
                    largestFallPercent = Math.max(largestFallPercent, fallFromHighestPercent);
                }
            }
            return largestFallPercent;
        }

        private Double returnSinceFirstClosePercent() {
            return percentChange(first(), last());
        }

        private Double averageOfLast(int count) {
            if (byDate.size() < count) {
                return null;
            }
            return lastCloses(count).stream().mapToDouble(DailyClose::close).average().orElseThrow();
        }

        private Double highestOfLast(int count) {
            if (byDate.size() < count) {
                return null;
            }
            return lastCloses(count).stream().mapToDouble(DailyClose::close).max().orElseThrow();
        }

        private List<DailyClose> lastCloses(int count) {
            return byDate.subList(byDate.size() - count, byDate.size());
        }

        /**
         * From the last close on or before {@code fromDate} to the last close on or before {@code toDate}: a weekend or
         * a holiday takes the trading day before it. {@code null} when the closes don't reach back that far.
         */
        private Double returnPercent(LocalDate fromDate, LocalDate toDate) {
            return percentChange(lastOnOrBefore(fromDate), lastOnOrBefore(toDate));
        }

        /** {@code null} when either close is missing, or the start is not above 0: no return to work out. */
        private Double percentChange(DailyClose startClose, DailyClose endClose) {
            if (startClose == null || endClose == null || startClose.close() <= 0) {
                return null;
            }
            return (endClose.close() / startClose.close() - 1) * 100;
        }

        private DailyClose lastOnOrBefore(LocalDate date) {
            return byDate.stream()
                    .filter(close -> !close.date().isAfter(date))
                    .reduce((earlier, later) -> later)
                    .orElse(null);
        }
    }
}
