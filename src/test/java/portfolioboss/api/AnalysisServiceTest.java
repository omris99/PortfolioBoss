package portfolioboss.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.ai.AiKeys;
import portfolioboss.ai.ClaudeReply;
import portfolioboss.ai.SearchResult;
import portfolioboss.ai.SearchResults;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.ai.StockAnalyzer;
import portfolioboss.ai.StockSearches;
import portfolioboss.ai.StockToAnalyze;
import portfolioboss.ai.TavilyClient;
import portfolioboss.api.response.AnalysisRunResponse;
import portfolioboss.api.response.FailedAnalysisResponse;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A run of the analysis against the real PostgreSQL test database, with Tavily and Claude replaced by fixed answers
 * ({@code @MockitoBean}): no test searches or calls Claude (AI_ANALYSIS_TODO.md, "fixed principles"). Same setup as
 * {@code PortfolioSyncServiceTest}; every test is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(AnalysisService.class)
class AnalysisServiceTest {

    private static final Instant SYNCED_AT = Instant.parse("2026-10-08T07:00:00Z");
    private static final StockAnalysisResult FOUND_NOTHING =
            new StockAnalysisResult(List.of(), List.of(), List.of(), null, null);
    private static final ClaudeReply REPLY = new ClaudeReply(new StockAnalysisResult(List.of(), List.of(), List.of(),
            Sentiment.NEUTRAL, "Nothing new."), "claude-sonnet-5-5", 10_500, 900, new BigDecimal("0.030000"));

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private StockAnalysisRepository stockAnalysisRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private AiKeys aiKeys;

    @MockitoBean
    private TavilyClient tavilyClient;

    @MockitoBean
    private StockAnalyzer stockAnalyzer;

    private long appleHoldingId;
    private long aevaHoldingId;
    private long intelHoldingId;

