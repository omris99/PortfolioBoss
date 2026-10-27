package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/** Reads and writes {@link StockAnalysisEntity} rows; Spring generates the class at startup, like {@link HoldingRepository}. */
public interface StockAnalysisRepository extends JpaRepository<StockAnalysisEntity, Long> {

    /**
     * The latest analysis of every holding that has one, in one query. Written in PostgreSQL's own SQL
     * ({@code nativeQuery}): its {@code DISTINCT ON} keeps the first row of each holding in the order given — the
     * latest, and of two at the same moment the one stored last. The scanned stocks' analyses are left out: their
     * {@code holding_id} is {@code NULL}, and {@code DISTINCT ON} would keep one of them as if it were a holding's.
     */
    @Query(value = """
            SELECT DISTINCT ON (holding_id) *
            FROM stock_analysis
            WHERE holding_id IS NOT NULL
            ORDER BY holding_id, analyzed_at DESC, id DESC
            """, nativeQuery = true)
    List<StockAnalysisEntity> findLatestOfEveryHolding();

    /**
     * The latest analysis of every one of these stocks that was analyzed as a scanned stock, from any scan — a stock
     * is found again in each day's scan, as a new {@code scan_candidate} row, and keeps its analysis (decision 12 of
     * RECOMMENDATIONS_TODO.md). {@code DISTINCT ON} as above, by the stock's contract id. {@code conIds} must not be
     * empty: PostgreSQL refuses {@code IN ()}.
     */
    @Query(value = """
            SELECT DISTINCT ON (scan_candidate.con_id) stock_analysis.*
            FROM stock_analysis
            JOIN scan_candidate ON scan_candidate.id = stock_analysis.scan_candidate_id
            WHERE scan_candidate.con_id IN (:conIds)
            ORDER BY scan_candidate.con_id, stock_analysis.analyzed_at DESC, stock_analysis.id DESC
            """, nativeQuery = true)
    List<StockAnalysisEntity> findLatestOfEveryScannedStock(Collection<Integer> conIds);
}
