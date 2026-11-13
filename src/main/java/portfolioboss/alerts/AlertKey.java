package portfolioboss.alerts;

/**
 * What makes two alerts the same alert: the same type, on the same stock, about the same thing — an open alert with this
 * key means the condition is still true, and no second one is made.
 *
 * @param conId   IB's contract id of the holding, or of the scanned stock
 * @param subject what tells two alerts of the same type on the same stock apart — an analyst's cut ("Morgan Stanley
 *                2026-11-10T14:05"), a report ("2026-11-18"), which warning ("QUANTITY_MISMATCH") —; {@code null}
 *                when the type and the stock are enough
 */
public record AlertKey(AlertType type, int conId, String subject) {
}
