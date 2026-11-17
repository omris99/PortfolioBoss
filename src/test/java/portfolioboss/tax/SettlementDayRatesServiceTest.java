package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portfolioboss.db.investors.InvestorRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.db.portfolio.ManualPositionEntity;
import portfolioboss.db.portfolio.TradeEntity;
import portfolioboss.flex.FlexReport;
import portfolioboss.flex.FlexReportCashTransaction;
import portfolioboss.flex.FlexReportPeriod;
import portfolioboss.flex.FlexReportTrade;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.model.portfolio.TradeSide;
import portfolioboss.sync.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verify;

/**
 * Which settlement days the tax asks the dollar's rate of, against the real PostgreSQL test database, with
 * {@link ExchangeRateDownloadService} replaced by a stand-in ({@code @MockitoBean}): its own tests are in
 * {@code exchangerates}; here only the days it is given. Made-up holdings and trades, one stock in dollars and one in
 * Hong Kong dollars — which the Bank of Israel has no rate for.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({SettlementDayRatesService.class, FlexReportStorageService.class, PortfolioSyncService.class})
class SettlementDayRatesServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final String ACCOUNT = "U7654321";
    private static final int DOLLAR_STOCK_CON_ID = 8101;
    private static final int HONG_KONG_STOCK_CON_ID = 8102;

    @Autowired
    private SettlementDayRatesService settlementDayRatesService;

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

    @MockitoBean
    private ExchangeRateDownloadService exchangeRateDownloadService;

    /** A buy's rate is a lot's cost in shekels, a fee's a tax shield: both are asked for, each day once. */
    @Test
    void theFlexTradesAndCashTransactionsInDollarsGiveTheirOwnSettlementDays() {
        LocalDate settlementDayOfTheBuy = LocalDate.of(2026, 3, 12);
        LocalDate settlementDayOfTheSale = LocalDate.of(2026, 6, 2);
        LocalDate settlementDayOfTheFee = LocalDate.of(2026, 3, 31);
        flexReportStorageService.storeReport(new FlexReport(
                new FlexReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 13)),
                List.of(
                        flexTradeOf("1001", DOLLAR_STOCK_CON_ID, "USD", settlementDayOfTheBuy),
                        flexTradeOf("1002", DOLLAR_STOCK_CON_ID, "USD", settlementDayOfTheSale),
                        flexTradeOf("1003", DOLLAR_STOCK_CON_ID, "USD", settlementDayOfTheSale),
                        flexTradeOf("1004", HONG_KONG_STOCK_CON_ID, "HKD", LocalDate.of(2026, 4, 8))),
                List.of(dollarFeeOf("2001", settlementDayOfTheFee)), List.of()), Instant.now());

        settlementDayRatesService.downloadMissingDollarRates(TODAY);

        verify(exchangeRateDownloadService).downloadMissingRates("USD",
                Set.of(settlementDayOfTheBuy, settlementDayOfTheSale, settlementDayOfTheFee), TODAY);
    }

    /** A typed trade has no settlement day of its own: it is estimated — a business day after it, two before 2024-05-28. */
    @Test
    void theTypedTradesInDollarsGiveTheirEstimatedSettlementDays() {
        syncTwoHoldings();
        LocalDate fridayBuyOfAHolding = LocalDate.of(2026, 3, 20);
        LocalDate thursdaySaleOfAManualPosition = LocalDate.of(2023, 6, 1);
        typeInATradeOfAHolding(DOLLAR_STOCK_CON_ID, fridayBuyOfAHolding);
        typeInATradeOfAHolding(HONG_KONG_STOCK_CON_ID, LocalDate.of(2026, 4, 7));
        typeInATradeOfAManualPositionInDollars(thursdaySaleOfAManualPosition);

        settlementDayRatesService.downloadMissingDollarRates(TODAY);

        LocalDate mondayAfterTheBuy = LocalDate.of(2026, 3, 23);
        LocalDate mondayAfterTheSale = LocalDate.of(2023, 6, 5);
        verify(exchangeRateDownloadService)
                .downloadMissingRates("USD", Set.of(mondayAfterTheBuy, mondayAfterTheSale), TODAY);
    }

    private FlexReportTrade flexTradeOf(String ibTradeId, int conId, String currency, LocalDate settleDate) {
        LocalDate tradeDate = settleDate.minusDays(1);
        return new FlexReportTrade(ibTradeId, "9" + ibTradeId, conId, "ABCD", "STK", currency, tradeDate,
                tradeDate.atTime(10, 30), settleDate, "BUY", BigDecimal.TEN, new BigDecimal("100"),
                new BigDecimal("-1"), currency, new BigDecimal("-1001"), "ExchTrade", BigDecimal.ZERO);
    }

    /** IB's market data fee: no stock, a negative amount. */
    private FlexReportCashTransaction dollarFeeOf(String ibTransactionId, LocalDate settleDate) {
        return new FlexReportCashTransaction(ibTransactionId, "Other Fees", null, null, "USD", new BigDecimal("-1.50"),
                settleDate.atTime(20, 0), settleDate, "Market data fee");
    }

    private void syncTwoHoldings() {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, Instant.parse("2026-11-15T21:40:00Z"), 50_000.0,
                10_000.0, List.of(
                new Holding("ABCD", DOLLAR_STOCK_CON_ID, "STK", "USD", 10, 100.0, 110.0, 1_100.0, 100.0, 0.0, ACCOUNT),
                new Holding("700", HONG_KONG_STOCK_CON_ID, "STK", "HKD", 100, 400.0, 420.0, 42_000.0, 400.0, 0.0,
                        ACCOUNT))));
    }

    private void typeInATradeOfAHolding(int conId, LocalDate tradeDate) {
        HoldingEntity holding = holdingRepository.findByAccountAndConId(ACCOUNT, conId).orElseThrow();
        entityManager.persist(new TradeEntity(holding, investorRepository.accountOwner(), tradeDate, TradeSide.BUY,
                BigDecimal.TEN, new BigDecimal("100"), null, new BigDecimal("5")));
    }

    private void typeInATradeOfAManualPositionInDollars(LocalDate tradeDate) {
        ManualPositionEntity manualPosition = new ManualPositionEntity("WXYZ", "USD", null, null);
        entityManager.persist(manualPosition);
        entityManager.persist(new TradeEntity(manualPosition, investorRepository.accountOwner(), tradeDate,
                TradeSide.SELL, BigDecimal.TEN, new BigDecimal("50"), null, new BigDecimal("5")));
        entityManager.flush();
    }
}
