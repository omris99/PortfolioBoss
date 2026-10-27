package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.ai.StockSearches;
import portfolioboss.stocks.Stock;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the {@code stock_analysis} table: one stock's analysis from one run, never changed afterwards — a
 * holding's, from {@code POST /api/analysis}, or a scanned stock's, from the Recommendations page (V10). Exactly one of
 * {@code holdingId} and {@code scanCandidateId} is set, which the table checks. Both are plain ids, like
 * {@code TradeEntity.investorId}: nothing here needs the holding or the scanned stock itself.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} has Hibernate store {@code result}, {@code searchResults} and
 * {@code finnhubStock} as JSON in their {@code JSONB} columns and read them back into the records.
 */
@Entity
@Table(name = "stock_analysis")
public class StockAnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code null} for a scanned stock's analysis. */
    private Long holdingId;
    /** The stock's row in the scan it was analyzed from ({@code scan_candidate}); {@code null} for a holding's. */
    private Long scanCandidateId;
    private Instant analyzedAt;
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    private StockAnalysisResult result;

    /**
     * The search results Claude read — only those that name the stock — so that a fact missing from {@code result} can
     * be traced to the search or to Claude (AI_ANALYSIS_TODO.md, decision 22); {@code null} before 2026-10-10.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private StockSearches searchResults;

    /**
     * What Finnhub answered about a scanned stock — its next earnings date and four of the company's figures
     * (RECOMMENDATIONS_TODO.md, decision 8); {@code null} when it didn't answer, and for a holding's analysis.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Stock finnhubStock;

    private int inputTokens;
    private int outputTokens;
    private int tavilyCredits;
    private BigDecimal costUsd;

    /** Required by JPA, which creates entities by reflection. */
    protected StockAnalysisEntity() {
    }

    /**
     * A holding's analysis.
     *
     * @param model   the model that actually answered — a refusal fallback may have moved the request to another one
     * @param costUsd what Claude's tokens cost; Tavily's credits are counted, not priced
     */
    public StockAnalysisEntity(long holdingId, Instant analyzedAt, String model, StockAnalysisResult result,
                               StockSearches searchResults, int inputTokens, int outputTokens, int tavilyCredits,
                               BigDecimal costUsd) {
        this(holdingId, null, null, analyzedAt, model, result, searchResults, inputTokens, outputTokens,
                tavilyCredits, costUsd);
    }

    /**
     * A scanned stock's analysis. It takes the stored row itself, not its id, so that a holding's id can't be passed
     * by mistake.
     *
     * @param finnhubStock {@code null} when Finnhub didn't answer
     */
    public StockAnalysisEntity(ScanCandidateEntity candidate, Stock finnhubStock, Instant analyzedAt, String model,
                               StockAnalysisResult result, StockSearches searchResults, int inputTokens,
                               int outputTokens, int tavilyCredits, BigDecimal costUsd) {
        this(null, candidate.id(), finnhubStock, analyzedAt, model, result, searchResults, inputTokens, outputTokens,
                tavilyCredits, costUsd);
    }

    private StockAnalysisEntity(Long holdingId, Long scanCandidateId, Stock finnhubStock, Instant analyzedAt,
                                String model, StockAnalysisResult result, StockSearches searchResults,
                                int inputTokens, int outputTokens, int tavilyCredits, BigDecimal costUsd) {
        this.holdingId = holdingId;
        this.scanCandidateId = scanCandidateId;
        this.finnhubStock = finnhubStock;
        this.analyzedAt = analyzedAt;
        this.model = model;
        this.result = result;
        this.searchResults = searchResults;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.tavilyCredits = tavilyCredits;
        this.costUsd = costUsd;
    }

    public Long id() {
        return id;
    }

    /** {@code null} for a scanned stock's analysis. */
    public Long holdingId() {
        return holdingId;
    }

    /** {@code null} for a holding's analysis. */
    public Long scanCandidateId() {
        return scanCandidateId;
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

    public StockSearches searchResults() {
        return searchResults;
    }

    public Stock finnhubStock() {
        return finnhubStock;
    }

    public int tavilyCredits() {
        return tavilyCredits;
    }

    public BigDecimal costUsd() {
        return costUsd;
    }
}
