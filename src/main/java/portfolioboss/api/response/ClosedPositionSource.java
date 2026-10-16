package portfolioboss.api.response;

/**
 * Where a closed position in {@code GET /api/portfolio} comes from. Its names go into the JSON as they are, like
 * {@code HoldingStatus}'s, so they are never renamed.
 */
public enum ClosedPositionSource {

    /** Derived from a holding's trades, which are corrected in the positions table. */
    TRADES,

    /** Derived from the trades of a manual position ({@code manual_position}), corrected where it is shown. */
    MANUAL
}
