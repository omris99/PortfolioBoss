package portfolioboss.calculation.alerts;

import portfolioboss.model.alerts.AlertType;

import java.math.BigDecimal;

/**
 * One type's line in the settings drawer.
 *
 * @param threshold its number — days or percent ({@link AlertType#thresholdUnit()}) —; {@code null} for a type that has
 *                  none
 */
public record AlertSetting(AlertType type, boolean enabled, BigDecimal threshold) {
}
