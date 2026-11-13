package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import portfolioboss.api.response.SyncRunResponse;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.ib.TwsReader;
import portfolioboss.ib.TwsReading;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;
import portfolioboss.stocks.FinnhubClient;
import portfolioboss.stocks.Stock;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The sync of the startup and of the Sync now button against the real PostgreSQL test database, with {@link TwsReader}
 * and {@link FinnhubClient} replaced by stand-ins ({@code @MockitoBean}): no test connects to TWS or asks Finnhub. TWS
 * reports two made-up holdings, one in dollars and one in Hong Kong dollars.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SyncService.class, PortfolioSyncService.class})
@RecordApplicationEvents
class SyncServiceTest {

    private static final String ACCOUNT = "U7654321";
    private static final Instant SYNCED_AT = Instant.parse("2026-11-11T21:40:00Z");
    private static final int DOLLAR_HOLDING_CON_ID = 8101;
    private static final int HONG_KONG_HOLDING_CON_ID = 8102;
    /** On the Hong Kong exchange IB's symbol is a number, which in Finnhub may be another company's. */
    private static final String HONG_KONG_SYMBOL = "700";
    private static final Stock FINNHUB_DATA_OF_DOLLAR_HOLDING = new Stock("ABCD",
            new EarningsDate(LocalDate.of(2026, 11, 20), "amc"), new CompanyFacts(12.5, 20.1, 0.4, 31.0));

    @Autowired
    private SyncService syncService;

    @Autowired
    private PortfolioSyncService portfolioSyncService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private TwsReader twsReader;

    @MockitoBean
    private FinnhubClient finnhubClient;

