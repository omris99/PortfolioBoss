package portfolioboss.alerts;

/**
 * A condition true now, which becomes an alert unless an open alert already has its key.
 *
 * @param message in English, shown as it is: "Closed below its 50-day average: 182.40 against 190.10"
 */
public record AlertCondition(AlertKey key, String symbol, String message) {
}
