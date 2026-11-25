package portfolioboss.api.alerts.response;

import portfolioboss.db.alerts.AlertEntity;
import portfolioboss.model.alerts.AlertGroup;
import portfolioboss.model.alerts.AlertType;

import java.time.Instant;

/**
 * One alert of the bell's list. The component names are JSON keys ({@code ui/src/types/alerts.ts} mirrors them): renamed
 * or removed only together with the UI.
 *
 * @param holdingId the holding the alert is about, whose drawer it opens; {@code null} for a stock from the scan that
 *                  isn't held
 * @param seenAt    {@code null} while the bell hasn't been opened after it: the bell counts these
 * @param group     the group of the bell's panel it is shown in ({@code AlertGroupCalculator})
 */
public record AlertResponse(long id, AlertType alertType, int conId, String symbol, Long holdingId, String message,
                            Instant createdAt, Instant seenAt, AlertGroup group) {

    public AlertResponse(AlertEntity alert, Long holdingId, AlertGroup group) {
        this(alert.id(), alert.alertType(), alert.conId(), alert.symbol(), holdingId, alert.message(), alert.createdAt(),
                alert.seenAt(), group);
    }
}
