package portfolioboss.alerts;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One row of the {@code alert_setting} table: one type's line of the settings drawer, as the user saved it. A type never
 * saved has no row, and its defaults ({@link AlertSettings}). The type is the row's id, so saving a type again replaces
 * its row.
 */
@Entity
@Table(name = "alert_setting")
public class AlertSettingEntity {

    @Id
    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    private boolean enabled;
    /** Typed by hand, so {@code NUMERIC} under a {@code BigDecimal}; {@code null} for a type without a number. */
    private BigDecimal threshold;

    /** Required by JPA, which creates entities by reflection. */
    protected AlertSettingEntity() {
    }

    protected AlertSettingEntity(AlertSetting setting) {
        this.alertType = setting.type();
        this.enabled = setting.enabled();
        this.threshold = setting.threshold();
    }

    protected AlertType alertType() {
        return alertType;
    }

    protected AlertSetting toAlertSetting() {
        return new AlertSetting(alertType, enabled, threshold);
    }
}
