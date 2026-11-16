package portfolioboss.tax;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the {@code tax_confirmation} table ({@code V19__tax_confirmation.sql}): what Phoenix recorded for one of the
 * account's settlement days, as the user confirmed it. Written only by {@link TaxConfirmationService}.
 */
@Entity
@Table(name = "tax_confirmation")
public class TaxConfirmationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate settlementDay;
    /** Typed by hand, so {@code NUMERIC} under a {@code BigDecimal}; in shekels, to the agora. */
    private BigDecimal taxAmount;
    private Instant confirmedAt;
    private String note;

    /** Required by JPA, which creates entities by reflection. */
    protected TaxConfirmationEntity() {
    }

    protected TaxConfirmationEntity(LocalDate settlementDay, BigDecimal taxAmount, String note, Instant confirmedAt) {
        this.settlementDay = settlementDay;
        this.taxAmount = taxAmount;
        this.note = note;
        this.confirmedAt = confirmedAt;
    }

    /** A correction: the amount Phoenix recorded, and the note, as the user typed them now. */
    protected void correct(BigDecimal taxAmount, String note, Instant correctedAt) {
        this.taxAmount = taxAmount;
        this.note = note;
        this.confirmedAt = correctedAt;
    }

    protected TaxConfirmation toTaxConfirmation() {
        return new TaxConfirmation(id, settlementDay, taxAmount, confirmedAt, note);
    }
}