    /** Two open holdings and a closed one; both keys set; every search and every call to Claude succeeds. */
    @BeforeEach
    void storeHoldingsAndAnswerEverySearch() {
        appleHoldingId = insertHolding(265598, "AAPL", 200.0, "OPEN");
        aevaHoldingId = insertHolding(495512557, "AEVA", 5.0, "OPEN");
        intelHoldingId = insertHolding(270639, "INTC", 30.0, "CLOSED");
        given(aiKeys.findMissingKeyName()).willReturn(null);
        given(tavilyClient.searchAnalystForecasts(anyString())).willReturn(new SearchResults(List.of(), 2));
        given(tavilyClient.searchMarketBeatAnalystActions(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(tavilyClient.searchLatestAnalystActions(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(tavilyClient.searchNews(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(stockAnalyzer.analyze(any(), any(), any())).willReturn(REPLY);
    }

    @Test
    void analyzesEveryOpenHoldingWhenNoneIsNamed() {
        AnalysisRunResponse summary = analysisService.analyze(null);

        assertThat(storedAnalysisSymbols()).containsExactly("AAPL", "AEVA");
        assertThat(summary.analyzed()).isEqualTo(2);
        assertThat(summary.failed()).isEmpty();
        assertThat(summary.tavilyCredits()).isEqualTo(10);
        assertThat(summary.inputTokens()).isEqualTo(21_000);
        assertThat(summary.outputTokens()).isEqualTo(1_800);
        assertThat(summary.costUsd()).isEqualByComparingTo("0.06");
    }

    /** Decision 8: the symbol, the currency and IB's price — nothing else about the holding. */
    @Test
    void claudeIsToldOnlyTheSymbolTheCurrencyAndThePrice() {
        analysisService.analyze(List.of(appleHoldingId));

        then(stockAnalyzer).should()
                .analyze(eq(new StockToAnalyze("AAPL", "USD", 200.0)), any(), any());
    }

    @Test
    void storesWhatTheRunFoundAndWhatItCost() {
        analysisService.analyze(List.of(appleHoldingId));
        entityManager.flush();

        assertThat(jdbc.queryForMap("""
                select model, input_tokens, output_tokens, tavily_credits, cost_usd, result ->> 'sentiment' as sentiment
                from stock_analysis"""))
                .containsEntry("model", "claude-sonnet-5-5")
                .containsEntry("input_tokens", 10_500)
                .containsEntry("output_tokens", 900)
                .containsEntry("tavily_credits", 5)
                .containsEntry("cost_usd", new BigDecimal("0.030000"))
                .containsEntry("sentiment", "NEUTRAL");
    }

    /**
     * Decisions 17 and 22: a MarketBeat article about FedEx is dropped before Claude reads the results — its address
     * kept — and what is stored with the analysis is exactly what Claude read, so a missing fact can be traced to the
     * search or to Claude.
     */
    @Test
    void storesTheSearchResultsClaudeReadAndOnlyThoseAboutTheStock() {
        SearchResult aboutApple = new SearchResult("Apple Inc. $AAPL Shares Sold",
                "https://www.marketbeat.com/instant-alerts/aapl", "Fri, 09 Oct 2026 07:28:41 GMT",
                "Morgan Stanley lowered their price target on Apple from $360.00 to $355.00.");
        SearchResult aboutFedex = new SearchResult("FedEx (FDX) price target cut",
                "https://www.marketbeat.com/instant-alerts/fdx", null, "Analysts cut FedEx's target.");
        given(tavilyClient.searchMarketBeatAnalystActions("AAPL"))
                .willReturn(new SearchResults(List.of(aboutApple, aboutFedex), 1));

        analysisService.analyze(List.of(appleHoldingId));
        entityManager.flush();

        StockSearches whatClaudeRead = new StockSearches(new SearchResults(List.of(), 2),
                new SearchResults(List.of(aboutApple), 1, List.of(aboutFedex.url())), new SearchResults(List.of(), 1),
                new SearchResults(List.of(), 1));
        then(stockAnalyzer).should().analyze(any(), any(), eq(whatClaudeRead));
        assertThat(stockAnalysisRepository.findLatestOfEveryHolding().getFirst().searchResults())
                .isEqualTo(whatClaudeRead);
        assertThat(jdbc.queryForObject(
                "select search_results -> 'marketBeatActions' -> 'results' -> 0 ->> 'published_date' "
                        + "from stock_analysis", String.class))
                .isEqualTo("Fri, 09 Oct 2026 07:28:41 GMT");
        assertThat(jdbc.queryForObject(
                "select search_results -> 'marketBeatActions' -> 'droppedUrls' ->> 0 from stock_analysis", String.class))
                .isEqualTo("https://www.marketbeat.com/instant-alerts/fdx");
    }

    @Test
    void analyzesOnlyTheHoldingsNamed() {
        AnalysisRunResponse summary = analysisService.analyze(List.of(aevaHoldingId));

        assertThat(storedAnalysisSymbols()).containsExactly("AEVA");
        assertThat(summary.analyzed()).isEqualTo(1);
        then(tavilyClient).should(never()).searchAnalystForecasts("AAPL");
    }

    /** AEVA's call to Claude fails: Apple is still analyzed and stored, and AEVA keeps the analysis it had. */
    @Test
    void aStockThatFailsIsNotStoredAndKeepsItsPreviousAnalysis() {
        stockAnalysisRepository.save(new StockAnalysisEntity(aevaHoldingId, SYNCED_AT, "claude-sonnet-5-5",
                FOUND_NOTHING, null, 9_000, 800, 3, new BigDecimal("0.026000")));
        given(stockAnalyzer.analyze(argThat(stock -> stock != null && stock.symbol().equals("AEVA")), any(), any()))
                .willThrow(new IllegalStateException("Claude declined to answer"));

        AnalysisRunResponse summary = analysisService.analyze(null);

        assertThat(summary.analyzed()).isEqualTo(1);
        assertThat(summary.failed()).containsExactly(new FailedAnalysisResponse("AEVA", "Claude declined to answer"));
        assertThat(summary.tavilyCredits()).isEqualTo(5);
        entityManager.flush();
        List<StockAnalysisEntity> latest = stockAnalysisRepository.findLatestOfEveryHolding();
        assertThat(latest).extracting(StockAnalysisEntity::holdingId).containsExactly(appleHoldingId, aevaHoldingId);
        assertThat(latest.get(1).analyzedAt()).isEqualTo(SYNCED_AT);
    }

    @Test
    void aMissingKeyIs503BeforeAnythingIsSearched() {
        given(aiKeys.findMissingKeyName()).willReturn("TAVILY_API_KEY");

        assertThatThrownBy(() -> analysisService.analyze(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason())
                            .isEqualTo("The analysis is off: TAVILY_API_KEY is not set in config/local.env");
                });
        verifyNoInteractions(tavilyClient, stockAnalyzer);
    }

    @Test
    void anUnknownHoldingIs404AndAClosedOneIs400() {
        assertThatThrownBy(() -> analysisService.analyze(List.of(appleHoldingId, 999_999L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> analysisService.analyze(List.of(intelHoldingId)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).isEqualTo("INTC is closed: only open holdings are analyzed");
                });
        verifyNoInteractions(tavilyClient, stockAnalyzer);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private List<String> storedAnalysisSymbols() {
        entityManager.flush();
        return jdbc.queryForList("""
                select holding.symbol from stock_analysis join holding on holding.id = stock_analysis.holding_id
                order by holding.symbol""", String.class);
    }

    private long insertHolding(int conId, String symbol, double marketPrice, String status) {
        Timestamp syncedAt = Timestamp.from(SYNCED_AT);
        return jdbc.queryForObject("""
                insert into holding (account, con_id, symbol, sec_type, currency, position, market_price, status,
                                     first_seen_at, last_synced_at)
                values ('U1234567', ?, ?, 'STK', 'USD', 10, ?, ?, ?, ?) returning id
                """, Long.class, conId, symbol, marketPrice, status, syncedAt, syncedAt);
    }
}
