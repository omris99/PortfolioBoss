package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads and writes {@link TradeEntity} rows. Spring generates the class at startup, like {@link HoldingRepository};
 * the inherited {@code findById}, {@code save} and {@code delete} are all the write endpoints need.
 */
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {
}
