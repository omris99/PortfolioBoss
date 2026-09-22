package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.List;
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
    private static final int APPLE = 265598;
    private static final int MICROSOFT = 272093;
    private static final Instant FIRST_RUN = Instant.parse("2026-09-21T08:00:00Z");
    private static final Instant SECOND_RUN = Instant.parse("2026-09-22T08:00:00Z");

    @Autowired
    private PortfolioSyncService syncService;

    @Autowired
    private PortfolioReadService readService;

    @Autowired
    private TestEntityManager entityManager;

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
        assertThat(holding.conId()).isEqualTo(APPLE);
        assertThat(holding.sector()).isNull();
        assertThat(holding.status()).isEqualTo(HoldingStatus.OPEN);
    }

    @Test
    void writesFiguresIbDidNotReportAsNull() {
        Holding withoutCostData = new Holding(
                "MSFT", MICROSOFT, "STK", "USD", 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, ACCOUNT);
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

    private static PortfolioSnapshot snapshotOf(Instant asOf, double netLiquidation, double totalCashValue,
                                                Holding... holdings) {
        return new PortfolioSnapshot(ACCOUNT, asOf, netLiquidation, totalCashValue, List.of(holdings));
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
