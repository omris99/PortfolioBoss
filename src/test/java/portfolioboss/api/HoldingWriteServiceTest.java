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
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.request.SectorRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.InvestorQuantityResponse;
import portfolioboss.api.response.InvestorResponse;
import portfolioboss.api.response.TradeResponse;
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
 * What the write endpoints store, against the PostgreSQL test database ({@code portfolioboss_test}), read back the
 * way the UI reads it: through {@link PortfolioReadService}. Same setup as {@code PortfolioReadServiceTest}. The HTTP
 * side (status codes, validation messages, 415) is in {@code HoldingWriteControllerTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, PortfolioReadService.class, HoldingWriteService.class,
        InvestorWriteService.class})
class HoldingWriteServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant FIRST_RUN = Instant.parse("2026-09-21T08:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-09-22T08:00:00Z");
    private static final long MISSING_ID = 999_999_999L;

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private PortfolioReadService readService;

    @Autowired
    private HoldingWriteService writeService;

    @Autowired
    private InvestorWriteService investorWriteService;

    @Autowired
    private TestEntityManager entityManager;

    private long appleHoldingId;

    @BeforeEach
    void syncApple() {
        syncService.sync(snapshotWithApple(FIRST_RUN));
        appleHoldingId = readApple().id();
    }

    @Test
    void anAddedTradeIsServedWithTheHoldingAndSetsItsBuyDate() {
        TradeResponse addedTrade = writeService.addTrade(appleHoldingId,
                new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY, new BigDecimal("10.5"),
                        new BigDecimal("150.25"), "Initial position", new BigDecimal("1.25"), null));

        HoldingResponse apple = readApple();

        assertThat(addedTrade.id()).isPositive();
        assertThat(apple.firstBuyDate()).isEqualTo(LocalDate.of(2024, 3, 14));
        assertThat(apple.trades()).hasSize(1);
        TradeResponse storedTrade = apple.trades().get(0);
        assertThat(storedTrade.id()).isEqualTo(addedTrade.id());
        assertThat(storedTrade.side()).isEqualTo(TradeSide.BUY);
        assertThat(storedTrade.quantity()).isEqualByComparingTo("10.5");
        assertThat(storedTrade.price()).isEqualByComparingTo("150.25");
        assertThat(storedTrade.note()).isEqualTo("Initial position");
        assertThat(storedTrade.commission()).isEqualByComparingTo("1.25");
        // the request names no investor: the trade is the account owner's
        assertThat(storedTrade.investorId()).isEqualTo(addedTrade.investorId()).isEqualTo(accountOwnerId());
    }

    @Test
    void aTradeWithoutPriceOrNoteIsStoredWithBothNull() {
        writeService.addTrade(appleHoldingId,
                new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY, new BigDecimal("10"), null, "   ", null,
                        null));

        TradeResponse storedTrade = readApple().trades().get(0);

        assertThat(storedTrade.price()).isNull();
        assertThat(storedTrade.note()).isNull();
    }

    @Test
    void aTradeWithoutACommissionIsChargedTheDefaultForItsQuantity() {
        TradeResponse addedTrade = writeService.addTrade(appleHoldingId, buyWithoutCommission("600"));

        assertThat(addedTrade.commission()).isEqualByComparingTo("6");
        assertThat(readApple().trades().get(0).commission()).isEqualByComparingTo("6");
    }

    @Test
    void aCommissionOfZeroIsKeptNotReplacedByTheDefault() {
        writeService.addTrade(appleHoldingId, new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("150"), null, BigDecimal.ZERO, null));

        assertThat(readApple().trades().get(0).commission()).isEqualByComparingTo("0");
    }

    @Test
    void changingATradeWithoutACommissionWorksTheDefaultOutAgainFromTheNewQuantity() {
        long tradeId = writeService.addTrade(appleHoldingId, buyWithoutCommission("10")).id();   // $5, the minimum
        forgetWhatHibernateLoaded();

        writeService.changeTrade(tradeId, buyWithoutCommission("1000"));

        assertThat(readApple().trades().get(0).commission()).isEqualByComparingTo("10");
    }

    @Test
    void addingATradeToAHoldingThatDoesNotExistIs404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.addTrade(MISSING_ID, buyOfTen(LocalDate.of(2024, 3, 14))));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("No holding with id " + MISSING_ID);
    }

    @Test
    void changingATradeReplacesEveryField() {
        long tradeId = writeService.addTrade(appleHoldingId, buyOfTen(LocalDate.of(2024, 3, 14))).id();
        forgetWhatHibernateLoaded();

        writeService.changeTrade(tradeId, new TradeRequest(LocalDate.of(2024, 5, 2), TradeSide.SELL,
                new BigDecimal("4"), new BigDecimal("190"), "Trimmed after earnings", new BigDecimal("2.5"), null));

        TradeResponse storedTrade = readApple().trades().get(0);
        assertThat(storedTrade.id()).isEqualTo(tradeId);
        assertThat(storedTrade.tradeDate()).isEqualTo(LocalDate.of(2024, 5, 2));
        assertThat(storedTrade.side()).isEqualTo(TradeSide.SELL);
        assertThat(storedTrade.quantity()).isEqualByComparingTo("4");
        assertThat(storedTrade.price()).isEqualByComparingTo("190");
        assertThat(storedTrade.note()).isEqualTo("Trimmed after earnings");
        assertThat(storedTrade.commission()).isEqualByComparingTo("2.5");
    }

    @Test
    void changingATradeThatDoesNotExistIs404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.changeTrade(MISSING_ID, buyOfTen(LocalDate.of(2024, 3, 14))));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exception.getReason()).isEqualTo("No trade with id " + MISSING_ID);
    }

    @Test
    void deletingATradeTwiceIs404TheSecondTime() {
        long tradeId = writeService.addTrade(appleHoldingId, buyOfTen(LocalDate.of(2024, 3, 14))).id();
        forgetWhatHibernateLoaded();

        writeService.deleteTrade(tradeId);
        forgetWhatHibernateLoaded();

        assertThat(readApple().trades()).isEmpty();
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.deleteTrade(tradeId));
        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSectorIsStoredWithoutSurroundingSpaces() {
        writeService.changeSector(appleHoldingId, new SectorRequest("  Technology  "));

        assertThat(readApple().sector()).isEqualTo("Technology");
    }

    @Test
    void aBlankSectorClearsIt() {
        writeService.changeSector(appleHoldingId, new SectorRequest("Technology"));
        forgetWhatHibernateLoaded();

        writeService.changeSector(appleHoldingId, new SectorRequest("   "));

        assertThat(readApple().sector()).isNull();
    }

    @Test
    void changingTheSectorOfAHoldingThatDoesNotExistIs404() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.changeSector(MISSING_ID, new SectorRequest("Technology")));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSectorAndTradesEnteredByHandSurviveTheNextSync() {
        writeService.changeSector(appleHoldingId, new SectorRequest("Technology"));
        writeService.addTrade(appleHoldingId, buyOfTen(LocalDate.of(2024, 3, 14)));
        forgetWhatHibernateLoaded();

        syncService.sync(snapshotWithApple(SECOND_RUN));

        HoldingResponse apple = readApple();
        assertThat(apple.sector()).isEqualTo("Technology");
        assertThat(apple.trades()).hasSize(1);
    }

    // ── whose trade ─────────────────────────────────────────────────────────────────────────────

    /** IB reports 10 Apple: 4 bought by Avi leave the account owner 6. */
    @Test
    void aTradeForAnotherInvestorIsTheirsAndSplitsTheHolding() {
        long aviId = addAvi();

        TradeResponse addedTrade = writeService.addTrade(appleHoldingId, buyOfFour(aviId));

        assertThat(addedTrade.investorId()).isEqualTo(aviId);
        HoldingResponse apple = readApple();
        assertThat(apple.trades()).extracting(TradeResponse::investorId).containsExactly(aviId);
        assertThat(apple.investorQuantities())
                .extracting(InvestorQuantityResponse::investorId, quantity -> quantity.quantity().intValue())
                .containsExactly(tuple(accountOwnerId(), 6), tuple(aviId, 4));
    }

    @Test
    void aTradeForAnInvestorThatDoesNotExistIs400() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> writeService.addTrade(appleHoldingId, buyOfFour(MISSING_ID)));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exception.getReason()).isEqualTo("No investor with id " + MISSING_ID);
    }

    @Test
    void correctingATradeWithoutNamingAnInvestorKeepsItsInvestor() {
        long aviId = addAvi();
        long tradeId = writeService.addTrade(appleHoldingId, buyOfFour(aviId)).id();
        forgetWhatHibernateLoaded();

        writeService.changeTrade(tradeId, new TradeRequest(LocalDate.of(2024, 3, 15), TradeSide.BUY,
                new BigDecimal("4"), new BigDecimal("155"), null, null, null));

        assertThat(readApple().trades()).extracting(TradeResponse::investorId).containsExactly(aviId);
    }

    @Test
    void correctingATradeWithAnInvestorMovesItToThem() {
        long aviId = addAvi();
        long tradeId = writeService.addTrade(appleHoldingId, buyOfFour(aviId)).id();
        forgetWhatHibernateLoaded();

        writeService.changeTrade(tradeId, new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY,
                new BigDecimal("4"), new BigDecimal("150"), null, null, accountOwnerId()));

        assertThat(readApple().trades()).extracting(TradeResponse::investorId).containsExactly(accountOwnerId());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Every request is its own transaction in the app; this makes the next read come from the database. */
    private void forgetWhatHibernateLoaded() {
        entityManager.flush();
        entityManager.clear();
    }

    private HoldingResponse readApple() {
        forgetWhatHibernateLoaded();
        return readService.currentPortfolio().orElseThrow().holdings().get(0);
    }

    /** The investor V4__investors.sql created, as the API serves it. */
    private long accountOwnerId() {
        return readService.currentPortfolio().orElseThrow().investors().stream()
                .filter(InvestorResponse::accountOwner)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private TradeRequest buyOfTen(LocalDate tradeDate) {
        return new TradeRequest(tradeDate, TradeSide.BUY, new BigDecimal("10"), new BigDecimal("150"), null, null,
                null);
    }

    private TradeRequest buyOfFour(Long investorId) {
        return new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY, new BigDecimal("4"), new BigDecimal("150"),
                null, null, investorId);
    }

    private long addAvi() {
        long aviId = investorWriteService.addInvestor(new InvestorRequest("Avi")).id();
        forgetWhatHibernateLoaded();
        return aviId;
    }

    private TradeRequest buyWithoutCommission(String quantity) {
        return new TradeRequest(LocalDate.of(2024, 3, 14), TradeSide.BUY, new BigDecimal(quantity),
                new BigDecimal("150"), null, null, null);
    }

    private PortfolioSnapshot snapshotWithApple(Instant asOf) {
        Holding apple = new Holding("AAPL", 265598, "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 0.0, ACCOUNT);
        return new PortfolioSnapshot(ACCOUNT, asOf, 100_000.0, 25_000.0, List.of(apple));
    }
}
