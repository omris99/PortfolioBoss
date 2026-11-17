package portfolioboss.db.alerts;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import portfolioboss.calculation.alerts.AlertCondition;
import portfolioboss.calculation.alerts.AlertKey;
import portfolioboss.model.alerts.AlertType;

import java.time.Instant;

/**
 * One row of the {@code alert} table ({@code V15__alerts.sql}): a condition that became true at a check. Written only by
 * {@code AlertService}: created when the condition became true, ended when it no longer was, seen when the bell was opened.
 */
@Entity
@Table(name = "alert")
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AlertType alertType;

    private int conId;
    private String symbol;
    private String subject;
    private String message;
    private Instant createdAt;
    /** {@code null} while the condition is still true: the alert is open. */
    private Instant endedAt;
    /** {@code null} until the bell is opened after it. */
    private Instant seenAt;

    /** Required by JPA, which creates entities by reflection. */
    protected AlertEntity() {
    }

    public AlertEntity(AlertCondition condition, Instant createdAt) {
        AlertKey key = condition.key();
        this.alertType = key.type();
        this.conId = key.conId();
        this.subject = key.subject();
        this.symbol = condition.symbol();
        this.message = condition.message();
        this.createdAt = createdAt;
    }

    public AlertKey key() {
        return new AlertKey(alertType, conId, subject);
    }

    public void markEnded(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Long id() {
        return id;
    }

    public AlertType alertType() {
        return alertType;
    }

    public int conId() {
        return conId;
    }

    public String symbol() {
        return symbol;
    }

    public String message() {
        return message;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant seenAt() {
        return seenAt;
    }
}
