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
import portfolioboss.ai.Headline;
import portfolioboss.ai.RatingCounts;
import portfolioboss.ai.SearchResult;
import portfolioboss.ai.SearchResults;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.ai.StockSearches;

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
        assertThat(readBack.searchResults()).isEqualTo(appleSearches());
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

        assertThat(resultKeys).containsExactly("consensusBySource", "headlines", "recentActions", "sentiment",
                "sentimentReason");
        assertThat(sourceKeys).containsExactly("analystCount", "averageTarget", "publishedDate", "ratingCounts",
                "ratingLabel", "sourceUrl");
        assertThat(publishedDate).isEqualTo("2026-10-05");
    }

    /**
     * An analysis stored on 2026-10-09 is shaped as the record was then: it holds Claude's own trend, which the code
     * works out now (decision 18), its actions have no result date (decision 20), and no search results were kept with
     * it (decision 22). The extra key is skipped and the missing ones read as {@code null} — Jackson's defaults — and
     * the rest comes back.
     */
    @Test
    void anAnalysisStoredInAnEarlierShapeStillReads() {
        long appleHoldingId = insertHolding(265598, "AAPL");
        jdbc.update("""
                insert into stock_analysis (holding_id, analyzed_at, model, result, input_tokens, output_tokens,
                                            tavily_credits, cost_usd)
                values (?, ?, 'claude-sonnet-5-5', ?::jsonb, 10500, 900, 3, 0.03)
                """, appleHoldingId, Timestamp.from(MONDAY), """
                {"consensusBySource": [], "analystTrend": "IMPROVING",
                 "recentActions": [{"date": "2026-09-03", "firm": "Mizuho Securities", "action": "TARGET_LOWERED",
                                    "fromRating": "Hold", "toRating": "Hold", "previousPriceTarget": 109.0,
                                    "priceTarget": 92.0, "url": "https://stockanalysis.com/stocks/intc/forecast"}],
                 "headlines": [], "sentiment": "POSITIVE", "sentimentReason": "Strong quarter."}
                """);

        StockAnalysisEntity readBack = stockAnalysisRepository.findLatestOfEveryHolding().getFirst();

        AnalystAction mizuho = new AnalystAction(LocalDate.of(2026, 9, 3), "Mizuho Securities",
                AnalystActionType.TARGET_LOWERED, "Hold", "Hold", 109.0, 92.0,
                "https://stockanalysis.com/stocks/intc/forecast", null);
        assertThat(readBack.result()).isEqualTo(new StockAnalysisResult(List.of(), List.of(mizuho), List.of(),
                Sentiment.POSITIVE, "Strong quarter."));
        assertThat(readBack.searchResults()).isNull();
    }

    /** NVDA's analysis of 2026-10-10 11:14 kept its search results before the addresses left out were: none, then. */
    @Test
    void searchResultsStoredBeforeTheAddressesLeftOutWereKeptReadWithNone() {
        long nvidiaHoldingId = insertHolding(4815747, "NVDA");
        jdbc.update("""
                insert into stock_analysis (holding_id, analyzed_at, model, result, search_results, input_tokens,
                                            output_tokens, tavily_credits, cost_usd)
                values (?, ?, 'claude-sonnet-5-5', ?::jsonb, ?::jsonb, 10500, 900, 5, 0.04)
                """, nvidiaHoldingId, Timestamp.from(MONDAY), """
                {"consensusBySource": [], "recentActions": [], "headlines": [], "sentiment": null,
                 "sentimentReason": null}
                """, """
                {"analystForecasts": {"results": [], "credits": 2}, "marketBeatActions": {"results": [], "credits": 1},
                 "latestActions": {"results": [], "credits": 1}, "news": {"results": [], "credits": 1}}
                """);

        StockSearches readBack = stockAnalysisRepository.findLatestOfEveryHolding().getFirst().searchResults();

        assertThat(readBack.marketBeatActions().droppedUrls()).isEmpty();
        assertThat(readBack.credits()).isEqualTo(5);
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
                List.of(new AnalystAction(LocalDate.of(2026, 10, 2), "Some Firm", AnalystActionType.TARGET_LOWERED,
                        "Overweight", "Overweight", 360.0, 355.0, "https://example.com/action",
                        LocalDate.of(2026, 10, 4))),
                List.of(new Headline(null, "Apple unveils a new product", "Reuters", "https://example.com/news")),
                Sentiment.NEUTRAL,
                null);
    }

    /** One result in each of the four searches, Tavily's date written its own way, and one with no date at all. */
    private StockSearches appleSearches() {
        return new StockSearches(
                new SearchResults(List.of(new SearchResult("Apple (AAPL) Stock Forecast", "https://financhill.com/aapl",
                        "2026-10-05", "48 analysts: 30 Buy, 16 Hold, 2 Sell.")), 2),
                new SearchResults(List.of(new SearchResult("Apple Inc. $AAPL Shares Sold by Some Fund",
                        "https://www.marketbeat.com/instant-alerts/aapl", "Fri, 09 Oct 2026 07:28:41 GMT",
                        "Morgan Stanley lowered their price target on Apple from $360.00 to $355.00.")), 1),
                new SearchResults(List.of(), 1),
                new SearchResults(List.of(new SearchResult("AAPL shares rise", "https://example.com/news", null,
                        "Shares of Apple rose 2%.")), 1));
    }

    private StockAnalysisEntity analysisOf(long holdingId, Instant analyzedAt, StockAnalysisResult result) {
        return new StockAnalysisEntity(holdingId, analyzedAt, "claude-sonnet-5-5", result, appleSearches(), 10500, 900,
                5, new BigDecimal("0.030000"));
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
