package portfolioboss.db;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Reads {@link InvestorEntity} rows. Spring generates the class at startup, like {@link HoldingRepository}. */
public interface InvestorRepository extends JpaRepository<InvestorEntity, Long> {

    /**
     * Every investor, the account owner first (the migration created it before anyone else), with their deposits and
     * withdrawals loaded in the same query — like {@code HoldingRepository.findByAccountOrderById}.
     */
    @EntityGraph(attributePaths = "cashMovements")
    List<InvestorEntity> findAllByOrderById();

    /** {@code where is_account_owner = true}: the one investor the migration created, never empty after it. */
    Optional<InvestorEntity> findByAccountOwnerTrue();

    /** Whether the name is taken, "avi" and "Avi" alike: two investors whose names differ only in case would be confusing. */
    boolean existsByNameIgnoreCase(String name);

    /** The same, by an investor other than {@code id}: renaming an investor to their own name is no conflict. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * The investor a new trade belongs to until the API lets the user pick another. A {@code default} method is plain
     * Java: Spring makes no query of it, it only calls the one above.
     */
    default InvestorEntity accountOwner() {
        return findByAccountOwnerTrue()
                .orElseThrow(() -> new IllegalStateException("No account owner: V4__investors.sql creates one"));
    }
}
