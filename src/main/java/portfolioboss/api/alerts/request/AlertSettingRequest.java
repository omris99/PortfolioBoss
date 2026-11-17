package portfolioboss.api.alerts.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import portfolioboss.calculation.alerts.AlertSetting;
import portfolioboss.model.alerts.AlertType;

import java.math.BigDecimal;

/**
 * One type's line of the settings drawer, as saved: on or off, and its number for a type that has one.
 *
 * @param enabled   a {@code Boolean}, so that a line without it is refused rather than read as "off"
 * @param threshold at most two decimal places: {@code alert_setting.threshold} is {@code NUMERIC(5,2)}
 */
public record AlertSettingRequest(
        @NotNull AlertType alertType,
        @NotNull Boolean enabled,
        @Digits(integer = 3, fraction = 2, message = "at most 2 decimal places") BigDecimal threshold) {

    /**
     * The one rule that depends on the type, so no annotation on a single field can check it ({@code @AssertTrue}, like
     * {@code NewManualPositionRequest}'s): the error names it "settings[4].thresholdRightForItsType: …". A missing type is
     * left to {@code @NotNull}.
     */
    @AssertTrue(message = "a type with a number needs one within its range (1–60 whole days, 1–100 percent), and a type "
            + "without one takes none")
    private boolean isThresholdRightForItsType() {
        if (alertType == null) {
            return true;
        }
        if (!alertType.hasThreshold()) {
            return threshold == null;
        }
        return threshold != null && alertType.thresholdUnit().allows(threshold);
    }

    public AlertSetting toAlertSetting() {
        return new AlertSetting(alertType, enabled, threshold);
    }
}
