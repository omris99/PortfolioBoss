package portfolioboss.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the sync against the real PostgreSQL test database ({@code portfolioboss_test}, see
 * {@code application-test.properties}); never against {@code portfolioboss}.
 *
 * <p>{@code @DataJpaTest} starts only the database layer, and every test runs in a transaction that is rolled
 * back at the end, so no test leaves rows behind. {@code replace = NONE} keeps PostgreSQL instead of swapping in
 * an in-memory database. Results are read back with plain SQL: the test then sees what is really in the columns,
 * not what Hibernate remembers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PortfolioSyncService.class)
class PortfolioSyncServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final int APPLE_CON_ID = 265598;
    private static final int MICROSOFT_CON_ID = 272093;
    private static final Instant FIRST_RUN = Instant.parse("2026-09-21T08:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-09-22T08:00:00Z");
    private static final Instant THIRD_RUN = Instant.parse("2026-09-23T08:00:00Z");
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final String SIX_MONTH_SCAN = "SCAN_histPerfRatio6m_DESC";
    /** Made-up stocks a scan found. */
    private static final int HOTA_CON_ID = 9001;
    private static final int HOTB_CON_ID = 9002;

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAHoldingSeenForTheFirstTime() {
        SyncResult result = syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));

        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
        assertThat(rowOf(APPLE_CON_ID))
                .containsEntry("symbol", "AAPL")
                .containsEntry("status", "OPEN")
                .containsEntry("position", 10.0)
                .containsEntry("average_cost", 150.0)
                .containsEntry("sector", null);
    }

    @Test
    void refreshesIbFiguresButNeverTheSectorOrTheTrades() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        jdbc.update("update holding set sector = 'Technology' where id = ?", appleHoldingId);
        insertTrade(appleHoldingId);
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(SECOND_RUN, apple(12, 160.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(rowOf(APPLE_CON_ID))
                .containsEntry("position", 12.0)
                .containsEntry("average_cost", 160.0)
                .containsEntry("sector", "Technology");
        assertThat(tradeCountOf(appleHoldingId)).isEqualTo(1);
    }

    @Test
    void marksAHoldingThatLeftTwsAsClosedAndKeepsItsRowAndTrades() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0), microsoft(5, 300.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        insertTrade(appleHoldingId);
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(SECOND_RUN, microsoft(5, 300.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 1));
        Map<String, Object> row = rowOf(APPLE_CON_ID);
        assertThat(row)
                .containsEntry("status", "CLOSED")
                .containsEntry("position", 0.0)
                .containsEntry("market_value", 0.0)
                .containsEntry("unrealized_pnl", 0.0)
                .containsEntry("average_cost", 150.0);
        assertThat(instantOf(row, "closed_detected_at")).isEqualTo(SECOND_RUN);
        assertThat(instantOf(row, "last_synced_at")).isEqualTo(FIRST_RUN);
        assertThat(tradeCountOf(appleHoldingId)).isEqualTo(1);
    }

    @Test
    void reopensAClosedHoldingThatComesBack() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));
        startNextRun();
        syncService.sync(snapshotOf(SECOND_RUN));   // Apple is gone from the portfolio
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(THIRD_RUN, apple(8, 155.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(rowOf(APPLE_CON_ID))
                .containsEntry("status", "OPEN")
                .containsEntry("position", 8.0)
                .containsEntry("closed_detected_at", null);
    }

    @Test
    void storesFiguresIbDidNotReportAsNull() {
        Holding withoutCostData = new Holding(
                "MSFT", MICROSOFT_CON_ID, "STK", "USD", 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, ACCOUNT);

        syncService.sync(snapshotOf(FIRST_RUN, withoutCostData));

        assertThat(rowOf(MICROSOFT_CON_ID))
                .containsEntry("position", 5.0)
                .containsEntry("average_cost", null)
                .containsEntry("market_price", null)
                .containsEntry("market_value", null)
                .containsEntry("unrealized_pnl", null);
    }

    @Test
    void keepsTwoHoldingsThatShareASymbolAsTwoRows() {
        syncService.sync(snapshotOf(FIRST_RUN, holding("BRK", 1001, 10, 100.0), holding("BRK", 1002, 5, 100.0)));

        assertThat(countOf("select count(*) from holding where symbol = 'BRK'")).isEqualTo(2);
    }

    @Test
    void keepsOneAccountStateRowAndReplacesItOnEachSync() {
        syncService.sync(new PortfolioSnapshot(ACCOUNT, FIRST_RUN, 100_000.0, 25_000.0, List.of()));
        startNextRun();

        syncService.sync(new PortfolioSnapshot(ACCOUNT, SECOND_RUN, Double.NaN, 30_000.0, List.of()));

        assertThat(countOf("select count(*) from account_state")).isEqualTo(1);
        Map<String, Object> state = jdbc.queryForMap("select * from account_state where account = ?", ACCOUNT);
        assertThat(state)
                .containsEntry("net_liquidation", null)
                .containsEntry("total_cash_value", 30_000.0);
        assertThat(instantOf(state, "as_of")).isEqualTo(SECOND_RUN);
    }

    /**
     * IB sends a contract's whole year again on every connection (a split rewrites its past prices), so it replaces
     * what was stored; a contract it sent nothing for this time — here SPY — keeps its closes.
     */
    @Test
    void storesTheDailyClosesAndReplacesAContractsOnTheNextSync() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)), Map.of(
                APPLE_CON_ID, List.of(close(2026, 9, 18, 150.0), close(2026, 9, 21, 152.0)),
                Benchmark.SPY_CON_ID, List.of(close(2026, 9, 21, 600.0))));
        startNextRun();

        syncService.sync(snapshotOf(SECOND_RUN, apple(10, 150.0)), Map.of(
                APPLE_CON_ID, List.of(close(2026, 9, 21, 76.0), close(2026, 9, 22, 77.0))));   // after a 2-for-1 split

        assertThat(storedClosesOf(APPLE_CON_ID)).containsExactly(close(2026, 9, 21, 76.0), close(2026, 9, 22, 77.0));
        assertThat(storedClosesOf(Benchmark.SPY_CON_ID)).containsExactly(close(2026, 9, 21, 600.0));
    }

    /** A duplicate would break the table's UNIQUE constraint, and with it the whole sync. */
    @Test
    void storesADateIbSentTwiceOnceWithTheLaterValue() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)), Map.of(
                APPLE_CON_ID, List.of(close(2026, 9, 21, 150.0), close(2026, 9, 21, 151.0))));

        assertThat(storedClosesOf(APPLE_CON_ID)).containsExactly(close(2026, 9, 21, 151.0));
    }

    @Test
    void aSyncWithoutDailyClosesKeepsTheStoredOnes() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)), Map.of(
                APPLE_CON_ID, List.of(close(2026, 9, 21, 150.0))));
        startNextRun();

        syncService.sync(snapshotOf(SECOND_RUN, apple(10, 150.0)));

        assertThat(storedClosesOf(APPLE_CON_ID)).containsExactly(close(2026, 9, 21, 150.0));
    }

    // ── the scan for hot stocks ─────────────────────────────────────────────────────────────────

    /**
     * Two made-up stocks: HOTA found by both scans, with closes read in the same run; HOTB found by one, its closes
     * missing. Each candidate is stored with its last close — the price the measurement starts from.
     */
    @Test
    void storesAScanWithEachCandidatesLastClose() {
        Scan scan = new Scan(FIRST_RUN,
                List.of(new ScanRow(THREE_MONTH_SCAN, 4, HOTA_CON_ID, "HOTA"),
                        new ScanRow(SIX_MONTH_SCAN, 17, HOTA_CON_ID, "HOTA"),
                        new ScanRow(SIX_MONTH_SCAN, 2, HOTB_CON_ID, "HOTB")),
                Map.of(HOTA_CON_ID,
                        new StockContractDetails(HOTA_CON_ID, "NASDAQ", "HOTA INC", "Technology", ""),
                        HOTB_CON_ID,
                        new StockContractDetails(HOTB_CON_ID, "NYSE", "HOTB CORP", "Industrials", "Machinery")));

        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)),
                Map.of(HOTA_CON_ID, List.of(close(2026, 9, 18, 52.0), close(2026, 9, 17, 50.0))), scan);

        entityManager.flush();
        Map<String, Object> scanRow = jdbc.queryForMap(
                "select scanned_at, scan_codes ->> 0 as first_scan_code, jsonb_array_length(scan_codes) as scan_count "
                        + "from scan");
        assertThat(instantOf(scanRow, "scanned_at")).isEqualTo(FIRST_RUN);
        assertThat(scanRow).containsEntry("first_scan_code", THREE_MONTH_SCAN).containsEntry("scan_count", 2);
        assertThat(candidateRowOf(HOTA_CON_ID))
                .containsEntry("symbol", "HOTA")
                .containsEntry("company_name", "HOTA INC")
                .containsEntry("exchange", "NASDAQ")
                .containsEntry("industry", "Technology")
                .containsEntry("category", null)              // IB sent an empty text
                .containsEntry("rank_in_six_month_scan", "17")
                .containsEntry("close_at_scan", 52.0);
        assertThat(candidateRowOf(HOTA_CON_ID).get("close_date")).hasToString("2026-09-18");
        assertThat(candidateRowOf(HOTB_CON_ID))
                .containsEntry("close_at_scan", null)
                .containsEntry("close_date", null);
    }

    /** Every scan is kept for the measurement; the latest one's time decides whether the next run scans. */
    @Test
    void keepsEveryScanAndKnowsWhenTheLatestRan() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)), Map.of(), scanOfOneStockAt(FIRST_RUN));
        startNextRun();
        syncService.sync(snapshotOf(SECOND_RUN, apple(10, 150.0)), Map.of(), scanOfOneStockAt(SECOND_RUN));

        assertThat(countOf("select count(*) from scan")).isEqualTo(2);
        assertThat(syncService.lastScannedAt()).contains(SECOND_RUN);
    }

    @Test
    void aSyncWithoutAScanStoresNone() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));

        assertThat(countOf("select count(*) from scan")).isZero();
        assertThat(syncService.lastScannedAt()).isEmpty();
    }

    /**
     * Decision 14: a scan stores the targets in its stocks' Dow Jones headlines. The next scan read HOTA's headlines
     * again — they hold one target now — and HOTB's didn't come: HOTA's are replaced, HOTB's kept.
     */
    @Test
    void storesTheScansPriceTargetsAndReplacesAStocksOnTheNextScan() {
        syncService.syncScan(scanOfOneStockAt(FIRST_RUN).withAnalystPriceTargets(Map.of(
                HOTA_CON_ID, List.of(priceTarget("Wells Fargo", 130.0), priceTarget("Barclays", 120.0)),
                HOTB_CON_ID, List.of(priceTarget("Needham", 80.0)))), Map.of());
        startNextRun();
        syncService.syncScan(scanOfOneStockAt(SECOND_RUN).withAnalystPriceTargets(Map.of(
                HOTA_CON_ID, List.of(priceTarget("Wells Fargo", 140.0)))), Map.of());

        assertThat(countOf("select count(*) from analyst_price_target where con_id = " + HOTA_CON_ID)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select * from analyst_price_target where con_id = ?", HOTA_CON_ID))
                .containsEntry("firm", "Wells Fargo")
                .containsEntry("price_target", 140.0)
                .containsEntry("previous_price_target", 125.0)
                .containsEntry("headline", "HOTA Price Target Raised to $140.00/Share From $125.00 by Wells Fargo");
        assertThat(countOf("select count(*) from analyst_price_target where con_id = " + HOTB_CON_ID)).isEqualTo(1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Every start of the app is a new process, so Hibernate remembers nothing from the last sync. {@code clear()}
     * gives the test the same starting point: the next sync loads the rows from the database again.
     */
    private void startNextRun() {
        entityManager.flush();
        entityManager.clear();
    }

    /** The raw row of a holding. Pending changes are flushed first: Hibernate writes them lazily. */
    private Map<String, Object> rowOf(int conId) {
        entityManager.flush();
        return jdbc.queryForMap("select * from holding where account = ? and con_id = ?", ACCOUNT, conId);
    }

    private long holdingIdOf(int conId) {
        return ((Number) rowOf(conId).get("id")).longValue();
    }

    /** The account owner's, the investor V4__investors.sql created. */
    private void insertTrade(long holdingId) {
        jdbc.update("insert into trade (holding_id, investor_id, trade_date, side, quantity, price, commission) "
                        + "values (?, (select id from investor where is_account_owner), DATE '2024-03-14', 'BUY', 10, "
                        + "150, 5)",
                holdingId);
    }

    private int tradeCountOf(long holdingId) {
        return countOf("select count(*) from trade where holding_id = " + holdingId);
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }

    private Instant instantOf(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }

    private List<DailyClose> storedClosesOf(int conId) {
        entityManager.flush();
        return jdbc.query("select bar_date, close_price from daily_close where con_id = ? order by bar_date",
                (row, rowNumber) -> new DailyClose(row.getObject("bar_date", LocalDate.class),
                        row.getDouble("close_price")),
                conId);
    }

    private DailyClose close(int year, int month, int day, double price) {
        return new DailyClose(LocalDate.of(year, month, day), price);
    }

    /** A candidate's raw row, with where the 6-month scan placed it read out of the JSON column. */
    private Map<String, Object> candidateRowOf(int conId) {
        entityManager.flush();
        return jdbc.queryForMap("select *, rank_by_scan_code ->> ? as rank_in_six_month_scan from scan_candidate "
                + "where con_id = ?", SIX_MONTH_SCAN, conId);
    }

    /** Raised from 125, on the day of the first run. */
    private AnalystPriceTarget priceTarget(String firm, double target) {
        return new AnalystPriceTarget(LocalDateTime.of(2026, 9, 18, 14, 0), firm, target, 125.0,
                "HOTA Price Target Raised to $%.2f/Share From $125.00 by %s".formatted(target, firm));
    }

    private Scan scanOfOneStockAt(Instant scannedAt) {
        return new Scan(scannedAt, List.of(new ScanRow(THREE_MONTH_SCAN, 0, HOTA_CON_ID, "HOTA")),
                Map.of(HOTA_CON_ID,
                        new StockContractDetails(HOTA_CON_ID, "NASDAQ", "HOTA INC", "Technology", "Software")));
    }

    private PortfolioSnapshot snapshotOf(Instant asOf, Holding... holdings) {
        return new PortfolioSnapshot(ACCOUNT, asOf, 100_000.0, 25_000.0, List.of(holdings));
    }

    private Holding apple(double position, double averageCost) {
        return holding("AAPL", APPLE_CON_ID, position, averageCost);
    }

    private Holding microsoft(double position, double averageCost) {
        return holding("MSFT", MICROSOFT_CON_ID, position, averageCost);
    }

    private Holding holding(String symbol, int conId, double position, double averageCost) {
        double marketPrice = 200.0;
        return new Holding(symbol, conId, "STK", "USD", position, averageCost, marketPrice,
                position * marketPrice, position * (marketPrice - averageCost), 0.0, ACCOUNT);
    }
}
