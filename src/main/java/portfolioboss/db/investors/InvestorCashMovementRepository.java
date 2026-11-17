package portfolioboss.db.investors;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@link InvestorCashMovementEntity} rows. Spring generates the class at startup, like
 * {@code TradeRepository}; the inherited {@code findById}, {@code save} and {@code delete} are all the write endpoints
 * need. They are read through {@link InvestorRepository#findAllByOrderById}, with their investor.
 */
public interface InvestorCashMovementRepository extends JpaRepository<InvestorCashMovementEntity, Long> {
}
