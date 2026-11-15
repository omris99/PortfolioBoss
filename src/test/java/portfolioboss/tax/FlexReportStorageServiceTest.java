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
                List.of(saleWithCommission("1000000001", "-5"), saleWithCommission("1000000001", "-2")), List.of());

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
        return new FlexReport(period, List.of(trades), List.of(marketDataFee()));
    }

    private FlexReportTrade saleWithCommission(String ibTradeId, String ibCommission) {
        return new FlexReportTrade(ibTradeId, "2000000001", 1111, "ABCD", "STK", "USD", LocalDate.of(2026, 11, 12),
                LocalDateTime.of(2026, 11, 12, 10, 30, 0), LocalDate.of(2026, 11, 13), "SELL", new BigDecimal("-10"),
                new BigDecimal("100"), new BigDecimal(ibCommission), "USD", new BigDecimal("995"), "ExchTrade");
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
