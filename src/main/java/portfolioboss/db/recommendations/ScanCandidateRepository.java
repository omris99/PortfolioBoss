package portfolioboss.db.recommendations;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads {@link ScanCandidateEntity} rows by id; Spring generates the class at startup, like {@code HoldingRepository}.
 * They are written only with their {@link ScanEntity}. Used to tell which stock an analysis of an earlier scan was of.
 */
public interface ScanCandidateRepository extends JpaRepository<ScanCandidateEntity, Long> {
}
