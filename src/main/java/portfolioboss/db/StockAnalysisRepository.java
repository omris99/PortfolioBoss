package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Reads and writes {@link StockAnalysisEntity} rows; Spring generates the class at startup, like {@link HoldingRepository}. */
public interface StockAnalysisRepository extends JpaRepository<StockAnalysisEntity, Long> {

    /**
     * The latest analysis of every holding that has one, in one query. Written in PostgreSQL's own SQL
     * ({@code nativeQuery}): its {@code DISTINCT ON} keeps the first row of each holding in the order given — the
     * latest, and of two at the same moment the one stored last.
     */
    @Query(value = """
            SELECT DISTINCT ON (holding_id) *
            FROM stock_analysis
            ORDER BY holding_id, analyzed_at DESC, id DESC
            """, nativeQuery = true)
    List<StockAnalysisEntity> findLatestOfEveryHolding();
}
