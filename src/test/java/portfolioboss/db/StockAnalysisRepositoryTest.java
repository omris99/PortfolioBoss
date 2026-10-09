package portfolioboss.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystActionType;
import portfolioboss.ai.AnalystRating;
import portfolioboss.ai.AnalystTrend;
import portfolioboss.ai.Headline;
import portfolioboss.ai.RatingCounts;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.ai.StockAnalysisResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code stock_analysis} table against the real PostgreSQL test database, like {@code PortfolioSyncServiceTest}:
 * that Claude's result goes into the {@code JSONB} column and comes back the same, and that the latest analysis of
 * every holding is found in one query.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StockAnalysisRepositoryTest {

    private static final Instant MONDAY = Instant.parse("2026-10-05T07:00:00Z");
    private static final Instant TUESDAY = Instant.parse("2026-10-06T07:00:00Z");

    @Autowired
    private StockAnalysisRepository stockAnalysisRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theResultComesBackFromTheDatabaseAsItWasStored() {
        long appleHoldingId = insertHolding(265598, "AAPL");
        StockAnalysisResult storedResult = appleResult();
        stockAnalysisRepository.save(analysisOf(appleHoldingId, MONDAY, storedResult));
        startReadingFromTheDatabase();

        StockAnalysisEntity readBack = stockAnalysisRepository.findLatestOfEveryHolding().getFirst();

        assertThat(readBack.result()).isEqualTo(storedResult);
        assertThat(readBack.holdingId()).isEqualTo(appleHoldingId);
        assertThat(readBack.analyzedAt()).isEqualTo(MONDAY);
        assertThat(readBack.model()).isEqualTo("claude-sonnet-5-5");
    }

    /**
     * The column holds the record's components under their own names, and nothing that is worked out from them — the
     * consensus shown or the dot would be stale the moment a rule changed.
     */
    @Test
    void theColumnHoldsOnlyWhatClaudeAnswered() {
        long appleHoldingId = insertHolding(265598, "AAPL");
        stockAnalysisRepository.save(analysisOf(appleHoldingId, MONDAY, appleResult()));
        startReadingFromTheDatabase();

        List<String> resultKeys = jdbc.queryForList(
                "select jsonb_object_keys(result) from stock_analysis order by 1", String.class);
        List<String> sourceKeys = jdbc.queryForList(
                "select jsonb_object_keys(result -> 'consensusBySource' -> 0) from stock_analysis order by 1",
                String.class);
        String publishedDate = jdbc.queryForObject(
                "select result -> 'consensusBySource' -> 0 ->> 'publishedDate' from stock_analysis", String.class);

        assertThat(resultKeys).containsExactly("analystTrend", "consensusBySource", "headlines", "recentActions",
                "sentiment", "sentimentReason");
        assertThat(sourceKeys).containsExactly("analystCount", "averageTarget", "publishedDate", "ratingCounts",
                "ratingLabel", "sourceUrl");
        assertThat(publishedDate).isEqualTo("2026-10-05");
    }

    @Test
    void findsTheLatestAnalysisOfEveryHolding() {
        long appleHoldingId = insertHolding(265598, "AAPL");
        long aevaHoldingId = insertHolding(495512557, "AEVA");
        stockAnalysisRepository.save(analysisOf(appleHoldingId, MONDAY, appleResult()));
        stockAnalysisRepository.save(analysisOf(appleHoldingId, TUESDAY, appleResult()));
        stockAnalysisRepository.save(analysisOf(aevaHoldingId, MONDAY, appleResult()));
        startReadingFromTheDatabase();

        List<StockAnalysisEntity> latest = stockAnalysisRepository.findLatestOfEveryHolding();

        assertThat(latest).extracting(StockAnalysisEntity::holdingId).containsExactly(appleHoldingId, aevaHoldingId);
        assertThat(latest).extracting(StockAnalysisEntity::analyzedAt).containsExactly(TUESDAY, MONDAY);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Two sources (one without a breakdown), an action and a headline, and some of what may be {@code null}. */
    private StockAnalysisResult appleResult() {
        return new StockAnalysisResult(
                List.of(new SourceConsensus("https://financhill.com/aapl", LocalDate.of(2026, 10, 5), 48,
                                new RatingCounts(0, 30, 16, 2, 0), 328.22, AnalystRating.BUY),
                        new SourceConsensus("https://stockanalysis.com/aapl", null, 44, null, 328.09, null)),
                AnalystTrend.STABLE,
                List.of(new AnalystAction(LocalDate.of(2026, 10, 2), "Some Firm", AnalystActionType.TARGET_LOWERED,
                        "Overweight", "Overweight", 360.0, 355.0, "https://example.com/action")),
                List.of(new Headline(null, "Apple unveils a new product", "Reuters", "https://example.com/news")),
                Sentiment.NEUTRAL,
                null);
    }

    private StockAnalysisEntity analysisOf(long holdingId, Instant analyzedAt, StockAnalysisResult result) {
        return new StockAnalysisEntity(holdingId, analyzedAt, "claude-sonnet-5-5", result, 10500, 900, 3,
                new BigDecimal("0.030000"));
    }

    private long insertHolding(int conId, String symbol) {
        Timestamp seenAt = Timestamp.from(MONDAY);
        return jdbc.queryForObject("""
                insert into holding (account, con_id, symbol, sec_type, currency, status, first_seen_at, last_synced_at)
                values ('U1234567', ?, ?, 'STK', 'USD', 'OPEN', ?, ?) returning id
                """, Long.class, conId, symbol, seenAt, seenAt);
    }

    /** Writes what Hibernate holds and forgets it, so what follows is read from the database itself. */
    private void startReadingFromTheDatabase() {
        entityManager.flush();
        entityManager.clear();
    }
}
