package portfolioboss.api;

/**
 * Published by {@link SyncService} once a sync from TWS is stored — the startup's or Sync now's. A Spring application
 * event: whoever needs to react to a new sync listens for it ({@code @EventListener}; the alerts, {@code
 * alerts.AlertsAfterSync}), and {@code SyncService} doesn't know who — so {@code api} needs no package that builds on it.
 * The listeners run in the same thread before {@code publishEvent} returns: when Sync now answers, they are done.
 */
public record PortfolioSyncedEvent() {
}
