package portfolioboss.api.sync.response;

import portfolioboss.ib.PortfolioSnapshot;

import java.time.Instant;

/**
 * What {@code POST /api/syncs} answers once the new reading from TWS is stored; the UI then reloads
 * {@code /api/portfolio}. The component names are JSON keys: added to, never renamed or removed.
 *
 * @param syncedAt     when IB sent the portfolio — the {@code asOf} the reloaded portfolio will carry
 * @param holdingCount the holdings IB reported
 */
public record SyncRunResponse(Instant syncedAt, int holdingCount) {

    public SyncRunResponse(PortfolioSnapshot snapshot) {
        this(snapshot.asOf(), snapshot.holdings().size());
    }
}
