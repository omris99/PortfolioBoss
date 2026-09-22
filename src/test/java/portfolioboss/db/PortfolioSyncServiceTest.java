package portfolioboss.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.sql.Timestamp;
import java.time.Instant;
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
    private static final int APPLE = 265598;
    private static final int MICROSOFT = 272093;
    private static final Instant FIRST_RUN = Instant.parse("2026-09-21T08:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-09-22T08:00:00Z");
    private static final Instant THIRD_RUN = Instant.parse("2026-09-23T08:00:00Z");

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
        assertThat(rowOf(APPLE))
                .containsEntry("symbol", "AAPL")
                .containsEntry("status", "OPEN")
                .containsEntry("position", 10.0)
                .containsEntry("average_cost", 150.0)
                .containsEntry("sector", null);
    }

    @Test
    void refreshesIbFiguresButNeverTheSectorOrTheTrades() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));
        long appleId = holdingIdOf(APPLE);
        jdbc.update("update holding set sector = 'Technology' where id = ?", appleId);
        insertTrade(appleId);
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(SECOND_RUN, apple(12, 160.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(rowOf(APPLE))
                .containsEntry("position", 12.0)
                .containsEntry("average_cost", 160.0)
                .containsEntry("sector", "Technology");
        assertThat(tradeCountOf(appleId)).isEqualTo(1);
    }

    @Test
    void marksAHoldingThatLeftTwsAsClosedAndKeepsItsRowAndTrades() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0), microsoft(5, 300.0)));
        long appleId = holdingIdOf(APPLE);
        insertTrade(appleId);
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(SECOND_RUN, microsoft(5, 300.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 1));
        Map<String, Object> row = rowOf(APPLE);
        assertThat(row)
                .containsEntry("status", "CLOSED")
                .containsEntry("position", 0.0)
                .containsEntry("market_value", 0.0)
                .containsEntry("unrealized_pnl", 0.0)
                .containsEntry("average_cost", 150.0);
        assertThat(instantOf(row, "closed_detected_at")).isEqualTo(SECOND_RUN);
        assertThat(instantOf(row, "last_synced_at")).isEqualTo(FIRST_RUN);
        assertThat(tradeCountOf(appleId)).isEqualTo(1);
    }

    @Test
    void reopensAClosedHoldingThatComesBack() {
        syncService.sync(snapshotOf(FIRST_RUN, apple(10, 150.0)));
        startNextRun();
        syncService.sync(snapshotOf(SECOND_RUN));   // Apple is gone from the portfolio
        startNextRun();

        SyncResult result = syncService.sync(snapshotOf(THIRD_RUN, apple(8, 155.0)));

        assertThat(result).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(rowOf(APPLE))
                .containsEntry("status", "OPEN")
                .containsEntry("position", 8.0)
                .containsEntry("closed_detected_at", null);
    }

    @Test
    void storesFiguresIbDidNotReportAsNull() {
        Holding withoutCostData = new Holding(
                "MSFT", MICROSOFT, "STK", "USD", 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, ACCOUNT);

        syncService.sync(snapshotOf(FIRST_RUN, withoutCostData));

        assertThat(rowOf(MICROSOFT))
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

    private void insertTrade(long holdingId) {
        jdbc.update("insert into trade (holding_id, trade_date, side, quantity) values (?, DATE '2024-03-14', 'BUY', 10)",
                holdingId);
    }

    private int tradeCountOf(long holdingId) {
        return countOf("select count(*) from trade where holding_id = " + holdingId);
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }

    private static Instant instantOf(Map<String, Object> row, String column) {
        return ((Timestamp) row.get(column)).toInstant();
    }

    private static PortfolioSnapshot snapshotOf(Instant asOf, Holding... holdings) {
        return new PortfolioSnapshot(ACCOUNT, asOf, 100_000.0, 25_000.0, List.of(holdings));
    }

    private static Holding apple(double position, double averageCost) {
        return holding("AAPL", APPLE, position, averageCost);
    }

    private static Holding microsoft(double position, double averageCost) {
        return holding("MSFT", MICROSOFT, position, averageCost);
    }

    private static Holding holding(String symbol, int conId, double position, double averageCost) {
        double marketPrice = 200.0;
        return new Holding(symbol, conId, "STK", "USD", position, averageCost, marketPrice,
                position * marketPrice, position * (marketPrice - averageCost), 0.0, ACCOUNT);
    }
}
