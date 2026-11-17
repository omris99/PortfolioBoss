package portfolioboss.ai;

/**
 * How big a partnership or a major contract is, by what the result itself says (RECOMMENDATIONS_TODO.md, decision 13) —
 * the rules are in {@link StockAnalyzer}'s instructions. The other kinds of event have none.
 */
public enum EventMagnitude {
    /** One of the world's largest companies named as the partner or customer, or an amount presented as material. */
    HIGH,
    /** A well-known company named, or an amount stated. */
    MEDIUM,
    LOW
}
