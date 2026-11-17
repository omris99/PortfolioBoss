package portfolioboss.model.alerts;


import java.math.BigDecimal;

/**
 * The nine kinds of alert (MY_HOLDINGS_TODO.md, decisions 6 and 16), each with the number it is checked against by
 * default — the settings drawer can change it ({@code AlertSettings}). Stored as its name in {@code alert.alert_type} and
 * {@code alert_setting.alert_type}, and the names reach the JSON as they are ({@code ui/src/types/alerts.ts} mirrors
 * them), so they must stay stable.
 */
public enum AlertType {
    /** The momentum label turned {@code WEAK} (decision 6, א). */
    MOMENTUM_TURNED_WEAK,
    /** The last close fell below the 50-day average. */
    CLOSED_BELOW_SMA50,
    /** The last close fell below the 200-day average. */
    CLOSED_BELOW_SMA200,
    /** The next report is this many days away or fewer (ב). */
    EARNINGS_SOON(7, ThresholdUnit.DAYS),
    /** A firm cut its price target in this many days back, in the Dow Jones headlines (ג). */
    ANALYST_CUT_TARGET(7, ThresholdUnit.DAYS),
    /** The unrealized P&L is this many percent below 0 or more: the price that far below IB's average cost (ד). */
    FAR_BELOW_AVERAGE_COST(15, ThresholdUnit.PERCENT),
    /** More than this many percent of the whole account (ה). */
    ABOVE_WEIGHT_LIMIT(15, ThresholdUnit.PERCENT),
    /** The holding has one of its warnings: a trade not entered, or entered wrong (ו). */
    TRADES_DONT_MATCH_IB,
    /** A stock in the latest scan's short list — HOT, the analysts' target 15%+ above the price (ז). */
    ENTERED_SHORT_LIST;

    private final BigDecimal defaultThreshold;
    private final ThresholdUnit thresholdUnit;

    /** A type with no number: true or not. */
    AlertType() {
        this.defaultThreshold = null;
        this.thresholdUnit = null;
    }

    AlertType(int defaultThreshold, ThresholdUnit thresholdUnit) {
        this.defaultThreshold = BigDecimal.valueOf(defaultThreshold);
        this.thresholdUnit = thresholdUnit;
    }

    /** {@code null} for a type with no number. */
    public BigDecimal defaultThreshold() {
        return defaultThreshold;
    }

    /** {@code null} for a type with no number. */
    public ThresholdUnit thresholdUnit() {
        return thresholdUnit;
    }

    public boolean hasThreshold() {
        return thresholdUnit != null;
    }
}
