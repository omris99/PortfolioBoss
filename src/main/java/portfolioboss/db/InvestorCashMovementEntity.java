package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the {@code investor_cash_movement} table: money an investor other than the account owner put into the IB
 * account or took out of it, entered by hand. The investor's cash is derived from these and from their trades, never
 * stored. The account owner has none: their cash is whatever IB's leaves.
 *
 * <p>{@code created_at} is filled by the database default and is not mapped.
 */
@Entity
@Table(name = "investor_cash_movement")
public class InvestorCashMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY, like a trade's holding: loading a movement does not also load its investor until something asks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investor_id")
    private InvestorEntity investor;

    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    private CashMovementType type;

    /** Always above 0: {@code type} says which way the money went. */
    private BigDecimal amount;
    private String note;

    /** Required by JPA, which creates entities by reflection. */
    protected InvestorCashMovementEntity() {
    }

    /** A deposit or withdrawal the user entered for {@code investor}. {@code note} may be {@code null}. */
    public InvestorCashMovementEntity(InvestorEntity investor, LocalDate movementDate, CashMovementType type,
                                      BigDecimal amount, String note) {
        this.investor = investor;
        changeDetails(movementDate, type, amount, note);
    }

    /** A correction by the user: every field is replaced, and the movement stays the same investor's. */
    public void changeDetails(LocalDate movementDate, CashMovementType type, BigDecimal amount, String note) {
        this.movementDate = movementDate;
        this.type = type;
        this.amount = amount;
        this.note = note;
    }

    public Long id() {
        return id;
    }

    public LocalDate movementDate() {
        return movementDate;
    }

    public CashMovementType type() {
        return type;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String note() {
        return note;
    }
}
