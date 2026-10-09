package portfolioboss.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.request.ManualPositionRequest;
import portfolioboss.api.request.NewManualPositionRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.ClosedPositionSource;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.calculation.Holding;
import portfolioboss.calculation.PortfolioSnapshot;
import portfolioboss.calculation.TradeSide;
import portfolioboss.db.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the manual-position endpoints store, against the PostgreSQL test database ({@code portfolioboss_test}), read
 * back the way the UI reads it: through {@link PortfolioReadService}. Same setup as {@code HoldingWriteServiceTest};
 * the HTTP side is in {@code ManualPositionWriteControllerTest}. Correcting and deleting their trades goes through
 * {@link HoldingWriteService}, so the rule about a manual position's last sell is tested here too.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, PortfolioReadService.class, HoldingWriteService.class,
        ManualPositionWriteService.class, InvestorWriteService.class})
class ManualPositionWriteServiceTest {

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
    private ManualPositionWriteService writeService;

    @Autowired
    private InvestorWriteService investorWriteService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    /** Nothing is served before a sync, the manual positions included. */
    @BeforeEach
    void syncApple() {
        Holding apple = new Holding("AAPL", 265598, "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        syncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 100_000.0, 25_000.0, List.of(apple)));
    }

    // ── adding ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void aNewManualPositionIsServedAsOneClosedPositionWithItsTwoTrades() {
        long manualPositionId = writeService.addManualPosition(new NewManualPositionRequest(" msft ", " usd ",
                "  Technology ", " Sold before PortfolioBoss ", new BigDecimal("5"), LocalDate.of(2022, 1, 10),
                new BigDecimal("300"), new BigDecimal("2"), LocalDate.of(2023, 5, 1), new BigDecimal("310"),
                new BigDecimal("3"), null)).id();

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.source()).isEqualTo(ClosedPositionSource.MANUAL);
            assertThat(closedPosition.manualPositionId()).isEqualTo(manualPositionId);
            assertThat(closedPosition.holdingId()).isNull();
            assertThat(closedPosition.symbol()).isEqualTo("MSFT");
            assertThat(closedPosition.currency()).isEqualTo("USD");
            assertThat(closedPosition.sector()).isEqualTo("Technology");
            assertThat(closedPosition.note()).isEqualTo("Sold before PortfolioBoss");
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2022, 1, 10));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2023, 5, 1));
            assertThat(closedPosition.quantity()).isEqualByComparingTo("5");
            assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("300");
            assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("310");
            assertThat(closedPosition.commissions()).isEqualByComparingTo("5");      // 2 + 3
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("45");     // 1,550 − 1,500 − 5
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("0");
            assertThat(closedPosition.trades()).extracting(TradeResponse::side)
                    .containsExactly(TradeSide.BUY, TradeSide.SELL);
            // the request names no investor: both trades are the account owner's
            assertThat(closedPosition.investorId()).isEqualTo(accountOwnerId());
            assertThat(closedPosition.trades()).extracting(TradeResponse::investorId)
                    .containsOnly(accountOwnerId());
        });
    }

    @Test
    void aNewManualPositionForAnotherInvestorIsTheirs() {
        long aviId = investorWriteService.addInvestor(new InvestorRequest("Avi")).id();

        writeService.addManualPosition(new NewManualPositionRequest("MSFT", "USD", null, null, new BigDecimal("5"),
                LocalDate.of(2022, 1, 10), new BigDecimal("300"), null, LocalDate.of(2023, 5, 1),
                new BigDecimal("310"), null, aviId));

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.investorId()).isEqualTo(aviId);
            assertThat(closedPosition.trades()).extracting(TradeResponse::investorId).containsOnly(aviId);
        });
    }

    @Test
    void aNewManualPositionForAnInvestorThatDoesNotExistIs400AndStoresNothing() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.addManualPosition(new NewManualPositionRequest("MSFT", "USD", null, null,
                        new BigDecimal("5"), LocalDate.of(2022, 1, 10), new BigDecimal("300"), null,
                        LocalDate.of(2023, 5, 1), new BigDecimal("310"), null, MISSING_ID)));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("No investor with id " + MISSING_ID);
        assertThat(readManualClosedPositions()).isEmpty();
    }

    /** 600 shares: $6 to buy and $6 to sell. */
    @Test
    void withoutCommissionsTheBuyAndTheSellAreEachChargedTheDefault() {
        writeService.addManualPosition(microsoftRoundTrip("600"));

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.trades()).extracting(TradeResponse::commission)
                    .allSatisfy(commission -> assertThat(commission).isEqualByComparingTo("6"));
            assertThat(closedPosition.commissions()).isEqualByComparingTo("12");
        });
    }

    /**
     * The example that started it (decision 10): three buys of 9988.HK and one sell of all 300 are one row at the
     * average buy price, 174.50. Its one sell, the last one, is corrected from 100 to 300 shares along the way.
     */
    @Test
    void threeBuysAndOneSellAreOneClosedPositionAtTheAverageBuyPrice() {
        long manualPositionId = writeService.addManualPosition(new NewManualPositionRequest("9988.HK", "HKD",
                "E-commerce", null, new BigDecimal("100"), LocalDate.of(2021, 4, 21), new BigDecimal("221"),
                new BigDecimal("5"), LocalDate.of(2025, 9, 30), new BigDecimal("177.7"), new BigDecimal("5"),
                null)).id();
        writeService.addTrade(manualPositionId, buyOf("100", "162.1", LocalDate.of(2021, 8, 19)));
        writeService.addTrade(manualPositionId, buyOf("100", "140.4", LocalDate.of(2021, 11, 19)));
        long sellId = sellIdOf(manualPositionId);
        forgetWhatHibernateLoaded();

        holdingWriteService.changeTrade(sellId, new TradeRequest(LocalDate.of(2025, 9, 30), TradeSide.SELL,
                new BigDecimal("300"), new BigDecimal("177.7"), null, new BigDecimal("5"), null));

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.quantity()).isEqualByComparingTo("300");
            assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("174.5");
            assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("177.7");
            assertThat(closedPosition.commissions()).isEqualByComparingTo("20");
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("940");   // 53,310 − 52,350 − 20
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2021, 4, 21));
            assertThat(closedPosition.trades()).hasSize(4);
            // a trade added to the position later is the account owner's too
            assertThat(closedPosition.trades()).extracting(TradeResponse::investorId)
                    .containsOnly(accountOwnerId());
        });
    }

    @Test
    void addingATradeToAManualPositionThatDoesNotExistIs404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.addTrade(MISSING_ID, buyOf("10", "100", LocalDate.of(2022, 1, 1))));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("No manual position with id " + MISSING_ID);
    }

    // ── its details ─────────────────────────────────────────────────────────────────────────────

    @Test
    void changingItsDetailsKeepsItsTrades() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("5")).id();
        forgetWhatHibernateLoaded();

        writeService.changeManualPosition(manualPositionId, new ManualPositionRequest(" nvda ", "usd", null, "IPO"));

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.symbol()).isEqualTo("NVDA");
            assertThat(closedPosition.sector()).isNull();
            assertThat(closedPosition.note()).isEqualTo("IPO");
            assertThat(closedPosition.trades()).hasSize(2);
        });
    }

    @Test
    void deletingAManualPositionDeletesItsTradesAndIs404TheSecondTime() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("5")).id();
        forgetWhatHibernateLoaded();

        writeService.deleteManualPosition(manualPositionId);
        forgetWhatHibernateLoaded();

        assertThat(readManualClosedPositions()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from trade where manual_position_id = ?", Integer.class,
                manualPositionId)).isZero();
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.deleteManualPosition(manualPositionId));
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── its last sell ───────────────────────────────────────────────────────────────────────────

    @Test
    void theLastSellOfAManualPositionCanBeCorrected() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("5")).id();
        long sellId = sellIdOf(manualPositionId);
        forgetWhatHibernateLoaded();

        holdingWriteService.changeTrade(sellId, new TradeRequest(LocalDate.of(2023, 6, 1), TradeSide.SELL,
                new BigDecimal("5"), new BigDecimal("320"), "Sold higher", new BigDecimal("1"), null));

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2023, 6, 1));
            assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("320");
        });
    }

    @Test
    void theLastSellOfAManualPositionCannotBeDeleted() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("5")).id();
        long sellId = sellIdOf(manualPositionId);
        forgetWhatHibernateLoaded();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> holdingWriteService.deleteTrade(sellId));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception.getReason()).contains("Delete the whole position instead");
        assertThat(readManualClosedPositions()).singleElement()
                .satisfies(closedPosition -> assertThat(closedPosition.trades()).hasSize(2));
    }

    @Test
    void theLastSellOfAManualPositionCannotBeTurnedIntoABuy() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("5")).id();
        long sellId = sellIdOf(manualPositionId);
        forgetWhatHibernateLoaded();

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> holdingWriteService.changeTrade(sellId, new TradeRequest(LocalDate.of(2023, 5, 1),
                        TradeSide.BUY, new BigDecimal("5"), new BigDecimal("310"), null, null, null)));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Bought 10 and 10, sold 10 and 10: deleting one sell leaves the other, so the position stays — partly sold. */
    @Test
    void aSellCanBeDeletedWhileAnotherRemains() {
        long manualPositionId = writeService.addManualPosition(microsoftRoundTrip("10")).id();
        long firstSellId = sellIdOf(manualPositionId);
        writeService.addTrade(manualPositionId, buyOf("10", "305", LocalDate.of(2022, 2, 1)));
        writeService.addTrade(manualPositionId, new TradeRequest(LocalDate.of(2023, 6, 1), TradeSide.SELL,
                new BigDecimal("10"), new BigDecimal("315"), null, null, null));
        forgetWhatHibernateLoaded();

        holdingWriteService.deleteTrade(firstSellId);

        assertThat(readManualClosedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.quantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("10");
        });
    }

    /** The rule is about manual positions only: a holding without a sell still shows up in the positions table. */
    @Test
    void aHoldingsOnlySellCanStillBeDeleted() {
        long appleHoldingId = readService.currentPortfolio().orElseThrow().holdings().get(0).id();
        holdingWriteService.addTrade(appleHoldingId, buyOf("10", "150", LocalDate.of(2024, 1, 1)));
        long sellId = holdingWriteService.addTrade(appleHoldingId, new TradeRequest(LocalDate.of(2024, 6, 1),
                TradeSide.SELL, new BigDecimal("10"), new BigDecimal("180"), null, null, null)).id();
        forgetWhatHibernateLoaded();

        holdingWriteService.deleteTrade(sellId);

        forgetWhatHibernateLoaded();
        assertThat(readService.currentPortfolio().orElseThrow().holdings().get(0).trades()).hasSize(1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Every request is its own transaction in the app; this makes the next read come from the database. */
    private void forgetWhatHibernateLoaded() {
        entityManager.flush();
        entityManager.clear();
    }

    /** The investor V4__investors.sql created. */
    private long accountOwnerId() {
        return jdbc.queryForObject("select id from investor where is_account_owner", Long.class);
    }

    private List<ClosedPositionResponse> readManualClosedPositions() {
        forgetWhatHibernateLoaded();
        return readService.currentPortfolio().orElseThrow().closedPositions().stream()
                .filter(closedPosition -> closedPosition.source() == ClosedPositionSource.MANUAL)
                .toList();
    }

    private long sellIdOf(long manualPositionId) {
        entityManager.flush();
        return jdbc.queryForObject("select id from trade where manual_position_id = ? and side = 'SELL'", Long.class,
                manualPositionId);
    }

    /** Bought at 300 on 2022-01-10 and sold at 310 on 2023-05-01, with no commission entered. */
    private NewManualPositionRequest microsoftRoundTrip(String quantity) {
        return new NewManualPositionRequest("MSFT", "USD", null, null, new BigDecimal(quantity),
                LocalDate.of(2022, 1, 10), new BigDecimal("300"), null, LocalDate.of(2023, 5, 1), new BigDecimal("310"),
                null, null);
    }

    private TradeRequest buyOf(String quantity, String price, LocalDate tradeDate) {
        return new TradeRequest(tradeDate, TradeSide.BUY, new BigDecimal(quantity), new BigDecimal(price), null,
                new BigDecimal("5"), null);
    }
}
