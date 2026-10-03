package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Reads and writes {@link ManualClosedPositionEntity} rows. Spring generates the class at startup, like
 * {@link HoldingRepository}; the inherited {@code findById}, {@code save} and {@code delete} are what the write
 * endpoints need.
 */
public interface ManualClosedPositionRepository extends JpaRepository<ManualClosedPositionEntity, Long> {

    /** Every row, in the order they were entered. */
    List<ManualClosedPositionEntity> findAllByOrderById();
}
