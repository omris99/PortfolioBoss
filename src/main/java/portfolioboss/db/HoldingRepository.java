package portfolioboss.db;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Reads and writes {@link HoldingEntity} rows. Only an interface: Spring generates the class at startup,
 * and turns each method name into a query ({@code findByAccountAndConId} is
 * {@code where account = ? and con_id = ?}). It also checks at startup that the names in them exist on
 * the entity, so a typo fails immediately.
 */
public interface HoldingRepository extends JpaRepository<HoldingEntity, Long> {

    Optional<HoldingEntity> findByAccountAndConId(String account, int conId);

    List<HoldingEntity> findByAccountAndStatus(String account, HoldingStatus status);

    /**
     * Open and closed holdings alike, in the order they were first seen, with their trades loaded in the
     * same query ({@code LEFT JOIN FETCH}) instead of one extra query per holding when something reads
     * {@code trades()}.
     */
    @EntityGraph(attributePaths = "trades")
    List<HoldingEntity> findByAccountOrderById(String account);
}