    /** The events published during each test: the alerts are checked on {@link PortfolioSyncedEvent}. */
    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void syncNowStoresTheHoldingsWithFinnhubsAnswerAndSaysWhen() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());
        given(finnhubClient.hasApiKey()).willReturn(true);
        given(finnhubClient.stockOrNullOnFailure(eq("ABCD"), any())).willReturn(FINNHUB_DATA_OF_DOLLAR_HOLDING);

        SyncRunResponse answer = syncService.syncNow();

        assertThat(answer).isEqualTo(new SyncRunResponse(SYNCED_AT, 2));
        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isEqualTo(2);
        assertThat(countOf("select count(*) from stock where con_id = " + DOLLAR_HOLDING_CON_ID)).isEqualTo(1);
    }

    /** MY_HOLDINGS_TODO.md, decision 10: only a holding in dollars has a symbol Finnhub knows by the same name. */
    @Test
    void asksFinnhubOnlyAboutTheHoldingsInDollars() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());
        given(finnhubClient.hasApiKey()).willReturn(true);

        syncService.syncNow();

        verify(finnhubClient).stockOrNullOnFailure(eq("ABCD"), any());
        verify(finnhubClient, never()).stockOrNullOnFailure(eq(HONG_KONG_SYMBOL), any());
        assertThat(countOf("select count(*) from stock where con_id = " + HONG_KONG_HOLDING_CON_ID)).isZero();
    }

    @Test
    void withoutFinnhubsKeyStoresTheHoldingsAndAsksFinnhubNothing() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());
        given(finnhubClient.hasApiKey()).willReturn(false);

        syncService.syncNow();

        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isEqualTo(2);
        verify(finnhubClient, never()).stockOrNullOnFailure(anyString(), any());
    }

    @Test
    void asksForTheScanWhenNoneWasEverStored() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());

        syncService.syncNow();

        verify(twsReader).readPortfolioUnlessBusy(true);
    }

    /** A scan stored just now came after the latest US market close: the next one is due after the next close. */
    @Test
    void doesNotAskForTheScanWhenOneCameAfterTheLatestMarketClose() throws InterruptedException {
        portfolioSyncService.syncScan(scanOfOneStockAt(Instant.now()), Map.of());
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());

        syncService.syncNow();

        verify(twsReader).readPortfolioUnlessBusy(false);
    }

    /** Once stored, the sync says so — the startup's and Sync now's alike —; whoever checks the alerts listens. */
    @Test
    void aStoredSyncIsAnnouncedOnce() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willReturn(readingOfTwoHoldings());
        given(twsReader.readPortfolio(anyBoolean())).willReturn(readingOfTwoHoldings());

        syncService.syncNow();
        syncService.syncAtStartup();

        assertThat(applicationEvents.stream(PortfolioSyncedEvent.class)).hasSize(2);
    }

    @Test
    void aSyncThatReadNothingIsNotAnnounced() throws InterruptedException {
        given(twsReader.readPortfolio(anyBoolean())).willReturn(null);
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willThrow(new TwsUnavailableException(
                "TWS is not reachable: open TWS and log in, then try again.", "Connection refused"));

        syncService.syncAtStartup();
        assertThatThrownBy(() -> syncService.syncNow()).isInstanceOf(TwsUnavailableException.class);

        assertThat(applicationEvents.stream(PortfolioSyncedEvent.class)).isEmpty();
    }

    /** The 409 comes from {@code ApiErrorHandler} ({@code SyncControllerTest}). */
    @Test
    void syncNowStoresNothingWhileAnotherReadingRuns() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willThrow(new TwsBusyException(
                "TWS is already being read — by a sync, a scan or the startup: try again in a moment."));

        assertThatThrownBy(() -> syncService.syncNow()).isInstanceOf(TwsBusyException.class);
        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isZero();
        verify(finnhubClient, never()).stockOrNullOnFailure(anyString(), any());
    }

    /** The 503 comes from {@code ApiErrorHandler} ({@code SyncControllerTest}). */
    @Test
    void syncNowStoresNothingWhenTwsCantBeRead() throws InterruptedException {
        given(twsReader.readPortfolioUnlessBusy(anyBoolean())).willThrow(new TwsUnavailableException(
                "TWS is not reachable: open TWS and log in, then try again.",
                "Timed out after 15s waiting for portfolio data. Is TWS running and logged in on port 7496?"));

        assertThatThrownBy(() -> syncService.syncNow()).isInstanceOf(TwsUnavailableException.class);
        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isZero();
        verify(finnhubClient, never()).stockOrNullOnFailure(anyString(), any());
    }

    @Test
    void syncAtStartupStoresWhatTwsSent() throws InterruptedException {
        given(twsReader.readPortfolio(anyBoolean())).willReturn(readingOfTwoHoldings());

        syncService.syncAtStartup();

        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isEqualTo(2);
    }

    /** TWS off is not an error at startup: the app comes up and serves the last sync. */
    @Test
    void syncAtStartupStoresNothingAndGoesOnWhenTwsCantBeRead() throws InterruptedException {
        given(twsReader.readPortfolio(anyBoolean())).willReturn(null);

        syncService.syncAtStartup();

        assertThat(countOf("select count(*) from holding where account = '" + ACCOUNT + "'")).isZero();
        verify(finnhubClient, never()).stockOrNullOnFailure(anyString(), any());
    }

    private TwsReading readingOfTwoHoldings() {
        PortfolioSnapshot snapshot = new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 50_000.0, 10_000.0, List.of(
                holdingOf("ABCD", DOLLAR_HOLDING_CON_ID, "USD"),
                holdingOf(HONG_KONG_SYMBOL, HONG_KONG_HOLDING_CON_ID, "HKD")));
        return new TwsReading(snapshot, Map.of(), null);
    }

    private Holding holdingOf(String symbol, int conId, String currency) {
        return new Holding(symbol, conId, "STK", currency, 10, 100.0, 110.0, 1_100.0, 100.0, 0.0, ACCOUNT);
    }

    private Scan scanOfOneStockAt(Instant scannedAt) {
        int scannedStockConId = 9001;
        return new Scan(scannedAt,
                List.of(new ScanRow("SCAN_histPerfRatio3m_DESC", 0, scannedStockConId, "HOTA")),
                Map.of(scannedStockConId,
                        new StockContractDetails(scannedStockConId, "NASDAQ", "HOTA INC", "Technology", "Software")));
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
