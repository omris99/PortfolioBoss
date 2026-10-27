package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads {@link ScanCandidateEntity} rows by id; Spring generates the class at startup, like {@link HoldingRepository}.
 * They are written only with their {@link ScanEntity}. Used to tell which stock an analysis of an earlier scan was of.
 */
public interface ScanCandidateRepository extends JpaRepository<ScanCandidateEntity, Long> {
}
