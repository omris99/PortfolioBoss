package portfolioboss.alerts.response;

import portfolioboss.alerts.AlertSetting;
import portfolioboss.alerts.AlertType;

import java.math.BigDecimal;

/**
 * One type's line of the settings drawer: as saved, or its default. The component names are JSON keys.
 *
 * @param threshold        days or percent; {@code null} for a type without a number
 * @param defaultThreshold what the type starts with — the drawer shows it beside the field
 */
public record AlertSettingResponse(AlertType alertType, boolean enabled, BigDecimal threshold,
                                   BigDecimal defaultThreshold) {

    public AlertSettingResponse(AlertSetting setting) {
        this(setting.type(), setting.enabled(), setting.threshold(), setting.type().defaultThreshold());
    }
}
