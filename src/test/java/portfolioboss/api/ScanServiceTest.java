package portfolioboss.api;

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
import portfolioboss.api.response.ScanRunResponse;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanReading;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.ib.TwsReader;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * The Scan again button against the real PostgreSQL test database, with {@link TwsReader} replaced by a stand-in
 * ({@code @MockitoBean}): no test connects to TWS. Two made-up stocks are found; one has its closes read with them.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ScanService.class, PortfolioSyncService.class})
class ScanServiceTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-10-26T21:05:00Z");
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final int HOTA_CON_ID = 9001;
    private static final int HOTB_CON_ID = 9002;

    @Autowired
    private ScanService scanService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private TwsReader twsReader;

    @Test
    void storesTheScanWithItsClosesAndAnswersWhatItFound() throws InterruptedException {
        given(twsReader.readScan()).willReturn(new ScanReading(scanOfTwoStocks(), Map.of(
                HOTA_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 26), 84.2)),
                Benchmark.SPY_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 26), 690.1)))));
        int holdingCountBefore = countOf("select count(*) from holding");

        ScanRunResponse answer = scanService.scanAgain();

        assertThat(answer).isEqualTo(new ScanRunResponse(SCANNED_AT, 2));
        assertThat(countOf("select count(*) from scan")).isEqualTo(1);
        assertThat(countOf("select count(*) from scan_candidate")).isEqualTo(2);
        assertThat(countOf("select count(*) from daily_close where con_id in (" + HOTA_CON_ID + ", "
                + Benchmark.SPY_CON_ID + ")")).isEqualTo(2);
        assertThat(countOf("select count(*) from holding")).isEqualTo(holdingCountBefore);   // the portfolio untouched
    }

    @Test
    void answers409WhileAnotherReadingRunsAndStoresNothing() throws InterruptedException {
        given(twsReader.readScan()).willThrow(new TwsBusyException("A scan is already running."));

        assertThatThrownBy(() -> scanService.scanAgain())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getReason()).isEqualTo("A scan is already running.");
                });
        assertThat(countOf("select count(*) from scan")).isZero();
    }

    /** The user's message goes to the browser; the console line with the detail stays in the console. */
    @Test
    void answers503WhenTwsCantBeReadAndStoresNothing() throws InterruptedException {
        given(twsReader.readScan()).willThrow(new TwsUnavailableException(
                "TWS is not reachable: open TWS and log in, then scan again.",
                "Timed out after 15s waiting for portfolio data. Is TWS running and logged in on port 7496?"));

        assertThatThrownBy(() -> scanService.scanAgain())
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason())
                            .isEqualTo("TWS is not reachable: open TWS and log in, then scan again.");
                });
        assertThat(countOf("select count(*) from scan")).isZero();
    }

    private Scan scanOfTwoStocks() {
        return new Scan(SCANNED_AT,
                List.of(new ScanRow(THREE_MONTH_SCAN, 0, HOTA_CON_ID, "HOTA"),
                        new ScanRow(THREE_MONTH_SCAN, 1, HOTB_CON_ID, "HOTB")),
                Map.of(HOTA_CON_ID,
                        new StockContractDetails(HOTA_CON_ID, "NASDAQ", "HOTA INC", "Technology", "Software"),
                        HOTB_CON_ID,
                        new StockContractDetails(HOTB_CON_ID, "NYSE", "HOTB CORP", "Industrials", "Machinery")));
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
