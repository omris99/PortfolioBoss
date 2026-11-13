package portfolioboss.alerts;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import portfolioboss.api.PortfolioSyncedEvent;

/**
 * Checks the alerts after every sync — {@code ./run.sh}'s startup and Sync now (MY_HOLDINGS_TODO.md, decision 14) — by
 * listening for the event the sync publishes, so that the sync doesn't depend on the alerts.
 *
 * <p>A class of its own rather than a method of {@link AlertService}: a call from inside {@code AlertService} to its own
 * {@code @Transactional} method would go around Spring's wrapper and run without a transaction.
 */
@Component
class AlertsAfterSync {

    private final AlertService alertService;

    private AlertsAfterSync(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * A failed check doesn't undo the sync, which is already stored, nor stop the startup: like a failed Finnhub call, it
     * is printed and the rest goes on. The next sync checks again.
     */
    @EventListener
    private void checkAlertsAfterSync(PortfolioSyncedEvent event) {
        try {
            alertService.checkAlerts();
        } catch (RuntimeException e) {
            System.err.println("[alerts error] could not check the alerts: " + e.getMessage());
        }
    }
}
