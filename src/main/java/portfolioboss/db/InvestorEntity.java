package portfolioboss.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import portfolioboss.calculation.CashMovementFact;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the {@code investor} table: someone whose money is in the IB account. Every trade belongs to one. The
 * account owner — exactly one, created by the migration — gets whatever IB reports that the others' trades and deposits
 * don't explain; every other investor is what was entered for them.
 *
 * <p>{@code created_at} is filled by the database default and is not mapped.
 */
@Entity
@Table(name = "investor")
public class InvestorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** The column is {@code is_account_owner}, not the {@code account_owner} the field's name would give. */
    @Column(name = "is_account_owner")
    private boolean accountOwner;

    /** Always empty for the account owner. */
    @OneToMany(mappedBy = "investor")
    @OrderBy("movementDate, id")
    private List<InvestorCashMovementEntity> cashMovements = new ArrayList<>();

    /** Required by JPA, which creates entities by reflection. */
    protected InvestorEntity() {
    }

    /** An investor other than the account owner: the API never creates a second account owner. */
    public InvestorEntity(String name) {
        this.name = name;
        this.accountOwner = false;
    }

    /** A correction by the user — the account owner's name too ("Me" → "Omri"). */
    public void changeName(String name) {
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isAccountOwner() {
        return accountOwner;
    }

    /** Ordered by date then id ({@code @OrderBy} on the field above). */
    public List<InvestorCashMovementEntity> cashMovements() {
        return List.copyOf(cashMovements);
    }

    /**
     * Reduced to what {@link portfolioboss.calculation.InvestorSummaryCalculator} needs, like {@code TradeEntity.toTradeFact()}.
     */
    public List<CashMovementFact> cashMovementFacts() {
        return cashMovements.stream()
                .map(movement -> new CashMovementFact(id, movement.type(), movement.amount()))
                .toList();
    }
}
