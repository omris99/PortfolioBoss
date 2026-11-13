package portfolioboss.alerts;

import java.math.BigDecimal;

/**
 * What an alert type's number counts, and the numbers the settings drawer allows for it: a report within 1–60 days, a
 * price 1–100% below the average cost.
 */
public enum ThresholdUnit {
    /** A whole number of days. */
    DAYS(1, 60, true),
    PERCENT(1, 100, false);

    private final BigDecimal smallestAllowed;
    private final BigDecimal largestAllowed;
    private final boolean wholeNumbersOnly;

    ThresholdUnit(int smallestAllowed, int largestAllowed, boolean wholeNumbersOnly) {
        this.smallestAllowed = BigDecimal.valueOf(smallestAllowed);
        this.largestAllowed = BigDecimal.valueOf(largestAllowed);
        this.wholeNumbersOnly = wholeNumbersOnly;
    }

    /** Within the range, and — for days — a whole number. */
    public boolean allows(BigDecimal threshold) {
        boolean withinRange = threshold.compareTo(smallestAllowed) >= 0 && threshold.compareTo(largestAllowed) <= 0;
        boolean wholeIfNeeded = !wholeNumbersOnly || threshold.stripTrailingZeros().scale() <= 0;
        return withinRange && wholeIfNeeded;
    }
}
