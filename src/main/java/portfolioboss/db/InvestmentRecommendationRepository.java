package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Reads and writes {@link InvestmentRecommendationEntity} rows; Spring generates the class at startup. */
public interface InvestmentRecommendationRepository extends JpaRepository<InvestmentRecommendationEntity, Long> {

    /** The latest recommendation, from whichever scan; empty before the first full run. */
    Optional<InvestmentRecommendationEntity> findFirstByOrderByRecommendedAtDesc();
}
