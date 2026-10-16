package portfolioboss.db;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and writes {@link ManualPositionEntity} rows. Spring generates the class at startup, like
 * {@link HoldingRepository}; the inherited {@code findById}, {@code save} and {@code delete} are what the write
 * endpoints need.
 */
public interface ManualPositionRepository extends JpaRepository<ManualPositionEntity, Long> {

    /**
     * Every manual position, in the order they were entered, with their trades loaded in the same query — like
     * {@code HoldingRepository.findByAccountOrderById}.
     */
    @EntityGraph(attributePaths = "trades")
    List<ManualPositionEntity> findAllByOrderById();
}
