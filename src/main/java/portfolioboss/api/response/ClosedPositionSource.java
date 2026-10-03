package portfolioboss.api.response;

/**
 * Where a closed position in {@code GET /api/portfolio} comes from. Its names go into the JSON as they are, like
 * {@code HoldingStatus}'s, so they are never renamed.
 */
public enum ClosedPositionSource {

    /** Derived from a holding's trades: a position period a sell brought back to zero. Corrected through its trades. */
    TRADES,

    /** Entered by hand as one row ({@code manual_closed_position}), and edited or deleted as that row. */
    MANUAL
}
