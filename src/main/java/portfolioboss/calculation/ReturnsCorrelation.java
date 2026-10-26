package portfolioboss.calculation;

import portfolioboss.ib.DailyClose;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How closely two stocks move together (RECOMMENDATIONS_TODO.md, decision 7): the correlation of their daily returns —
 * 1 when they move exactly together, 0 when one tells nothing about the other, −1 when they move opposite. Measured
 * only on the dates both have a close: a date one of them lacks (a halt, a stock that started trading later) is left
 * out of both, and each return runs from the previous shared date, so the two are always compared over the same days.
 *
 * @param closes      one stock's daily closes, in any order
 * @param otherCloses the other stock's
 */
public record ReturnsCorrelation(List<DailyClose> closes, List<DailyClose> otherCloses) {

    /** With fewer shared dates than this the figure says little, so there is none. */
    private static final int LEAST_SHARED_DATES = 100;
    /** From here up the two move together: buying one adds little spread to holding the other. */
    private static final double MOVES_TOGETHER_FROM = 0.7;

    /**
     * Pearson's correlation coefficient of the daily returns. {@code null} under 100 shared dates, or when either
     * stock's price never moved on them — a spread of 0 there is nothing to divide by.
     */
    public Double coefficient() {
        Map<LocalDate, Double> closeByDate = closeByDate(closes);
        Map<LocalDate, Double> otherCloseByDate = closeByDate(otherCloses);
        List<LocalDate> sharedDates = closeByDate.keySet().stream()
                .filter(otherCloseByDate::containsKey)
                .sorted()
                .toList();
        if (sharedDates.size() < LEAST_SHARED_DATES) {
            return null;
        }
        return correlationOf(dailyReturnsOn(sharedDates, closeByDate), dailyReturnsOn(sharedDates, otherCloseByDate));
    }

    /** 0.7 or more; {@code null} when the coefficient is. */
    public Boolean movesTogether() {
        Double coefficient = coefficient();
        return coefficient == null ? null : coefficient >= MOVES_TOGETHER_FROM;
    }

    /** Only closes above 0: anything else is not a price, and no return can be worked out from it. */
    private Map<LocalDate, Double> closeByDate(List<DailyClose> dailyCloses) {
        Map<LocalDate, Double> closeByDate = new HashMap<>();
        dailyCloses.stream()
                .filter(dailyClose -> dailyClose.close() > 0)
                .forEach(dailyClose -> closeByDate.put(dailyClose.date(), dailyClose.close()));
        return closeByDate;
    }

    /** From each shared date to the next: one return fewer than there are dates. */
    private List<Double> dailyReturnsOn(List<LocalDate> sharedDates, Map<LocalDate, Double> closeByDate) {
        List<Double> dailyReturns = new ArrayList<>();
        for (int dateIndex = 1; dateIndex < sharedDates.size(); dateIndex++) {
            double previousClose = closeByDate.get(sharedDates.get(dateIndex - 1));
            double close = closeByDate.get(sharedDates.get(dateIndex));
            dailyReturns.add(close / previousClose - 1);
        }
        return dailyReturns;
    }

    /**
     * How the two lists move around their own averages together, divided by how much each moves on its own: the
     * products of the day-by-day distances from the averages, over the square root of both sums of squared distances.
     */
    private Double correlationOf(List<Double> dailyReturns, List<Double> otherDailyReturns) {
        double averageReturn = averageOf(dailyReturns);
        double otherAverageReturn = averageOf(otherDailyReturns);
        double sumOfDistanceProducts = 0;
        double sumOfSquaredDistances = 0;
        double otherSumOfSquaredDistances = 0;
        for (int dayIndex = 0; dayIndex < dailyReturns.size(); dayIndex++) {
            double distanceFromAverage = dailyReturns.get(dayIndex) - averageReturn;
            double otherDistanceFromAverage = otherDailyReturns.get(dayIndex) - otherAverageReturn;
            sumOfDistanceProducts += distanceFromAverage * otherDistanceFromAverage;
            sumOfSquaredDistances += distanceFromAverage * distanceFromAverage;
            otherSumOfSquaredDistances += otherDistanceFromAverage * otherDistanceFromAverage;
        }
        if (sumOfSquaredDistances == 0 || otherSumOfSquaredDistances == 0) {
            return null;
        }
        return sumOfDistanceProducts / Math.sqrt(sumOfSquaredDistances * otherSumOfSquaredDistances);
    }

    private double averageOf(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
}
