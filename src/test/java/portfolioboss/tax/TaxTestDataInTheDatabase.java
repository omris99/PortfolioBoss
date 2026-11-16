package portfolioboss.tax;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.db.TradeEntity;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Made-up data for the tax's database tests, stored in the real PostgreSQL test database through the app's own services:
 * a holding synced from IB, IB's Flex rows, the Bank of Israel's rates and the trades typed in for an investor. Made-up
 * values throughout — a stock ABCD in dollars, an investor Avi. A test imports it ({@code @Import}) and gets it with
 * {@code @Autowired}.
 *
 * <p>The account's year ({@link #storeTheAccountsYear}): it bought 10 at $100 (settled 3 March 2026, at 3.60) with a $1
 * commission and sold them at $150 (settled 2 June, at 3.50) with $1: a gain of $498 — 1,743.00 ₪ in dollars, 1,642.90 ₪
 * in shekels, so 1,642.90 is taxed, 410.73. IB's $10 fee of 1 April (at 3.70) is a shield of 9.25: the year's tax is
 * 401.48.
 */
class TaxTestDataInTheDatabase {

    protected static final String ACCOUNT = "U7654321";
    protected static final Instant SYNCED_AT = Instant.parse("2026-11-15T10:00:00Z");
    protected static final int STOCK_CON_ID = 8101;
    protected static final LocalDate BUY_DAY = LocalDate.of(2026, 3, 2);
    protected static final LocalDate SALE_DAY = LocalDate.of(2026, 6, 1);
    protected static final LocalDate FEE_SETTLEMENT_DAY = LocalDate.of(2026, 4, 1);
    protected static final LocalDate SALE_SETTLEMENT_DAY = LocalDate.of(2026, 6, 2);

    private final PortfolioSyncService portfolioSyncService;
    private final FlexReportStorageService flexReportStorageService;
    private final HoldingRepository holdingRepository;
    private final InvestorRepository investorRepository;
    private final TestEntityManager entityManager;
    private final JdbcTemplate jdbc;

    protected TaxTestDataInTheDatabase(PortfolioSyncService portfolioSyncService,
                                       FlexReportStorageService flexReportStorageService,
                                       HoldingRepository holdingRepository, InvestorRepository investorRepository,
                                       TestEntityManager entityManager, JdbcTemplate jdbc) {
        this.portfolioSyncService = portfolioSyncService;
        this.flexReportStorageService = flexReportStorageService;
        this.holdingRepository = holdingRepository;
        this.investorRepository = investorRepository;
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    /** The holding, the rates, and the account's buy, sale and fee — the year of the class's description. */
    protected void storeTheAccountsYear() {
        syncTheHolding();
        storeTheRates();
        storeTheAccountsBuyAndSaleAt("150");
    }

    protected void syncTheHolding() {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 50_000.0, 10_000.0, List.of(
                new Holding("ABCD", STOCK_CON_ID, "STK", "USD", 10, 100.0, 110.0, 1_100.0, 100.0, 0.0, ACCOUNT))));
    }

    /** The days the made-up trades and fee settle on; the Bank of Israel's stretch is unbroken between them. */
    protected void storeTheRates() {
        insertDollarRate(LocalDate.of(2026, 3, 3), "3.60");
        insertDollarRate(FEE_SETTLEMENT_DAY, "3.70");
        insertDollarRate(SALE_SETTLEMENT_DAY, "3.50");
    }

    /** The buy of 10 at $100 and their sale at {@code salePrice}; stored again, IB's rows replace the stored ones. */
    protected void storeTheAccountsBuyAndSaleAt(String salePrice) {
        storeFlexReport(List.of(
                        flexTradeOf("1001", STOCK_CON_ID, BUY_DAY.atTime(10, 0), "10", "100", BigDecimal.ZERO),
                        flexTradeOf("1002", STOCK_CON_ID, SALE_DAY.atTime(11, 0), "-10", salePrice,
                                new BigDecimal("498"))),
                List.of());
    }

    /** The trades and corporate actions given, and IB's $10 fee of 1 April. */
    protected void storeFlexReport(List<FlexReportTrade> trades, List<FlexReportCorporateAction> corporateActions) {
        FlexReportCashTransaction fee = new FlexReportCashTransaction("3001", "Other Fees", null, null, "USD",
                new BigDecimal("-10"), LocalDateTime.of(2026, 4, 1, 17, 0), FEE_SETTLEMENT_DAY,
                "MARKETDATA FOR APR 2026");
        flexReportStorageService.storeReport(new FlexReport(
                new FlexReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 13)), trades, List.of(fee),
                corporateActions), SYNCED_AT);
    }

    /** An execution with IB's signs and a $1 commission, settled the next day. */
    protected FlexReportTrade flexTradeOf(String ibTradeId, int conId, LocalDateTime tradeTime, String signedQuantity,
                                          String price, BigDecimal ibsFifoGain) {
        BigDecimal quantity = new BigDecimal(signedQuantity);
        return new FlexReportTrade(ibTradeId, "2" + ibTradeId, conId, "ABCD", "STK", "USD", tradeTime.toLocalDate(),
                tradeTime, tradeTime.toLocalDate().plusDays(1), quantity.signum() < 0 ? "SELL" : "BUY", quantity,
                new BigDecimal(price), new BigDecimal("-1"), "USD", BigDecimal.ZERO, "ExchTrade", ibsFifoGain);
    }

    protected InvestorEntity storeAnInvestor(String name) {
        InvestorEntity investor = new InvestorEntity(name);
        entityManager.persist(investor);
        return investor;
    }

    /** With a $1 commission; the persistence context is emptied, so that the holding is read again with its trades. */
    protected void typeInATradeOf(InvestorEntity investor, LocalDate tradeDate, TradeSide side, String quantity,
                                  String price) {
        HoldingEntity holding = holdingRepository.findByAccountAndConId(ACCOUNT, STOCK_CON_ID).orElseThrow();
        entityManager.persist(new TradeEntity(holding, investorRepository.findById(investor.id()).orElseThrow(),
                tradeDate, side, new BigDecimal(quantity), new BigDecimal(price), null, BigDecimal.ONE));
        entityManager.flush();
        entityManager.clear();
    }

    private void insertDollarRate(LocalDate rateDate, String rate) {
        jdbc.update("insert into exchange_rate (currency, rate_date, rate) values ('USD', ?, ?)", rateDate,
                new BigDecimal(rate));
    }
}
