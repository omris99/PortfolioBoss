package portfolioboss.calculation;

/**
 * What a {@link HoldingWarning} is about. The names reach the JSON as they are ({@code ui/src/types/portfolio.ts}
 * mirrors them), so they must stay stable.
 */
public enum HoldingWarningType {

    /** No buy entered yet, so the buy date and holding period are unknown. Expected while backfilling, so the UI shows it in a milder colour. */
    NO_TRADES_LOGGED,

    /** IB no longer reports the holding, but no sell ending its current position period was entered. */
    CLOSED_WITHOUT_SELL,

    /** The trades entered add up to a different quantity than IB reports. */
    QUANTITY_MISMATCH
}
