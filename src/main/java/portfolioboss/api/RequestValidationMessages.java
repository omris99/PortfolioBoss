package portfolioboss.api;

/**
 * The messages of the request checks that bodies of several features share — a trade, a manual position, a deposit —,
 * written once so that every amount and quantity is refused in the same words. Each replaces Hibernate Validator's own
 * message, which the UI would show as it is.
 */
public final class RequestValidationMessages {

    /**
     * Replaces "numeric value out of bounds (&lt;14 digits&gt;.&lt;6 digits&gt; expected)" for an amount stored in a
     * {@code NUMERIC(20,6)} column: 14 digits before the decimal point and 6 after it.
     */
    public static final String TOO_MANY_DIGITS = "must have at most 6 decimal places (and 14 digits before the point)";

    /**
     * The same, for a quantity: {@code @Digits} with {@code fraction = 0}. It counts the decimal places as they were
     * written, so 10.0 is rejected too; the UI sends 10.
     */
    public static final String WHOLE_NUMBER = "must be a whole number (at most 14 digits)";

    private RequestValidationMessages() {
    }
}
