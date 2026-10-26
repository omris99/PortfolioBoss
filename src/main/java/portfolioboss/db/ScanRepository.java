package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

/** Reads and writes {@link ScanEntity} rows; Spring generates the class at startup, like {@link HoldingRepository}. */
public interface ScanRepository extends JpaRepository<ScanEntity, Long> {

    /**
     * The latest scan, without its candidates — they are read when {@code candidates()} is first called, inside the
     * same transaction.
     */
    Optional<ScanEntity> findFirstByOrderByScannedAtDesc();

    /** When the latest scan ran — only the time, for the once-a-trading-day rule; empty before the first scan. */
    @Query("select max(scan.scannedAt) from ScanEntity scan")
    Optional<Instant> findLatestScannedAt();
}
