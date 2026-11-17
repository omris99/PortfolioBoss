package portfolioboss.db.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import portfolioboss.calculation.alerts.AlertSetting;
import portfolioboss.calculation.alerts.AlertSettings;
import portfolioboss.model.alerts.AlertType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads and writes {@link AlertSettingEntity} rows. The id is the alert type, so that is the second type parameter;
 * {@code save} of a type already saved replaces its row.
 */
public interface AlertSettingRepository extends JpaRepository<AlertSettingEntity, AlertType> {

    /** Every type's setting: the saved ones, and the defaults for the rest. */
    default AlertSettings alertSettings() {
        Map<AlertType, AlertSetting> savedSettingsByType = new EnumMap<>(AlertType.class);
        findAll().forEach(savedSetting -> savedSettingsByType.put(savedSetting.alertType(), savedSetting.toAlertSetting()));
        return new AlertSettings(savedSettingsByType);
    }
}
