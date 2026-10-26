package portfolioboss.api.response;

/**
 * How closely a scanned stock moves with one open holding (RECOMMENDATIONS_TODO.md, decision 7): the correlation of
 * their daily returns over the year, 1 exactly together, 0 unrelated, −1 opposite. The component names are JSON keys:
 * added to, never renamed or removed.
 *
 * @param movesTogether 0.7 or more — buying the stock adds little spread to holding this one
 */
public record CorrelationResponse(long holdingId, String symbol, double coefficient, boolean movesTogether) {
}
