package portfolioboss.db;

/**
 * What one sync did to the {@code holding} table.
 *
 * @param added   holdings seen for the first time
 * @param updated holdings that were already stored and were refreshed from IB
 * @param closed  holdings that were open and are no longer in the portfolio TWS reported
 */
public record SyncResult(int added, int updated, int closed) {
}
