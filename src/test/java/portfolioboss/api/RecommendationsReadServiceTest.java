package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.model.HotStockLabel;

import java.time.Instant;
import java.time.LocalDate;
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

    /** The scan found all six, in this order; every one is on NASDAQ. */
    private void storeTheScan() {
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
                new Scan(SYNCED_AT, scanRows, contractDetailsByConId));
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

    private PortfolioSnapshot snapshotWithApple() {
        Holding apple = new Holding("AAPL", APPLE_CON_ID, "STK", "USD", 10, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        return new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 100_000.0, 25_000.0, List.of(apple));
    }
}
