package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.ai.StockAnalysisResult;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the {@code stock_analysis} table: one holding's analysis from one run of {@code POST /api/analysis},
 * written only by {@code api.AnalysisService} and never changed afterwards. {@code holdingId} is a plain id, like
 * {@code TradeEntity.investorId}: nothing here needs the holding itself.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} has Hibernate store {@code result} as JSON in the {@code JSONB} column and
 * read it back into the record.
 */
@Entity
@Table(name = "stock_analysis")
public class StockAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long holdingId;
    private Instant analyzedAt;
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    private StockAnalysisResult result;

    private int inputTokens;
    private int outputTokens;
    private int tavilyCredits;
    private BigDecimal costUsd;

    /** Required by JPA, which creates entities by reflection. */
    protected StockAnalysisEntity() {
    }

    /**
     * @param model   the model that actually answered — a refusal fallback may have moved the request to another one
     * @param costUsd what Claude's tokens cost; Tavily's credits are counted, not priced
     */
    public StockAnalysisEntity(long holdingId, Instant analyzedAt, String model, StockAnalysisResult result,
                               int inputTokens, int outputTokens, int tavilyCredits, BigDecimal costUsd) {
        this.holdingId = holdingId;
        this.analyzedAt = analyzedAt;
        this.model = model;
        this.result = result;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.tavilyCredits = tavilyCredits;
        this.costUsd = costUsd;
    }

    public Long id() {
        return id;
    }

    public long holdingId() {
        return holdingId;
    }

    public Instant analyzedAt() {
        return analyzedAt;
    }

    public String model() {
        return model;
    }

    public StockAnalysisResult result() {
        return result;
    }
}
