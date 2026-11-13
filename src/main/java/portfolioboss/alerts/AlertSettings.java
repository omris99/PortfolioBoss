package portfolioboss.alerts;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Every type's setting: the one saved in the settings drawer, or — for a type never saved — its default, on and with the
 * number {@link AlertType} gives it.
 *
 * @param savedSettingsByType the rows of {@code alert_setting}
 */
public record AlertSettings(Map<AlertType, AlertSetting> savedSettingsByType) {

    public AlertSetting settingOf(AlertType type) {
        AlertSetting savedSetting = savedSettingsByType.get(type);
        return savedSetting != null ? savedSetting : new AlertSetting(type, true, type.defaultThreshold());
    }

    public boolean isEnabled(AlertType type) {
        return settingOf(type).enabled();
    }

    /** The type's number, saved or default; {@code null} for a type that has none. */
    public BigDecimal thresholdOf(AlertType type) {
        return settingOf(type).threshold();
    }

    /** In the enum's order: the drawer's. */
    public List<AlertSetting> allSettings() {
        return Arrays.stream(AlertType.values()).map(this::settingOf).toList();
    }

    public List<AlertType> enabledTypes() {
        return Arrays.stream(AlertType.values()).filter(this::isEnabled).toList();
    }
}
