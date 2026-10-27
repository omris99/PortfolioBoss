package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

/**
 * One row of the {@code investment_recommendation} table: the stocks one full run of the Recommendations page's Analyze
 * picked, in their order (RECOMMENDATIONS_TODO.md, decisions 5 and 13) — written only by
 * {@code api.RecommendationAnalysisService}, never changed. {@code scanId} and the picks are plain ids, like
 * {@link StockAnalysisEntity#holdingId()}: nothing here needs the scan or the stocks themselves.
 */
@Entity
@Table(name = "investment_recommendation")
public class InvestmentRecommendationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long scanId;
    private Instant recommendedAt;

    /** The picked stocks' {@code scan_candidate} ids, in the recommendation's order; empty when none qualified. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> picks;

    /** Required by JPA, which creates entities by reflection. */
    protected InvestmentRecommendationEntity() {
    }

    /** @param scanId the scan the short list came from */
    public InvestmentRecommendationEntity(long scanId, Instant recommendedAt, List<Long> picks) {
        this.scanId = scanId;
        this.recommendedAt = recommendedAt;
        this.picks = picks;
    }

    public Long id() {
        return id;
    }

    public long scanId() {
        return scanId;
    }

    public Instant recommendedAt() {
        return recommendedAt;
    }

    public List<Long> picks() {
        return picks;
    }
}
