package portfolioboss.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.ManualClosedPositionRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.ClosedPositionSource;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.db.TradeSide;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the closed-position endpoints store, against the PostgreSQL test database ({@code portfolioboss_test}), read
 * back the way the UI reads it: through {@link PortfolioReadService}. Same setup as {@code HoldingWriteServiceTest};
 * the HTTP side is in {@code ClosedPositionWriteControllerTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, PortfolioReadService.class, HoldingWriteService.class,
        ClosedPositionWriteService.class})
class ClosedPositionWriteServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant SYNCED_AT = Instant.parse("2026-09-21T08:00:00Z");
    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private PortfolioReadService readService;

    @Autowired
    private HoldingWriteService holdingWriteService;

    @Autowired
    private ClosedPositionWriteService writeService;

    @Autowired
    private TestEntityManager entityManager;

    /** Nothing is served before a sync, the rows entered by hand included. */
    @BeforeEach
    void syncApple() {
        Holding apple = new Holding("AAPL", 265598, "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        syncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 100_000.0, 25_000.0, List.of(apple)));
    }

    @Test
    void anAddedRowIsServedWithItsFiguresAndNoHolding() {
        ClosedPositionResponse addedRow = writeService.addManualClosedPosition(microsoftRoundTrip("5", "2"));

        assertThat(addedRow.manualClosedPositionId()).isPositive();
        assertThat(readClosedPositions()).singleElement().satisfies(storedRow -> {
            assertThat(storedRow.source()).isEqualTo(ClosedPositionSource.MANUAL);
            assertThat(storedRow.manualClosedPositionId()).isEqualTo(addedRow.manualClosedPositionId());
            assertThat(storedRow.holdingId()).isNull();
            assertThat(storedRow.symbol()).isEqualTo("MSFT");
            assertThat(storedRow.currency()).isEqualTo("USD");
            assertThat(storedRow.sector()).isEqualTo("Technology");
            assertThat(storedRow.note()).isEqualTo("Sold before PortfolioBoss");
            assertThat(storedRow.openDate()).isEqualTo(LocalDate.of(2022, 1, 10));
            assertThat(storedRow.closeDate()).isEqualTo(LocalDate.of(2023, 5, 1));
            assertThat(storedRow.holdingDays()).isEqualTo(476);
            assertThat(storedRow.quantity()).isEqualByComparingTo("5");
            assertThat(storedRow.averageBuyPrice()).isEqualByComparingTo("300");
            assertThat(storedRow.averageSellPrice()).isEqualByComparingTo("310");
            assertThat(storedRow.commissions()).isEqualByComparingTo("2");
            assertThat(storedRow.realizedPnl()).isEqualByComparingTo("48");          // 1,550 − 1,500 − 2
            assertThat(storedRow.realizedPnlPercent()).isEqualByComparingTo("3.2");
            assertThat(storedRow.warning()).isNull();
        });
    }

    /** " msft " and " usd " as typed: stored the way IB writes them, so the totals per currency add up. */
    @Test
    void theSymbolAndCurrencyAreStoredInCapitalsWithoutSpaces() {
        writeService.addManualClosedPosition(new ManualClosedPositionRequest(" msft ", " usd ", "   ",
                new BigDecimal("5"), LocalDate.of(2022, 1, 10), new BigDecimal("300"), LocalDate.of(2023, 5, 1),
                new BigDecimal("310"), null, "   "));

        assertThat(readClosedPositions()).singleElement().satisfies(storedRow -> {
            assertThat(storedRow.symbol()).isEqualTo("MSFT");
            assertThat(storedRow.currency()).isEqualTo("USD");
            assertThat(storedRow.sector()).isNull();
            assertThat(storedRow.note()).isNull();
        });
    }

    /** One order to buy and one to sell: 2 × $5 for 10 shares, 2 × $6 for 600. */
    @Test
    void withoutACommissionTheBuyAndTheSellAreEachChargedTheDefault() {
        writeService.addManualClosedPosition(microsoftRoundTrip("10", null));
        writeService.addManualClosedPosition(microsoftRoundTrip("600", null));

        assertThat(readClosedPositions())
                .extracting(ClosedPositionResponse::commissions)
                .satisfiesExactly(
                        commissions -> assertThat(commissions).isEqualByComparingTo("10"),
                        commissions -> assertThat(commissions).isEqualByComparingTo("12"));
    }

    @Test
    void changingARowReplacesEveryField() {
        long rowId = writeService.addManualClosedPosition(microsoftRoundTrip("5", "2")).manualClosedPositionId();
        forgetWhatHibernateLoaded();

        writeService.changeManualClosedPosition(rowId, new ManualClosedPositionRequest("NVDA", "USD", null,
                new BigDecimal("20"), LocalDate.of(2021, 3, 1), new BigDecimal("100"), LocalDate.of(2021, 9, 1),
                new BigDecimal("90"), new BigDecimal("0"), null));

        assertThat(readClosedPositions()).singleElement().satisfies(storedRow -> {
            assertThat(storedRow.manualClosedPositionId()).isEqualTo(rowId);
            assertThat(storedRow.symbol()).isEqualTo("NVDA");
            assertThat(storedRow.sector()).isNull();
            assertThat(storedRow.note()).isNull();
            assertThat(storedRow.quantity()).isEqualByComparingTo("20");
            assertThat(storedRow.openDate()).isEqualTo(LocalDate.of(2021, 3, 1));
            assertThat(storedRow.closeDate()).isEqualTo(LocalDate.of(2021, 9, 1));
            assertThat(storedRow.commissions()).isEqualByComparingTo("0");
            assertThat(storedRow.realizedPnl()).isEqualByComparingTo("-200");
        });
    }

    @Test
    void changingARowThatDoesNotExistIs404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.changeManualClosedPosition(MISSING_ID, microsoftRoundTrip("5", "2")));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("No manual closed position with id " + MISSING_ID);
    }

    @Test
    void deletingARowTwiceIs404TheSecondTime() {
        long rowId = writeService.addManualClosedPosition(microsoftRoundTrip("5", "2")).manualClosedPositionId();
        forgetWhatHibernateLoaded();

        writeService.deleteManualClosedPosition(rowId);
        forgetWhatHibernateLoaded();

        assertThat(readClosedPositions()).isEmpty();
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.deleteManualClosedPosition(rowId));
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRowsEnteredByHandAreServedAfterTheOnesDerivedFromTrades() {
        writeService.addManualClosedPosition(microsoftRoundTrip("5", "2"));
        long appleHoldingId = readService.currentPortfolio().orElseThrow().holdings().get(0).id();
        holdingWriteService.addTrade(appleHoldingId, appleTrade(LocalDate.of(2024, 1, 1), TradeSide.BUY));
        holdingWriteService.addTrade(appleHoldingId, appleTrade(LocalDate.of(2024, 6, 1), TradeSide.SELL));

        assertThat(readClosedPositions())
                .extracting(ClosedPositionResponse::source, ClosedPositionResponse::symbol)
                .containsExactly(tuple(ClosedPositionSource.TRADES, "AAPL"), tuple(ClosedPositionSource.MANUAL, "MSFT"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Every request is its own transaction in the app; this makes the next read come from the database. */
    private void forgetWhatHibernateLoaded() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<ClosedPositionResponse> readClosedPositions() {
        forgetWhatHibernateLoaded();
        return readService.currentPortfolio().orElseThrow().closedPositions();
    }

    /** Bought at 300 on 2022-01-10, sold at 310 on 2023-05-01; {@code commission} may be {@code null}. */
    private ManualClosedPositionRequest microsoftRoundTrip(String quantity, String commission) {
        return new ManualClosedPositionRequest("MSFT", "USD", "  Technology ", new BigDecimal(quantity),
                LocalDate.of(2022, 1, 10), new BigDecimal("300"), LocalDate.of(2023, 5, 1), new BigDecimal("310"),
                commission == null ? null : new BigDecimal(commission), " Sold before PortfolioBoss ");
    }

    private TradeRequest appleTrade(LocalDate tradeDate, TradeSide side) {
        return new TradeRequest(tradeDate, side, new BigDecimal("10"), new BigDecimal("150"), null, null);
    }
}
