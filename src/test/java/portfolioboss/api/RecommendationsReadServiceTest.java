package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.LatestRecommendationResponse;
import portfolioboss.api.response.PickedStockResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.db.InvestmentRecommendationEntity;
import portfolioboss.db.InvestmentRecommendationRepository;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.db.ScanCandidateEntity;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.model.HotStockLabel;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;
import portfolioboss.stocks.Stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntToDoubleFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The Recommendations read against the real PostgreSQL test database: a sync stores a holding, a scan of made-up stocks
 * and their closes, and the read sorts them into the lists. Each stock has a year of closes, one per calendar day from
 * {@link #START}, shaped for its list:
 * <ul>
 *   <li>AAPL, the one holding — a rise with small waves, 5% above its 50-day average: {@code HOT}. The scan found it too.</li>
 *   <li>HOTA — AAPL's closes at half the price: the same daily returns, so it moves exactly with AAPL.</li>
 *   <li>TOOHOT — flat, then up 2% a day for 50 days: far above its 50-day average.</li>
 *   <li>CORRECTING — up from 100 to 200, then down to 180: under its 50-day average, still above the 200-day one.</li>
 *   <li>FALLING — down all year: no long upward trend, in no list. NOCLOSES — none came: in no list either.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, RecommendationsReadService.class})
class RecommendationsReadServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant SYNCED_AT = Instant.parse("2026-05-08T21:00:00Z");
    /** A day later: the next scan finds the same stocks again, as new rows. */
    private static final Instant NEXT_SCAN = Instant.parse("2026-05-09T21:00:00Z");
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final LocalDate START = LocalDate.of(2025, 9, 1);
    private static final int YEAR_OF_CLOSES = 250;
    private static final int APPLE_CON_ID = 265598;
    // made-up stocks the scan found
    private static final int HOTA_CON_ID = 9001;
    private static final int TOO_HOT_CON_ID = 9002;
    private static final int CORRECTING_CON_ID = 9003;
    private static final int FALLING_CON_ID = 9004;
    private static final int NO_CLOSES_CON_ID = 9005;

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private RecommendationsReadService recommendationsReadService;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private StockAnalysisRepository stockAnalysisRepository;

    @Autowired
    private InvestmentRecommendationRepository investmentRecommendationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void nothingBeforeTheFirstScan() {
        syncService.sync(snapshotWithApple());

        assertThat(readAfterTheSync()).isEmpty();
    }

    @Test
    void putsEachStockInItsListAndCountsTheRest() {
        storeTheScan();

        RecommendationsResponse recommendations = readAfterTheSync().orElseThrow();

        assertThat(recommendations.scannedAt()).isEqualTo(SYNCED_AT);
        assertThat(recommendations.candidateCount()).isEqualTo(6);
        assertThat(recommendations.hotStocks()).extracting(HotStockResponse::symbol)
                .containsExactlyInAnyOrder("AAPL", "HOTA");
        assertThat(recommendations.tooHotStocks()).extracting(HotStockResponse::symbol).containsExactly("TOOHOT");
        assertThat(recommendations.correctingStocks()).extracting(HotStockResponse::symbol)
                .containsExactly("CORRECTING");
        assertThat(recommendations.latestRecommendation()).isNull();
    }

    /**
     * Decision 12: the latest recommendation stays on the page after a newer scan, with the date of its own scan, its
     * stocks in its order and each one's contract id — by which the page finds it in today's lists.
     */
    @Test
    void theLatestRecommendationIsShownWithItsOwnScansDate() {
        storeTheScan();
        long firstScanId = jdbc.queryForObject("select id from scan", Long.class);
        long hotaCandidateId = candidateNamed("HOTA").id();
        long appleCandidateId = candidateNamed("AAPL").id();
        investmentRecommendationRepository.save(new InvestmentRecommendationEntity(firstScanId,
                SYNCED_AT.plusSeconds(3600), List.of(hotaCandidateId, appleCandidateId)));
        storeTheScanAt(NEXT_SCAN);

        LatestRecommendationResponse latest = readAfterTheSync().orElseThrow().latestRecommendation();

        assertThat(latest.recommendedAt()).isEqualTo(SYNCED_AT.plusSeconds(3600));
        assertThat(latest.scannedAt()).isEqualTo(SYNCED_AT);
        assertThat(latest.picks()).extracting(PickedStockResponse::symbol).containsExactly("HOTA", "AAPL");
        assertThat(latest.picks().getFirst().candidateId()).isEqualTo(hotaCandidateId);
        assertThat(latest.picks().getFirst().conId()).isEqualTo(HOTA_CON_ID);
    }

    @Test
    void servesWhatTheScanStoredAndTheRulesVerdict() {
        storeTheScan();

        HotStockResponse hotStock = stockNamed("HOTA");
        HotStockResponse correctingStock = stockNamed("CORRECTING");

        assertThat(hotStock.conId()).isEqualTo(HOTA_CON_ID);
        assertThat(hotStock.companyName()).isEqualTo("HOTA INC");
        assertThat(hotStock.exchange()).isEqualTo("NASDAQ");
        assertThat(hotStock.industry()).isEqualTo("Technology");
        assertThat(hotStock.label()).isEqualTo(HotStockLabel.HOT);
        assertThat(hotStock.percentAboveSma50()).isBetween(0.0, 15.0);
        assertThat(hotStock.momentum().monthsInMarket()).isEqualTo(8);   // 250 days: 2025-09-01 to 2026-05-08
        assertThat(correctingStock.percentAboveSma50()).isNegative();
    }

    /**
     * Decision 12: HOTA is found again in the next scan, as a new row, and keeps the latest analysis of its row in the
     * first one. Its risk signs and the timing note come from that analysis's Finnhub answer: a debt 2.5 times the
     * equity, and a report ten days after the latest scan.
     */
    @Test
    void aStockKeepsItsLatestAnalysisFromAnEarlierScan() {
        storeTheScan();
        ScanCandidateEntity hotaOfTheFirstScan = candidateNamed("HOTA");
        Stock finnhubStock = new Stock("HOTA", new EarningsDate(dateOf(NEXT_SCAN).plusDays(10), "bmo"),
                new CompanyFacts(8.0, 15.0, 2.5, 25.0));
        stockAnalysisRepository.save(analysisOf(hotaOfTheFirstScan, null, SYNCED_AT.plusSeconds(3600)));
        stockAnalysisRepository.save(analysisOf(hotaOfTheFirstScan, finnhubStock, SYNCED_AT.plusSeconds(7200)));
        storeTheScanAt(NEXT_SCAN);

        HotStockResponse hota = stockNamed("HOTA");

        assertThat(hota.candidateId()).isNotEqualTo(hotaOfTheFirstScan.id());
        assertThat(hota.analysis().analyzedAt()).isEqualTo(SYNCED_AT.plusSeconds(7200));
        assertThat(hota.companyFacts()).isEqualTo(finnhubStock.companyFacts());
        assertThat(hota.nextEarningsDate()).isEqualTo(finnhubStock.nextEarningsDate());
        assertThat(hota.riskSigns()).containsExactly(RiskSign.HIGH_DEBT);
        assertThat(hota.reportsWithin14Days()).isTrue();
    }

    @Test
    void aStockNeverAnalyzedHasNoAnalysisNorRiskSigns() {
        storeTheScan();

        HotStockResponse tooHot = stockNamed("TOOHOT");

        assertThat(tooHot.analysis()).isNull();
        assertThat(tooHot.nextEarningsDate()).isNull();
        assertThat(tooHot.companyFacts()).isNull();
        assertThat(tooHot.riskSigns()).isNull();
        assertThat(tooHot.reportsWithin14Days()).isFalse();
    }

    /** Without Finnhub's answer four of the five signs were never checked: no list of signs — not an empty one. */
    @Test
    void withoutFinnhubsAnswerTheRiskSignsAreUnknown() {
        storeTheScan();
        stockAnalysisRepository.save(analysisOf(candidateNamed("HOTA"), null, SYNCED_AT.plusSeconds(3600)));

        HotStockResponse hota = stockNamed("HOTA");

        assertThat(hota.analysis()).isNotNull();
        assertThat(hota.companyFacts()).isNull();
        assertThat(hota.riskSigns()).isNull();
    }

    @Test
    void aStockMovingWithAHoldingSaysSo() {
        storeTheScan();

        HotStockResponse hotStock = stockNamed("HOTA");

        assertThat(hotStock.correlations()).hasSize(1);
        CorrelationResponse withApple = hotStock.correlations().getFirst();
        assertThat(withApple.symbol()).isEqualTo("AAPL");
        assertThat(withApple.coefficient()).isCloseTo(1, within(1e-9));
        assertThat(withApple.movesTogether()).isTrue();
        assertThat(hotStock.heldByInvestorIds()).isEmpty();
    }

    /** AAPL is held with no trades entered, so all of it is the account owner's. */
    @Test
    void aStockAlreadyHeldNamesWhoHoldsItAndIsNotComparedWithItself() {
        storeTheScan();

        HotStockResponse apple = stockNamed("AAPL");

        assertThat(apple.heldByInvestorIds()).containsExactly(investorRepository.accountOwner().id());
        assertThat(apple.correlations()).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private void storeTheScan() {
        storeTheScanAt(SYNCED_AT);
    }

    /** The scan found all six, in this order; every one is on NASDAQ. */
    private void storeTheScanAt(Instant scannedAt) {
        Map<Integer, String> symbolByConId = new LinkedHashMap<>();
        symbolByConId.put(APPLE_CON_ID, "AAPL");
        symbolByConId.put(HOTA_CON_ID, "HOTA");
        symbolByConId.put(TOO_HOT_CON_ID, "TOOHOT");
        symbolByConId.put(CORRECTING_CON_ID, "CORRECTING");
        symbolByConId.put(FALLING_CON_ID, "FALLING");
        symbolByConId.put(NO_CLOSES_CON_ID, "NOCLOSES");
        List<ScanRow> scanRows = new ArrayList<>();
        Map<Integer, StockContractDetails> contractDetailsByConId = new HashMap<>();
        symbolByConId.forEach((conId, symbol) -> {
            scanRows.add(new ScanRow(THREE_MONTH_SCAN, scanRows.size(), conId, symbol));
            contractDetailsByConId.put(conId,
                    new StockContractDetails(conId, "NASDAQ", symbol + " INC", "Technology", "Software"));
        });

        syncService.sync(snapshotWithApple(), Map.of(
                APPLE_CON_ID, series(this::appleClose),
                HOTA_CON_ID, series(day -> appleClose(day) / 2),
                TOO_HOT_CON_ID, series(this::flatThenSharpRise),
                CORRECTING_CON_ID, series(this::riseThenPullback),
                FALLING_CON_ID, series(day -> 200 - day * 0.4)),
                new Scan(scannedAt, scanRows, contractDetailsByConId));
    }

    /** The stock's row in the only scan stored so far. */
    private ScanCandidateEntity candidateNamed(String symbol) {
        entityManager.flush();
        long candidateId = jdbc.queryForObject("select id from scan_candidate where symbol = ?", Long.class, symbol);
        return entityManager.find(ScanCandidateEntity.class, candidateId);
    }

    /** An analysis that found nothing; {@code finnhubStock} is what Finnhub answered, or {@code null}. */
    private StockAnalysisEntity analysisOf(ScanCandidateEntity candidate, Stock finnhubStock, Instant analyzedAt) {
        return new StockAnalysisEntity(candidate, finnhubStock, analyzedAt, "claude-sonnet-5-5",
                new StockAnalysisResult(List.of(), List.of(), List.of(), null, null, null, List.of()), null, 10_500,
                900, 5,
                new BigDecimal("0.030000"));
    }

    /**
     * The API reads in a later request than the sync, in a new process: {@code clear()} gives the test the same
     * starting point, so the read loads the scan and its candidates from the database.
     */
    private Optional<RecommendationsResponse> readAfterTheSync() {
        entityManager.flush();
        entityManager.clear();
        return recommendationsReadService.currentRecommendations();
    }

    private HotStockResponse stockNamed(String symbol) {
        RecommendationsResponse recommendations = readAfterTheSync().orElseThrow();
        return Stream.of(recommendations.hotStocks(), recommendations.tooHotStocks(),
                        recommendations.correctingStocks())
                .flatMap(List::stream)
                .filter(stock -> stock.symbol().equals(symbol))
                .findFirst()
                .orElseThrow();
    }

    private double appleClose(int day) {
        return 100 + day * 0.2 + 3 * Math.sin(day / 3.0);
    }

    /** 100 for 200 days, then 2% up every day: 269 at the end, against a 50-day average of about 172. */
    private double flatThenSharpRise(int day) {
        return day < 200 ? 100 : 100 * Math.pow(1.02, day - 199);
    }

    /** From 100 up to 200 on day 229, then down 1 a day to 180: under the 50-day average (192), above the 200-day (164). */
    private double riseThenPullback(int day) {
        return day <= 229 ? 100 + day * 100.0 / 229 : 200 - (day - 229);
    }

    private List<DailyClose> series(IntToDoubleFunction priceOfDay) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = 0; day < YEAR_OF_CLOSES; day++) {
            closes.add(new DailyClose(START.plusDays(day), priceOfDay.applyAsDouble(day)));
        }
        return closes;
    }

    /** The day in this machine's time zone, as the read counts the timing note from. */
    private LocalDate dateOf(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private PortfolioSnapshot snapshotWithApple() {
        Holding apple = new Holding("AAPL", APPLE_CON_ID, "STK", "USD", 10, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        return new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 100_000.0, 25_000.0, List.of(apple));
    }
}
