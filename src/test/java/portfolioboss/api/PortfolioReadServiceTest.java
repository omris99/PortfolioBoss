package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.ClosedPositionSource;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.InvestorQuantityResponse;
import portfolioboss.api.response.InvestorResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.calculation.Benchmark;
import portfolioboss.calculation.CashMovementType;
import portfolioboss.calculation.DailyClose;
import portfolioboss.calculation.Holding;
import portfolioboss.calculation.HoldingStatus;
import portfolioboss.calculation.HoldingWarning;
import portfolioboss.calculation.HoldingWarningType;
import portfolioboss.calculation.MomentumLabel;
import portfolioboss.calculation.PortfolioSnapshot;
import portfolioboss.calculation.TradeSide;
import portfolioboss.db.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * The whole path the API serves: a real sync stores a snapshot in the PostgreSQL test database
 * ({@code portfolioboss_test}), then {@link PortfolioReadService} reads it back as the {@code PortfolioResponse}.
 * Same setup as {@code PortfolioSyncServiceTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({PortfolioSyncService.class, PortfolioReadService.class})
class PortfolioReadServiceTest {

    private static final String ACCOUNT = "U1234567";
    private static final int APPLE_CON_ID = 265598;
    private static final int MICROSOFT_CON_ID = 272093;
    private static final int NVIDIA_CON_ID = 4815747;
    private static final Instant FIRST_RUN = Instant.parse("2026-09-21T08:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-09-22T08:00:00Z");
    private static final LocalDate FIRST_CLOSE_DATE = LocalDate.of(2026, 1, 1);

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private PortfolioReadService readService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void hasNothingToServeBeforeTheFirstSync() {
        assertThat(readPortfolio()).isEmpty();
    }

    @Test
    void servesWhatTheSyncStored() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0)));

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.account()).isEqualTo(ACCOUNT);
        assertThat(portfolio.asOf()).isEqualTo(FIRST_RUN);
        assertThat(portfolio.netLiquidation()).isEqualTo(100_000.0);
        assertThat(portfolio.totalCashValue()).isEqualTo(25_000.0);
        assertThat(portfolio.holdings()).hasSize(1);
        HoldingResponse holding = portfolio.holdings().get(0);
        assertThat(holding.symbol()).isEqualTo("AAPL");
        assertThat(holding.secType()).isEqualTo("STK");
        assertThat(holding.currency()).isEqualTo("USD");
        assertThat(holding.position()).isEqualTo(10.0);
        assertThat(holding.averageCost()).isEqualTo(150.0);
        assertThat(holding.marketPrice()).isEqualTo(200.0);
        assertThat(holding.marketValue()).isEqualTo(2000.0);
        assertThat(holding.unrealizedPnl()).isEqualTo(500.0);
        assertThat(holding.realizedPnl()).isEqualTo(0.0);
        assertThat(holding.account()).isEqualTo(ACCOUNT);
        assertThat(holding.costBasis()).isEqualTo(1500.0);
        assertThat(holding.unrealizedPnlPercent()).isCloseTo(33.333, within(0.001));
        assertThat(holding.id()).isPositive();
        assertThat(holding.conId()).isEqualTo(APPLE_CON_ID);
        assertThat(holding.sector()).isNull();
        assertThat(holding.status()).isEqualTo(HoldingStatus.OPEN);
        assertThat(holding.firstBuyDate()).isNull();
        assertThat(holding.lastSellDate()).isNull();
        assertThat(holding.holdingDays()).isNull();
        assertThat(holding.trades()).isEmpty();
        assertThat(holding.warnings()).extracting(HoldingWarning::type).containsExactly(HoldingWarningType.NO_TRADES_LOGGED);
        assertThat(portfolio.closedPositions()).isEmpty();
        // the account owner alone: everything IB reports is theirs, even with no trade entered
        assertThat(holding.investorQuantities()).singleElement().satisfies(investorQuantity -> {
            assertThat(investorQuantity.investorId()).isEqualTo(accountOwnerId());
            assertThat(investorQuantity.quantity()).isEqualByComparingTo("10");
        });
        assertThat(portfolio.investors()).singleElement().satisfies(accountOwner -> {
            assertThat(accountOwner.id()).isEqualTo(accountOwnerId());
            assertThat(accountOwner.name()).isEqualTo("Me");
            assertThat(accountOwner.accountOwner()).isTrue();
            assertThat(accountOwner.depositsMinusWithdrawals()).isNull();
            assertThat(accountOwner.cash()).isEqualByComparingTo("25000");
            assertThat(accountOwner.sharesValue()).isEqualByComparingTo("2000");
            assertThat(accountOwner.totalValue()).isEqualByComparingTo("100000");
            assertThat(accountOwner.sharesCost()).isEqualByComparingTo("1500");
            assertThat(accountOwner.unrealizedPnl()).isEqualByComparingTo("500");
            assertThat(accountOwner.realizedPnlByCurrency()).isEmpty();
            assertThat(accountOwner.cashMovements()).isEmpty();
            assertThat(accountOwner.warnings()).isEmpty();
        });
    }

    /**
     * The example of INVESTORS_TODO.md, from the database to the response. IB: NAV 100,000, cash 40,000, shares worth
     * 60,000 that cost 50,000, NVDA at 180. Avi deposited 30,000 and bought 24 of the 39 NVDA at 120; the account owner
     * bought and sold AAPL (+500 USD) and 9988.HK (+950 HKD) before PortfolioBoss — manual positions.
     */
    @Test
    void splitsTheAccountBetweenTheAccountOwnerAndAnotherInvestor() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 40_000.0,
                holdingAt("NVDA", NVIDIA_CON_ID, 39, 120.0, 180.0),       // worth 7,020, cost 4,680
                holdingAt("MSFT", MICROSOFT_CON_ID, 20, 2266.0, 2649.0)));  // worth 52,980, cost 45,320
        long nvidiaHoldingId = holdingIdOf(NVIDIA_CON_ID);
        long accountOwnerId = accountOwnerId();
        long aviId = insertInvestor("Avi");
        insertDeposit(aviId, LocalDate.of(2026, 1, 10), "30000");
        insertTrade(nvidiaHoldingId, accountOwnerId, LocalDate.of(2026, 2, 1), TradeSide.BUY, "15", "120");
        insertTrade(nvidiaHoldingId, aviId, LocalDate.of(2026, 2, 1), TradeSide.BUY, "24", "120");
        long appleId = insertManualPosition("AAPL", "USD");
        insertManualTrade(appleId, LocalDate.of(2024, 1, 1), TradeSide.BUY, "10", "100");
        insertManualTrade(appleId, LocalDate.of(2024, 6, 1), TradeSide.SELL, "10", "150");
        long alibabaId = insertManualPosition("9988.HK", "HKD");
        insertManualTrade(alibabaId, LocalDate.of(2021, 4, 21), TradeSide.BUY, "100", "100");
        insertManualTrade(alibabaId, LocalDate.of(2025, 9, 30), TradeSide.SELL, "100", "109.5");

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        HoldingResponse nvidia = portfolio.holdings().get(0);
        assertThat(nvidia.investorQuantities())
                .extracting(InvestorQuantityResponse::investorId, investorQuantity -> investorQuantity.quantity().intValue())
                .containsExactly(tuple(accountOwnerId, 15), tuple(aviId, 24));
        // the account owner's part is IB's 7,020 / 4,680 less Avi's 4,320 / 2,880
        InvestorQuantityResponse accountOwnerPart = nvidia.investorQuantities().get(0);
        assertThat(accountOwnerPart.sharesValue()).isEqualByComparingTo("2700");
        assertThat(accountOwnerPart.sharesCost()).isEqualByComparingTo("1800");
        assertThat(accountOwnerPart.unrealizedPnl()).isEqualByComparingTo("900");
        assertThat(accountOwnerPart.unrealizedPnlPercent()).isEqualByComparingTo("50");
        InvestorQuantityResponse aviPart = nvidia.investorQuantities().get(1);
        assertThat(aviPart.sharesValue()).isEqualByComparingTo("4320");
        assertThat(aviPart.sharesCost()).isEqualByComparingTo("2880");
        assertThat(aviPart.unrealizedPnl()).isEqualByComparingTo("1440");
        assertThat(aviPart.unrealizedPnlPercent()).isEqualByComparingTo("50");
        assertThat(nvidia.trades()).extracting(TradeResponse::investorId).containsExactly(accountOwnerId, aviId);
        assertThat(portfolio.closedPositions()).extracting(ClosedPositionResponse::investorId)
                .containsOnly(accountOwnerId);
        assertThat(portfolio.investors()).extracting(InvestorResponse::id).containsExactly(accountOwnerId, aviId);

        InvestorResponse accountOwner = portfolio.investors().get(0);
        assertThat(accountOwner.depositsMinusWithdrawals()).isNull();
        assertThat(accountOwner.cash()).isEqualByComparingTo("12880");
        assertThat(accountOwner.sharesValue()).isEqualByComparingTo("55680");
        assertThat(accountOwner.totalValue()).isEqualByComparingTo("68560");
        assertThat(accountOwner.sharesCost()).isEqualByComparingTo("47120");
        assertThat(accountOwner.unrealizedPnl()).isEqualByComparingTo("8560");
        assertThat(accountOwner.realizedPnlByCurrency()).containsOnlyKeys("HKD", "USD");
        assertThat(accountOwner.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("500");
        assertThat(accountOwner.realizedPnlByCurrency().get("HKD")).isEqualByComparingTo("950");
        assertThat(accountOwner.totalPnl()).isEqualByComparingTo("9060");     // the HKD is not added in
        assertThat(accountOwner.warnings()).isEmpty();

        InvestorResponse avi = portfolio.investors().get(1);
        assertThat(avi.name()).isEqualTo("Avi");
        assertThat(avi.accountOwner()).isFalse();
        assertThat(avi.depositsMinusWithdrawals()).isEqualByComparingTo("30000");
        assertThat(avi.cash()).isEqualByComparingTo("27120");
        assertThat(avi.sharesValue()).isEqualByComparingTo("4320");
        assertThat(avi.totalValue()).isEqualByComparingTo("31440");
        assertThat(avi.sharesCost()).isEqualByComparingTo("2880");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("1440");
        assertThat(avi.unrealizedPnlPercent()).isEqualByComparingTo("50");
        assertThat(avi.realizedPnlByCurrency()).isEmpty();
        assertThat(avi.totalPnl()).isEqualByComparingTo("1440");
        assertThat(avi.cashMovements()).singleElement().satisfies(deposit -> {
            assertThat(deposit.movementDate()).isEqualTo(LocalDate.of(2026, 1, 10));
            assertThat(deposit.type()).isEqualTo(CashMovementType.DEPOSIT);
            assertThat(deposit.amount()).isEqualByComparingTo("30000");
            assertThat(deposit.note()).isNull();
        });
        assertThat(avi.warnings()).isEmpty();
    }

    @Test
    void derivesBuySellDatesAndHoldingDaysFromTheTradesTable() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        insertTrade(appleHoldingId, LocalDate.of(2024, 3, 14), TradeSide.BUY, "10", "150.00", "Initial position");

        HoldingResponse holding = readPortfolio().orElseThrow().holdings().get(0);

        LocalDate snapshotDate = FIRST_RUN.atZone(ZoneId.systemDefault()).toLocalDate();
        assertThat(holding.firstBuyDate()).isEqualTo(LocalDate.of(2024, 3, 14));
        assertThat(holding.lastSellDate()).isNull();
        assertThat(holding.holdingDays()).isEqualTo(ChronoUnit.DAYS.between(LocalDate.of(2024, 3, 14), snapshotDate));
        assertThat(holding.trades()).hasSize(1);
        TradeResponse trade = holding.trades().get(0);
        assertThat(trade.tradeDate()).isEqualTo(LocalDate.of(2024, 3, 14));
        assertThat(trade.side()).isEqualTo(TradeSide.BUY);
        assertThat(trade.quantity()).isEqualByComparingTo("10");
        assertThat(trade.price()).isEqualByComparingTo("150.00");
        assertThat(trade.note()).isEqualTo("Initial position");
        // the 10 bought match the 10 IB reports: IB's stored quantity reached the check
        assertThat(holding.warnings()).isEmpty();
    }

    @Test
    void aFullSellClosesThePositionPeriodSoHoldingDaysRunsToTheSellDateNotTheSnapshot() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        insertTrade(appleHoldingId, LocalDate.of(2024, 1, 1), TradeSide.BUY, "10", "150.00", null);
        insertTrade(appleHoldingId, LocalDate.of(2024, 6, 1), TradeSide.SELL, "10", "180.00", null);
        forgetWhatHibernateLoaded();
        syncService.sync(snapshotOf(SECOND_RUN, 100_000.0, 25_000.0));   // Apple is gone: CLOSED

        PortfolioResponse portfolio = readPortfolio().orElseThrow();
        HoldingResponse holding = portfolio.holdings().get(0);

        assertThat(holding.status()).isEqualTo(HoldingStatus.CLOSED);
        assertThat(holding.firstBuyDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(holding.lastSellDate()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(holding.holdingDays())
                .isEqualTo(ChronoUnit.DAYS.between(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1)));
        assertThat(holding.trades()).hasSize(2);
        assertThat(portfolio.closedPositions())
                .extracting(ClosedPositionResponse::closeDate)
                .containsExactly(LocalDate.of(2024, 6, 1));
    }

    @Test
    void servesTheClosedPositionsOfAHoldingThatIsOpenAgain() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(5, 200.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        jdbc.update("update holding set sector = ? where id = ?", "Technology", appleHoldingId);
        insertTrade(appleHoldingId, LocalDate.of(2024, 3, 1), TradeSide.BUY, "10", "150.00", null, "5");
        insertTrade(appleHoldingId, LocalDate.of(2025, 6, 1), TradeSide.SELL, "10", "180.00", null, "5");
        insertTrade(appleHoldingId, LocalDate.of(2026, 2, 1), TradeSide.BUY, "5", "200.00", null, "5");

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.holdings().get(0).status()).isEqualTo(HoldingStatus.OPEN);
        assertThat(portfolio.holdings().get(0).trades()).extracting(TradeResponse::commission)
                .allSatisfy(commission -> assertThat(commission).isEqualByComparingTo("5"));
        assertThat(portfolio.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.source()).isEqualTo(ClosedPositionSource.TRADES);
            assertThat(closedPosition.holdingId()).isEqualTo(appleHoldingId);
            assertThat(closedPosition.manualPositionId()).isNull();
            assertThat(closedPosition.symbol()).isEqualTo("AAPL");
            assertThat(closedPosition.currency()).isEqualTo("USD");
            assertThat(closedPosition.sector()).isEqualTo("Technology");
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(closedPosition.holdingDays()).isEqualTo(457);
            assertThat(closedPosition.quantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("150");
            assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
            // the buy and the sell of this period, $5 each — not the buy of the period still open
            assertThat(closedPosition.commissions()).isEqualByComparingTo("10");
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("290");
            assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("19.33333333333333");
            assertThat(closedPosition.warning()).isNull();
            assertThat(closedPosition.note()).isNull();
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("0");
            // the buy and the sell of this period — not the 2026 buy, which opened the next one
            assertThat(closedPosition.trades())
                    .extracting(TradeResponse::tradeDate)
                    .containsExactly(LocalDate.of(2024, 3, 1), LocalDate.of(2025, 6, 1));
        });
    }

    @Test
    void aPartialSellOfAHoldingStillOpenIsServedWithWhatIsStillHeld() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 100.0)));
        long appleHoldingId = holdingIdOf(APPLE_CON_ID);
        insertTrade(appleHoldingId, LocalDate.of(2024, 1, 1), TradeSide.BUY, "15", "100.00", null);
        insertTrade(appleHoldingId, LocalDate.of(2024, 6, 1), TradeSide.SELL, "5", "130.00", null);

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.holdings().get(0).status()).isEqualTo(HoldingStatus.OPEN);
        assertThat(portfolio.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.quantity()).isEqualByComparingTo("5");
            assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("100");
            assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("130");
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("150");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2024, 6, 1));
            assertThat(closedPosition.trades()).hasSize(2);
        });
    }

    @Test
    void writesFiguresIbDidNotReportAsNull() {
        Holding withoutCostData = new Holding(
                "MSFT", MICROSOFT_CON_ID, "STK", "USD", 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, ACCOUNT);
        syncService.sync(snapshotOf(FIRST_RUN, Double.NaN, Double.NaN, withoutCostData));

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.netLiquidation()).isNull();
        assertThat(portfolio.totalCashValue()).isNull();
        HoldingResponse holding = portfolio.holdings().get(0);
        assertThat(holding.position()).isEqualTo(5.0);
        assertThat(holding.averageCost()).isNull();
        assertThat(holding.marketValue()).isNull();
        assertThat(holding.costBasis()).isNull();
        assertThat(holding.unrealizedPnlPercent()).isNull();
    }

    @Test
    void includesClosedHoldingsWithTheirStatus() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0), microsoft(5, 300.0)));
        forgetWhatHibernateLoaded();
        syncService.sync(snapshotOf(SECOND_RUN, 100_000.0, 25_000.0, microsoft(5, 300.0)));

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.holdings())
                .extracting(HoldingResponse::symbol, HoldingResponse::status, HoldingResponse::position)
                .containsExactly(tuple("AAPL", HoldingStatus.CLOSED, 0.0), tuple("MSFT", HoldingStatus.OPEN, 5.0));
    }

    /** A year of closes rising faster than SPY's: every check holds. Microsoft has no closes stored: no momentum. */
    @Test
    void servesTheMomentumFromTheStoredDailyCloses() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0), microsoft(5, 300.0)), Map.of(
                APPLE_CON_ID, risingCloses(0.003),
                Benchmark.SPY_CON_ID, risingCloses(0.001)));

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        MomentumResponse appleMomentum = portfolio.holdings().get(0).momentum();
        assertThat(appleMomentum.asOf()).isEqualTo(FIRST_CLOSE_DATE.plusDays(249));
        assertThat(appleMomentum.sma200()).isNotNull();
        assertThat(appleMomentum.beatsSpy()).isTrue();
        assertThat(appleMomentum.score()).isEqualTo(5);
        assertThat(appleMomentum.label()).isEqualTo(MomentumLabel.STRONG);
        assertThat(portfolio.holdings().get(1).symbol()).isEqualTo("MSFT");
        assertThat(portfolio.holdings().get(1).momentum()).isNull();
    }

    @Test
    void servesTheLatestSyncOnly() {
        syncService.sync(snapshotOf(FIRST_RUN, 100_000.0, 25_000.0, apple(10, 150.0)));
        forgetWhatHibernateLoaded();
        syncService.sync(snapshotOf(SECOND_RUN, 110_000.0, 20_000.0, apple(12, 160.0)));

        PortfolioResponse portfolio = readPortfolio().orElseThrow();

        assertThat(portfolio.asOf()).isEqualTo(SECOND_RUN);
        assertThat(portfolio.netLiquidation()).isEqualTo(110_000.0);
        assertThat(portfolio.holdings()).extracting(HoldingResponse::position).containsExactly(12.0);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private Optional<PortfolioResponse> readPortfolio() {
        forgetWhatHibernateLoaded();
        return readService.currentPortfolio();
    }

    /**
     * Every start of the app is a new process, and the API reads in a later request than the sync. {@code clear()}
     * makes the next read come from the database, not from what Hibernate still remembers.
     */
    private void forgetWhatHibernateLoaded() {
        entityManager.flush();
        entityManager.clear();
    }

    private PortfolioSnapshot snapshotOf(Instant asOf, double netLiquidation, double totalCashValue,
                                                Holding... holdings) {
        return new PortfolioSnapshot(ACCOUNT, asOf, netLiquidation, totalCashValue, List.of(holdings));
    }

    private long holdingIdOf(int conId) {
        entityManager.flush();
        return jdbc.queryForObject(
                "select id from holding where account = ? and con_id = ?", Long.class, ACCOUNT, conId);
    }

    /** No commission, so the figures the tests check stay round. */
    private void insertTrade(long holdingId, LocalDate tradeDate, TradeSide side, String quantity, String price,
                             String note) {
        insertTrade(holdingId, tradeDate, side, quantity, price, note, "0");
    }

    /** The account owner's trade. */
    private void insertTrade(long holdingId, LocalDate tradeDate, TradeSide side, String quantity, String price,
                             String note, String commission) {
        insertTrade(holdingId, accountOwnerId(), tradeDate, side, quantity, price, note, commission);
    }

    /** No note and no commission. */
    private void insertTrade(long holdingId, long investorId, LocalDate tradeDate, TradeSide side, String quantity,
                             String price) {
        insertTrade(holdingId, investorId, tradeDate, side, quantity, price, null, "0");
    }

    private void insertTrade(long holdingId, long investorId, LocalDate tradeDate, TradeSide side, String quantity,
                             String price, String note, String commission) {
        // Bound as BigDecimal, not String: the driver would otherwise send them as varchar, and
        // Postgres refuses to insert a varchar into a numeric column without an explicit cast.
        jdbc.update("insert into trade (holding_id, investor_id, trade_date, side, quantity, price, note, commission) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                holdingId, investorId, tradeDate, side.name(), new BigDecimal(quantity),
                price == null ? null : new BigDecimal(price), note, new BigDecimal(commission));
        forgetWhatHibernateLoaded();   // the sync's session must not overwrite what was just inserted with SQL
    }

    /** The investor V4__investors.sql created. */
    private long accountOwnerId() {
        return jdbc.queryForObject("select id from investor where is_account_owner", Long.class);
    }

    private long insertInvestor(String name) {
        return jdbc.queryForObject("insert into investor (name) values (?) returning id", Long.class, name);
    }

    private void insertDeposit(long investorId, LocalDate movementDate, String amount) {
        jdbc.update("insert into investor_cash_movement (investor_id, movement_date, type, amount) values (?, ?, ?, ?)",
                investorId, movementDate, "DEPOSIT", new BigDecimal(amount));
    }

    private long insertManualPosition(String symbol, String currency) {
        return jdbc.queryForObject("insert into manual_position (symbol, currency) values (?, ?) returning id",
                Long.class, symbol, currency);
    }

    /** The account owner's, with no commission. */
    private void insertManualTrade(long manualPositionId, LocalDate tradeDate, TradeSide side, String quantity,
                                   String price) {
        jdbc.update("insert into trade (manual_position_id, investor_id, trade_date, side, quantity, price, commission) "
                        + "values (?, ?, ?, ?, ?, ?, 0)",
                manualPositionId, accountOwnerId(), tradeDate, side.name(), new BigDecimal(quantity),
                new BigDecimal(price));
    }

    /** 250 daily closes from {@link #FIRST_CLOSE_DATE}, growing by {@code dailyGrowth} a day. */
    private List<DailyClose> risingCloses(double dailyGrowth) {
        List<DailyClose> closes = new ArrayList<>();
        for (int day = 0; day < 250; day++) {
            closes.add(new DailyClose(FIRST_CLOSE_DATE.plusDays(day), 100 * Math.pow(1 + dailyGrowth, day)));
        }
        return closes;
    }

    private Holding apple(double position, double averageCost) {
        return holding("AAPL", APPLE_CON_ID, position, averageCost);
    }

    private Holding microsoft(double position, double averageCost) {
        return holding("MSFT", MICROSOFT_CON_ID, position, averageCost);
    }

    private Holding holding(String symbol, int conId, double position, double averageCost) {
        return holdingAt(symbol, conId, position, averageCost, 200.0);
    }

    private Holding holdingAt(String symbol, int conId, double position, double averageCost, double marketPrice) {
        return new Holding(symbol, conId, "STK", "USD", position, averageCost, marketPrice,
                position * marketPrice, position * (marketPrice - averageCost), 0.0, ACCOUNT);
    }
}
