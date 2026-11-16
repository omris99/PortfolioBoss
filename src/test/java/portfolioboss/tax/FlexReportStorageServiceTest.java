package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stored Flex reports against the real PostgreSQL test database ({@code portfolioboss_test}), each test rolled back.
 * Made-up rows: a sale of ABCD and a market data fee.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(FlexReportStorageService.class)
class FlexReportStorageServiceTest {

    private static final Instant DOWNLOADED_AT = Instant.parse("2026-11-15T08:00:00Z");
    private static final FlexReportPeriod NOVEMBER_PERIOD =
            new FlexReportPeriod(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13));

    @Autowired
    private FlexReportStorageService flexReportStorageService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void everyRowOfTheReportIsStoredAndItsDaysLogged() {
        flexReportStorageService.storeReport(reportOf(NOVEMBER_PERIOD, saleWithCommission("1000000001", "-5")),
                DOWNLOADED_AT);

        assertThat(countOf("select count(*) from flex_trade where ib_trade_id = '1000000001'")).isEqualTo(1);
        assertThat(countOf("select count(*) from flex_cash_transaction where ib_transaction_id = '3000000001'"))
                .isEqualTo(1);
        assertThat(countOf("select count(*) from flex_report where from_date = '2026-11-01' and to_date = '2026-11-13'"
                + " and trade_count = 1 and cash_transaction_count = 1")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select fifo_pnl_realized from flex_trade where ib_trade_id = '1000000001'",
                BigDecimal.class)).isEqualByComparingTo("245.5");
    }

    /** Each row of an action is a row of its own; storing the same days again replaces them by IB's id. */
    @Test
    void theRowsOfACorporateActionAreStoredAndCountedOnce() {
        FlexReport report = new FlexReport(NOVEMBER_PERIOD, List.of(), List.of(), reverseSplitRows());

        flexReportStorageService.storeReport(report, DOWNLOADED_AT);
        flexReportStorageService.storeReport(report, DOWNLOADED_AT.plusSeconds(86_400));

        assertThat(countOf("select count(*) from flex_corporate_action where ib_action_id = '6000000001'"))
                .isEqualTo(2);
        assertThat(countOf("select count(*) from flex_report where corporate_action_count = 2")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select quantity from flex_corporate_action where con_id = 1111",
                BigDecimal.class)).isEqualByComparingTo("-500");
    }

    /** Each download is logged, but a row already stored is not stored twice. */
    @Test
    void storingTheSameDaysAgainAddsNoRow() {
        FlexReport report = reportOf(NOVEMBER_PERIOD, saleWithCommission("1000000001", "-5"));

        flexReportStorageService.storeReport(report, DOWNLOADED_AT);
        flexReportStorageService.storeReport(report, DOWNLOADED_AT.plusSeconds(86_400));

        assertThat(countOf("select count(*) from flex_trade")).isEqualTo(1);
        assertThat(countOf("select count(*) from flex_cash_transaction")).isEqualTo(1);
        assertThat(countOf("select count(*) from flex_report")).isEqualTo(2);
    }

    @Test
    void aRowIbChangedReplacesTheStoredOne() {
        flexReportStorageService.storeReport(reportOf(NOVEMBER_PERIOD, saleWithCommission("1000000001", "-5")),
                DOWNLOADED_AT);
        flexReportStorageService.storeReport(reportOf(NOVEMBER_PERIOD, saleWithCommission("1000000001", "-1.25")),
                DOWNLOADED_AT.plusSeconds(86_400));

        entityManager.flush();
        BigDecimal storedCommission = jdbc.queryForObject(
                "select ib_commission from flex_trade where ib_trade_id = '1000000001'", BigDecimal.class);
        assertThat(storedCommission).isEqualByComparingTo("-1.25");
    }

    /** Two new rows with one IB id would break the table's UNIQUE, and with it the whole report. */
    @Test
    void aRowIbSentTwiceInOneReportIsStoredOnceAsItCameTheSecondTime() {
        FlexReport report = new FlexReport(NOVEMBER_PERIOD,
                List.of(saleWithCommission("1000000001", "-5"), saleWithCommission("1000000001", "-2")), List.of(),
                List.of());

        flexReportStorageService.storeReport(report, DOWNLOADED_AT);

        entityManager.flush();
        assertThat(countOf("select count(*) from flex_trade")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select ib_commission from flex_trade", BigDecimal.class))
                .isEqualByComparingTo("-2");
    }

    @Test
    void theLastStoredDateIsTheLatestReportsLastDay() {
        assertThat(flexReportStorageService.lastStoredDate()).isEmpty();

        flexReportStorageService.storeReport(reportOf(NOVEMBER_PERIOD), DOWNLOADED_AT);
        flexReportStorageService.storeReport(
                reportOf(new FlexReportPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))), DOWNLOADED_AT);

        assertThat(flexReportStorageService.lastStoredDate()).contains(LocalDate.of(2026, 11, 13));
    }

    private FlexReport reportOf(FlexReportPeriod period, FlexReportTrade... trades) {
        return new FlexReport(period, List.of(trades), List.of(marketDataFee()), List.of());
    }

    private FlexReportTrade saleWithCommission(String ibTradeId, String ibCommission) {
        return new FlexReportTrade(ibTradeId, "2000000001", 1111, "ABCD", "STK", "USD", LocalDate.of(2026, 11, 12),
                LocalDateTime.of(2026, 11, 12, 10, 30, 0), LocalDate.of(2026, 11, 13), "SELL", new BigDecimal("-10"),
                new BigDecimal("100"), new BigDecimal(ibCommission), "USD", new BigDecimal("995"), "ExchTrade",
                new BigDecimal("245.5"));
    }

    /** Two rows of one reverse split of 1 for 5: the old contract's 500 shares leave, the new contract's 100 come in. */
    private List<FlexReportCorporateAction> reverseSplitRows() {
        LocalDateTime actionTime = LocalDateTime.of(2026, 11, 10, 20, 25, 0);
        return List.of(
                new FlexReportCorporateAction("5000000001", "6000000001", "RS", 2222, "WXYZ", "STK", actionTime,
                        new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, "WXYZ SPLIT 1 FOR 5"),
                new FlexReportCorporateAction("5000000002", "6000000001", "RS", 1111, "WXYZ.OLD", "STK", actionTime,
                        new BigDecimal("-500"), BigDecimal.ZERO, BigDecimal.ZERO, "WXYZ SPLIT 1 FOR 5"));
    }

    private FlexReportCashTransaction marketDataFee() {
        return new FlexReportCashTransaction("3000000001", "Other Fees", null, null, "USD", new BigDecimal("-10"),
                LocalDateTime.of(2026, 11, 4, 17, 0, 0), LocalDate.of(2026, 11, 4), "X******00:MARKETDATA FOR NOV 2026");
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
