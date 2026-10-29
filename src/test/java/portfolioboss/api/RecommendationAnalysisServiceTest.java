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
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;
import portfolioboss.ai.SearchResults;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.StockAnalysisPipeline;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.ai.StockAnalyzer;
import portfolioboss.ai.StockToAnalyze;
import portfolioboss.ai.TavilyClient;
import portfolioboss.api.response.FailedAnalysisResponse;
import portfolioboss.api.response.RecommendationAnalysisRunResponse;
import portfolioboss.api.response.RecommendationPickResponse;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.FinnhubClient;
import portfolioboss.stocks.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The Recommendations page's paid run against the real PostgreSQL test database, with Tavily, Claude and Finnhub
 * replaced by fixed answers ({@code @MockitoBean}): no test searches, calls Claude or asks Finnhub. A scan of made-up
 * stocks with a year of closes each, one per calendar day:
 * <ul>
 *   <li>HOT01 … HOT11 — a rise with small waves, each a little steeper than the one before: all HOT, a little more
 *   stretched above the 50-day average each, so the page's order is HOT01 first. HOT11 is the eleventh: off the short
 *   list.</li>
 *   <li>TOOHOT — flat, then up 2% a day for 50 days. FALLING — down all year: in no list.</li>
 * </ul>
 * Three firms put every stock's target 30% above its last close, and Finnhub gives every stock the figures of a healthy
 * company, unless a test says otherwise.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({RecommendationAnalysisService.class, RecommendationsReadService.class, StockAnalysisPipeline.class,
        PortfolioSyncService.class})
class RecommendationAnalysisServiceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-05-08T21:00:00Z");
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final LocalDate START = LocalDate.of(2025, 9, 1);
    private static final int YEAR_OF_CLOSES = 250;
    private static final int HOT_STOCK_COUNT = 11;
    private static final int FIRST_HOT_CON_ID = 9001;
    private static final int TOO_HOT_CON_ID = 9101;
    private static final int FALLING_CON_ID = 9102;
    private static final CompanyFacts HEALTHY_COMPANY = new CompanyFacts(10.0, 20.0, 0.5, 30.0);
    /** Revenue falling (3) and a debt three times the equity (2): 5, more than a pick may weigh. */
    private static final CompanyFacts FALLING_REVENUE_AND_HIGH_DEBT = new CompanyFacts(-5.0, 20.0, 3.0, 30.0);
    private static final ClaudeReply REPLY = new ClaudeReply(new StockAnalysisResult(List.of(), List.of(), List.of(),
            Sentiment.NEUTRAL, "Nothing new.", null, List.of()), "claude-sonnet-5-5", 10_500, 900,
            new BigDecimal("0.030000"));
    /** Each stock's three firms put its target this many times its last close: 30% above it. */
    private static final double TARGET_TIMES_THE_CLOSE = 1.3;

    @Autowired
    private RecommendationAnalysisService recommendationAnalysisService;

    @Autowired
    private PortfolioSyncService syncService;

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

    @MockitoBean
    private FinnhubClient finnhubClient;

    /** The scan stored; every key set; every search, every call to Claude and every call to Finnhub succeeds. */
    @BeforeEach
    void storeTheScanAndAnswerEveryCall() {
        storeTheScan();
        given(aiKeys.findMissingKeyName()).willReturn(null);
        given(tavilyClient.searchAnalystForecasts(anyString())).willReturn(new SearchResults(List.of(), 2));
        given(tavilyClient.searchMarketBeatAnalystActions(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(tavilyClient.searchLatestAnalystActions(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(tavilyClient.searchNews(anyString())).willReturn(new SearchResults(List.of(), 1));
        given(stockAnalyzer.analyze(any(), any(), any())).willReturn(REPLY);
        given(finnhubClient.hasApiKey()).willReturn(true);
        given(finnhubClient.stock(anyString(), any()))
                .willAnswer(invocation -> new Stock(invocation.getArgument(0), null, HEALTHY_COMPANY));
    }

    @Test
    void theFullRunAnalyzesTheFirstTenHotCandidatesAndPicksThree() {
        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(storedAnalysisSymbols()).containsExactly("HOT01", "HOT02", "HOT03", "HOT04", "HOT05", "HOT06",
                "HOT07", "HOT08", "HOT09", "HOT10");
        assertThat(response.analysis().analyzed()).isEqualTo(10);
        assertThat(response.analysis().tavilyCredits()).isEqualTo(50);
        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol)
                .containsExactly("HOT01", "HOT02", "HOT03");
        assertThat(response.picks().getFirst().candidateId()).isEqualTo(candidateIdOf("HOT01"));
        assertThat(response.picks().getFirst().riskSigns()).isEmpty();
        then(tavilyClient).should(never()).searchNews("HOT11");
        then(tavilyClient).should(never()).searchNews("TOOHOT");
    }

    /**
     * Decision 14: HOT01's firms put its target 10% above its price and HOT03's below it, so neither is analyzed, and
     * the short list is the nine other HOT stocks — HOT11, the eleventh, among them.
     */
    @Test
    void theShortListTakesOnlyTheStocksWithTheAnalystsTarget15PercentAbove() {
        changeTargetOf("HOT01", 1.1);
        changeTargetOf("HOT03", 0.9);

        recommendationAnalysisService.analyze(null);

        assertThat(storedAnalysisSymbols()).containsExactly("HOT02", "HOT04", "HOT05", "HOT06", "HOT07", "HOT08",
                "HOT09", "HOT10", "HOT11");
    }

    /** No stock with enough upside: nothing is searched or asked, and "no recommendation this time" is stored. */
    @Test
    void withoutAStockWithEnoughUpsideNothingIsPaidForAndNoRecommendationIsStored() {
        jdbc.update("update analyst_price_target set price_target = 1");

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);
        entityManager.flush();

        assertThat(response.analysis().analyzed()).isZero();
        assertThat(response.picks()).isEmpty();
        verifyNoInteractions(tavilyClient, stockAnalyzer);
        then(finnhubClient).should(never()).stock(anyString(), any());
        assertThat(jdbc.queryForObject("select picks::text from investment_recommendation", String.class))
                .isEqualTo("[]");
    }

    @Test
    void aPickCarriesHowFarTheAnalystsTargetIsAboveItsPrice() {
        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(response.picks().getFirst().targetUpsidePercent()).isCloseTo(30, within(1e-9));
    }

    /** V11: the full run's picks are kept, in their order, with the scan they came from — for the measurement. */
    @Test
    void theFullRunStoresItsPicksInTheirOrder() {
        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);
        entityManager.flush();

        Map<String, Object> stored = jdbc.queryForMap("""
                select scan_id, picks::text as picks from investment_recommendation""");
        long latestScanId = jdbc.queryForObject("select max(id) from scan", Long.class);
        String expectedPicks = response.picks().stream()
                .map(pick -> String.valueOf(pick.candidateId()))
                .collect(Collectors.joining(", ", "[", "]"));
        assertThat(stored.get("scan_id")).isEqualTo(latestScanId);
        assertThat(stored.get("picks")).isEqualTo(expectedPicks);
    }

    /** "No recommendation this time" is an answer too: stored with no picks. */
    @Test
    void aRunThatPicksNoneIsStoredWithNoPicks() {
        given(finnhubClient.stock(anyString(), any()))
                .willAnswer(invocation -> new Stock(invocation.getArgument(0), null, FALLING_REVENUE_AND_HIGH_DEBT));

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);
        entityManager.flush();

        assertThat(response.picks()).isEmpty();
        assertThat(jdbc.queryForObject("select picks::text from investment_recommendation", String.class))
                .isEqualTo("[]");
    }

    /** One candidate's analysis makes no recommendation, so it stores none. */
    @Test
    void oneCandidatesAnalysisStoresNoRecommendation() {
        recommendationAnalysisService.analyze(candidateIdOf("HOT01"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("select count(*) from investment_recommendation", Integer.class)).isZero();
    }

    /** Decision 6: the symbol, the dollar and the last close — nothing about the portfolio. */
    @Test
    void claudeIsToldOnlyTheSymbolTheCurrencyAndTheLastClose() {
        recommendationAnalysisService.analyze(candidateIdOf("HOT01"));

        then(stockAnalyzer).should()
                .analyze(eq(new StockToAnalyze("HOT01", "USD", hotClose(1, YEAR_OF_CLOSES - 1))), any(), any());
    }

    @Test
    void storesFinnhubsAnswerWithTheAnalysis() {
        recommendationAnalysisService.analyze(candidateIdOf("HOT01"));
        entityManager.flush();

        assertThat(jdbc.queryForObject("""
                select finnhub_stock -> 'companyFacts' ->> 'peRatio' from stock_analysis""", String.class))
                .isEqualTo("30.0");
    }

    @Test
    void aCandidateWhoseRiskSignsWeighMoreThanTwoIsNotPicked() {
        given(finnhubClient.stock(eq("HOT01"), any())).willReturn(new Stock("HOT01", null, FALLING_REVENUE_AND_HIGH_DEBT));

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol)
                .containsExactly("HOT02", "HOT03", "HOT04");
    }

    /**
     * The weight comes before the list's order: HOT10, the last of the short list and the only one with no sign, is
     * picked before HOT01, whose high debt weighs 2. HOT02 … HOT09 weigh 5 and are not picked.
     */
    @Test
    void theLighterRiskIsPickedFirst() {
        given(finnhubClient.stock(eq("HOT01"), any()))
                .willReturn(new Stock("HOT01", null, new CompanyFacts(10.0, 20.0, 3.0, 30.0)));
        for (int hotStockNumber = 2; hotStockNumber <= 9; hotStockNumber++) {
            String symbol = "HOT%02d".formatted(hotStockNumber);
            given(finnhubClient.stock(eq(symbol), any()))
                    .willReturn(new Stock(symbol, null, FALLING_REVENUE_AND_HIGH_DEBT));
        }

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol).containsExactly("HOT10", "HOT01");
        assertThat(response.picks().getLast().riskSigns()).containsExactly(RiskSign.HIGH_DEBT);
    }

    /** Decision 13: HOT05's news reports a partnership with a giant (+3), which puts it before HOT01 … HOT04 (0). */
    @Test
    void goodNewsPutsALaterCandidateFirst() {
        NewsEvent partnership = new NewsEvent(NewsEventType.PARTNERSHIP, EventMagnitude.HIGH,
                "HOT05 expanded its partnership with a giant.", "HOT05 and a giant", "https://example.com/hot05");
        ClaudeReply replyWithAPartnership = new ClaudeReply(new StockAnalysisResult(List.of(), List.of(), List.of(),
                Sentiment.POSITIVE, "A partnership.", null, List.of(partnership)), "claude-sonnet-5-5", 10_500, 900,
                new BigDecimal("0.030000"));
        given(stockAnalyzer.analyze(argThat(stock -> stock != null && stock.symbol().equals("HOT05")), any(), any()))
                .willReturn(replyWithAPartnership);

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol)
                .containsExactly("HOT05", "HOT01", "HOT02");
        assertThat(response.picks().getFirst().newsScore()).isEqualTo(3);
        assertThat(response.picks().getFirst().score()).isEqualTo(3);
    }

    /** Finnhub refusing a call: the analysis is stored all the same, without the figures — and is never picked. */
    @Test
    void whenFinnhubFailsTheAnalysisIsStoredWithoutItsFiguresAndNotPicked() {
        given(finnhubClient.stock(eq("HOT01"), any())).willThrow(new IllegalStateException("401 Unauthorized"));

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);
        entityManager.flush();

        assertThat(response.analysis().analyzed()).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
                select stock_analysis.finnhub_stock is null from stock_analysis
                join scan_candidate on scan_candidate.id = stock_analysis.scan_candidate_id
                where scan_candidate.symbol = 'HOT01'""", Boolean.class)).isTrue();
        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol)
                .containsExactly("HOT02", "HOT03", "HOT04");
    }

    @Test
    void aCandidateThatFailsIsNeitherStoredNorPicked() {
        given(stockAnalyzer.analyze(argThat(stock -> stock != null && stock.symbol().equals("HOT01")), any(), any()))
                .willThrow(new IllegalStateException("Claude declined to answer"));

        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(null);

        assertThat(storedAnalysisSymbols()).hasSize(9).doesNotContain("HOT01");
        assertThat(response.analysis().failed())
                .containsExactly(new FailedAnalysisResponse("HOT01", "Claude declined to answer"));
        assertThat(response.picks()).extracting(RecommendationPickResponse::symbol)
                .containsExactly("HOT02", "HOT03", "HOT04");
    }

    /** One candidate, from any list: its analysis, and no recommendation. */
    @Test
    void oneCandidateIsAnalyzedWithoutARecommendation() {
        RecommendationAnalysisRunResponse response = recommendationAnalysisService.analyze(candidateIdOf("TOOHOT"));

        assertThat(storedAnalysisSymbols()).containsExactly("TOOHOT");
        assertThat(response.analysis().analyzed()).isEqualTo(1);
        assertThat(response.picks()).isNull();
    }

    /** Without Finnhub no stock could be picked, so the full run spends nothing; one candidate is analyzed without it. */
    @Test
    void withoutFinnhubsKeyTheFullRunIs503ButOneCandidateIsAnalyzed() {
        given(finnhubClient.hasApiKey()).willReturn(false);

        assertThatThrownBy(() -> recommendationAnalysisService.analyze(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).isEqualTo("The recommendation is off: FINNHUB_API_KEY is not "
                            + "set in config/local.env, and without the company figures no stock can be picked");
                });
        verifyNoInteractions(tavilyClient, stockAnalyzer);

        recommendationAnalysisService.analyze(candidateIdOf("HOT01"));

        assertThat(storedAnalysisSymbols()).containsExactly("HOT01");
        then(finnhubClient).should(never()).stock(anyString(), any());
    }

    @Test
    void aMissingKeyOfTheAnalysisIs503BeforeAnythingIsSearched() {
        given(aiKeys.findMissingKeyName()).willReturn("ANTHROPIC_API_KEY");

        assertThatThrownBy(() -> recommendationAnalysisService.analyze(candidateIdOf("HOT01")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason())
                            .isEqualTo("The analysis is off: ANTHROPIC_API_KEY is not set in config/local.env");
                });
        verifyNoInteractions(tavilyClient, stockAnalyzer, finnhubClient);
    }

    /** FALLING is in the scan but in no list; 999 999 is in no scan at all. */
    @Test
    void aCandidateThatIsNotInTheLatestScansListsIs404() {
        long fallingCandidateId = candidateIdOf("FALLING");

        assertThatThrownBy(() -> recommendationAnalysisService.analyze(fallingCandidateId))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason())
                            .isEqualTo("No candidate with id " + fallingCandidateId + " in the latest scan's lists");
                });
        assertThatThrownBy(() -> recommendationAnalysisService.analyze(999_999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(tavilyClient, stockAnalyzer);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** The scan found all thirteen, in this order; every one is on NASDAQ. Then, as in a new request, nothing held. */
    private void storeTheScan() {
        Map<Integer, String> symbolByConId = new LinkedHashMap<>();
        Map<Integer, List<DailyClose>> dailyCloses = new HashMap<>();
        for (int hotStockNumber = 1; hotStockNumber <= HOT_STOCK_COUNT; hotStockNumber++) {
            int conId = FIRST_HOT_CON_ID + hotStockNumber - 1;
            int steepness = hotStockNumber;
            symbolByConId.put(conId, "HOT%02d".formatted(hotStockNumber));
            dailyCloses.put(conId, series(day -> hotClose(steepness, day)));
        }
        symbolByConId.put(TOO_HOT_CON_ID, "TOOHOT");
        dailyCloses.put(TOO_HOT_CON_ID, series(day -> day < 200 ? 100 : 100 * Math.pow(1.02, day - 199)));
        symbolByConId.put(FALLING_CON_ID, "FALLING");
        dailyCloses.put(FALLING_CON_ID, series(day -> 200 - day * 0.4));

        List<ScanRow> scanRows = new ArrayList<>();
        Map<Integer, StockContractDetails> contractDetailsByConId = new HashMap<>();
        Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId = new HashMap<>();
        symbolByConId.forEach((conId, symbol) -> {
            scanRows.add(new ScanRow(THREE_MONTH_SCAN, scanRows.size(), conId, symbol));
            contractDetailsByConId.put(conId,
                    new StockContractDetails(conId, "NASDAQ", symbol + " INC", "Technology", "Software"));
            double lastClose = dailyCloses.get(conId).getLast().close();
            analystPriceTargetsByConId.put(conId, threeFirmsTargets(lastClose * TARGET_TIMES_THE_CLOSE));
        });
        Scan scan = new Scan(SCANNED_AT, scanRows, contractDetailsByConId)
                .withAnalystPriceTargets(analystPriceTargetsByConId);
        syncService.syncScan(scan, dailyCloses);
        entityManager.flush();
        entityManager.clear();
    }

    /** The same target from three firms, a week before the scan. */
    private List<AnalystPriceTarget> threeFirmsTargets(double priceTarget) {
        LocalDateTime aWeekBeforeTheScan = LocalDateTime.of(2026, 5, 1, 12, 0);
        return List.of("Wells Fargo", "Barclays", "Needham").stream()
                .map(firm -> new AnalystPriceTarget(aWeekBeforeTheScan, firm, priceTarget, null, "A headline"))
                .toList();
    }

    /** Every firm's target of the stock becomes this many times its last close. */
    private void changeTargetOf(String symbol, double timesTheClose) {
        int hotStockNumber = Integer.parseInt(symbol.substring("HOT".length()));
        double lastClose = hotClose(hotStockNumber, YEAR_OF_CLOSES - 1);
        jdbc.update("update analyst_price_target set price_target = ? where con_id = ?",
                lastClose * timesTheClose, FIRST_HOT_CON_ID + hotStockNumber - 1);
    }

    /** A rise with small waves, 0.1 a day steeper with each {@code steepness}: a little more above the 50-day average. */
    private double hotClose(int steepness, int day) {
        return 100 + day * (0.1 + steepness * 0.01) + 3 * Math.sin(day / 3.0);
    }

    private List<DailyClose> series(IntToDoubleFunction priceOfDay) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = 0; day < YEAR_OF_CLOSES; day++) {
            closes.add(new DailyClose(START.plusDays(day), priceOfDay.applyAsDouble(day)));
        }
        return closes;
    }

    private long candidateIdOf(String symbol) {
        return jdbc.queryForObject("select id from scan_candidate where symbol = ?", Long.class, symbol);
    }

    private List<String> storedAnalysisSymbols() {
        entityManager.flush();
        return jdbc.queryForList("""
                select scan_candidate.symbol from stock_analysis
                join scan_candidate on scan_candidate.id = stock_analysis.scan_candidate_id
                order by scan_candidate.symbol""", String.class);
    }
}
