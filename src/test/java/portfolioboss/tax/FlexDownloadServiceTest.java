package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.db.TradeEntity;
import portfolioboss.exceptions.FlexReportUnavailableException;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A sync's download of the Flex reports not stored yet, against the real PostgreSQL test database, with
 * {@link FlexClient} replaced by a stand-in ({@code @MockitoBean}): no test calls IB. The stand-in answers each request
 * with an empty report of the days asked for. The sync is on Sunday 15 November 2026, so the reports reach Friday the
 * 13th.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FlexDownloadService.class, FlexReportStorageService.class, PortfolioSyncService.class})
class FlexDownloadServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate LAST_WEEKDAY = LocalDate.of(2026, 11, 13);
    private static final String ACCOUNT = "U7654321";

    @Autowired
    private FlexDownloadService flexDownloadService;

    @Autowired
    private FlexReportStorageService flexReportStorageService;

    @Autowired
    private PortfolioSyncService portfolioSyncService;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private InvestorRepository investorRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FlexClient flexClient;

    @Test
    void withoutTheKeysNothingIsAskedOfIb() throws InterruptedException {
        given(flexClient.hasKeys()).willReturn(false);

        flexDownloadService.downloadWhatIsMissing(TODAY);

        verify(flexClient, never()).downloadReport(any());
    }

    @Test
    void theFirstDownloadReadsTheYearsSinceTheEarliestTypedTradeOldestFirst() throws InterruptedException {
        typeInABuyOn(LocalDate.of(2025, 3, 5));
        answerEveryRequestWithAnEmptyReport();

        flexDownloadService.downloadWhatIsMissing(TODAY);

        InOrder requestsInOrder = inOrder(flexClient);
        requestsInOrder.verify(flexClient)
                .downloadReport(new FlexReportPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));
        requestsInOrder.verify(flexClient)
                .downloadReport(new FlexReportPeriod(LocalDate.of(2026, 1, 1), LAST_WEEKDAY));
        assertThat(countOf("select count(*) from flex_report")).isEqualTo(2);
    }

    /** The periods are in order, so the first stays stored and the next sync goes on from it. */
    @Test
    void aFailedPeriodStopsTheDownloadWithoutFailingTheSync() throws InterruptedException {
        typeInABuyOn(LocalDate.of(2024, 3, 5));
        answerEveryRequestWithAnEmptyReport();
        FlexReportPeriod secondPeriod = new FlexReportPeriod(LocalDate.of(2024, 12, 31), LocalDate.of(2025, 12, 30));
        FlexReportPeriod thirdPeriod = new FlexReportPeriod(LocalDate.of(2025, 12, 31), LAST_WEEKDAY);
        given(flexClient.downloadReport(secondPeriod)).willThrow(
                new FlexReportUnavailableException("IB refused the request: 1003 Statement is not available."));

        flexDownloadService.downloadWhatIsMissing(TODAY);

        verify(flexClient, never()).downloadReport(thirdPeriod);
        assertThat(flexReportStorageService.lastStoredDate()).contains(LocalDate.of(2024, 12, 30));
    }

    @Test
    void anUpdateAsksOnlyForTheDaysAfterTheStoredOnesWithAWeekBack() throws InterruptedException {
        flexReportStorageService.storeReport(emptyReportOf(
                new FlexReportPeriod(LocalDate.of(2025, 11, 11), LocalDate.of(2026, 11, 10))), Instant.now());
        answerEveryRequestWithAnEmptyReport();

        flexDownloadService.downloadWhatIsMissing(TODAY);

        verify(flexClient).downloadReport(new FlexReportPeriod(LocalDate.of(2026, 11, 3), LAST_WEEKDAY));
    }

    @Test
    void nothingIsAskedOnceTheStoredReportsReachTheLastWeekday() throws InterruptedException {
        flexReportStorageService.storeReport(emptyReportOf(
                new FlexReportPeriod(LocalDate.of(2025, 11, 17), LAST_WEEKDAY)), Instant.now());
        given(flexClient.hasKeys()).willReturn(true);

        flexDownloadService.downloadWhatIsMissing(TODAY);

        verify(flexClient, never()).downloadReport(any());
    }

    private void answerEveryRequestWithAnEmptyReport() throws InterruptedException {
        given(flexClient.hasKeys()).willReturn(true);
        given(flexClient.downloadReport(any())).willAnswer(request -> emptyReportOf(request.getArgument(0)));
    }

    private FlexReport emptyReportOf(FlexReportPeriod period) {
        return new FlexReport(period, List.of(), List.of());
    }

    /** A holding synced from IB, and a buy of it typed in by hand on that day. */
    private void typeInABuyOn(LocalDate tradeDate) {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, Instant.parse("2026-11-14T21:40:00Z"), 50_000.0,
                10_000.0, List.of(new Holding("ABCD", 8101, "STK", "USD", 10, 100.0, 110.0, 1_100.0, 100.0, 0.0,
                ACCOUNT))));
        HoldingEntity holding = holdingRepository.findByAccountAndConId(ACCOUNT, 8101).orElseThrow();
        entityManager.persist(new TradeEntity(holding, investorRepository.accountOwner(), tradeDate, TradeSide.BUY,
                BigDecimal.TEN, new BigDecimal("100"), null, new BigDecimal("5")));
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
