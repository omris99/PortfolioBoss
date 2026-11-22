package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.calculation.tax.TaxCalculationTrade;
import portfolioboss.calculation.tax.TaxYearCalculator;
import portfolioboss.db.investors.InvestorEntity;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.model.portfolio.TradeSide;
import portfolioboss.sync.PortfolioSyncService;
import portfolioboss.sync.StockSplitDetectionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestDataInTheDatabase.BUY_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.SALE_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.STOCK_CON_ID;

/**
 * The capital gains tax worked out from what is stored, against the real PostgreSQL test database: IB's Flex rows, the
 * Bank of Israel's rates, a holding synced from IB and the trades typed in for another investor — the made-up year of
 * {@link TaxTestDataInTheDatabase}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({CapitalGainsTaxService.class, FlexReportStorageService.class, PortfolioSyncService.class,
        StockSplitDetectionService.class, TaxTestDataInTheDatabase.class})
class CapitalGainsTaxServiceTest {

    @Autowired
    private CapitalGainsTaxService capitalGainsTaxService;

    /** The sync's step that stores the splits of the Flex rows: the tax follows only the splits stored. */
    @Autowired
    private StockSplitDetectionService stockSplitDetectionService;

    @Autowired
    private TaxTestDataInTheDatabase storedData;

    @Test
    void nothingIsWorkedOutBeforeTheFirstSync() {
        assertThat(capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors()).isEmpty();
    }

    @Test
    void theAccountsTaxComesFromTheFlexTradesAndFeesAtTheirSettlementDaysRates() {
        storedData.storeTheAccountsYear();

        CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax =
                capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow();

        TaxYearCalculator accountsYear = capitalGainsTax.accountTax().taxYears().get(0);
        assertThat(accountsYear.year()).isEqualTo(2026);
        assertThat(accountsYear.sales()).singleElement()
                .satisfies(sale -> assertThat(sale.taxableGain()).isEqualByComparingTo("1642.90"));
        assertThat(accountsYear.balance().taxOfTheYearSoFar()).isEqualByComparingTo("401.48");
        assertThat(capitalGainsTax.ourFifoAgainstIbsFifo().salesCompared()).hasSize(1);
        assertThat(capitalGainsTax.ourFifoAgainstIbsFifo().salesWhoseGainDiffersFromIbs()).isEmpty();
    }

    /**
     * Avi bought 4 of the 10 and sold them, each typed in on the day of its Flex twin, whose settlement day it takes: $198
     * — 693.00 ₪ in dollars, 652.90 ₪ in shekels —, a tax of 163.23. The account owner is not an investor of their own.
     */
    @Test
    void anotherInvestorsTaxComesFromTheirTypedTradesAtTheirTwinsSettlementDays() {
        storedData.storeTheAccountsYear();
        InvestorEntity avi = storedData.storeAnInvestor("Avi");
        storedData.typeInATradeOf(avi, BUY_DAY, TradeSide.BUY, "4", "100");
        storedData.typeInATradeOf(avi, SALE_DAY, TradeSide.SELL, "4", "150");

        List<InvestorCapitalGainsTax> otherInvestorsTax =
                capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow().otherInvestorsTax();

        assertThat(otherInvestorsTax).singleElement().satisfies(investorTax -> {
            assertThat(investorTax.investorName()).isEqualTo("Avi");
            assertThat(investorTax.tax().tradesOfEveryStock().get(0).tradesInOrder())
                    .extracting(TaxCalculationTrade::settlementDay)
                    .containsExactly(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 2));
            assertThat(investorTax.tax().taxYears().get(0).balance().taxOfTheYearSoFar())
                    .isEqualByComparingTo("163.23");
        });
    }

    /** No Flex trade of the stock on Thursday 5 March: the settlement day is estimated, a business day later. */
    @Test
    void aTypedTradeWithoutATwinGetsTheEstimatedSettlementDay() {
        storedData.syncTheHolding();
        InvestorEntity avi = storedData.storeAnInvestor("Avi");
        storedData.typeInATradeOf(avi, LocalDate.of(2026, 3, 5), TradeSide.BUY, "4", "100");

        TaxCalculationTrade buy = capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow()
                .otherInvestorsTax().get(0).tax().tradesOfEveryStock().get(0).tradesInOrder().get(0);

        assertThat(buy.settlementDay()).isEqualTo(LocalDate.of(2026, 3, 6));
    }

    /**
     * The account bought 10 at $100, the stock split 2 for 1 in the same contract, and it sold the 20 at $75: IB's $498.
     * Avi bought 4 at $100 before it and sold 8 at $75 after it: his buy counts as 8 at $50, so his sale is computed —
     * the same $198 as selling 4 at $150, a tax of 163.23 —, not one of more shares than he bought.
     */
    @Test
    void anInvestorsTradesHeldThroughAStoredSplitAreTranslated() {
        storedData.syncTheHolding();
        storedData.storeTheRates();
        storedData.storeFlexReport(List.of(
                        storedData.flexTradeOf("1001", STOCK_CON_ID, BUY_DAY.atTime(10, 0), "10", "100",
                                BigDecimal.ZERO),
                        storedData.flexTradeOf("1002", STOCK_CON_ID, SALE_DAY.atTime(11, 0), "-20", "75",
                                new BigDecimal("498"))),
                List.of(new FlexReportCorporateAction("4001", "5001", "FS", STOCK_CON_ID, "ABCD", "STK",
                        LocalDateTime.of(2026, 4, 1, 20, 25), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                        "ABCD SPLIT 2 FOR 1")));
        stockSplitDetectionService.storeNewSplits();
        InvestorEntity avi = storedData.storeAnInvestor("Avi");
        storedData.typeInATradeOf(avi, BUY_DAY, TradeSide.BUY, "4", "100");
        storedData.typeInATradeOf(avi, SALE_DAY, TradeSide.SELL, "8", "75");

        CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax =
                capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow();

        assertThat(capitalGainsTax.accountTax().sales()).singleElement()
                .satisfies(sale -> assertThat(sale.isComputed()).isTrue());
        assertThat(capitalGainsTax.ourFifoAgainstIbsFifo().salesWhoseGainDiffersFromIbs()).isEmpty();
        assertThat(capitalGainsTax.otherInvestorsTax().get(0).tax().taxYears().get(0).balance().taxOfTheYearSoFar())
                .isEqualByComparingTo("163.23");
    }

    /** A split the sync hasn't stored is not followed: the sale of the stock is not computed, never guessed. */
    @Test
    void aSplitNotStoredLeavesTheStocksSalesNotComputed() {
        storedData.syncTheHolding();
        storedData.storeTheRates();
        storedData.storeFlexReport(List.of(
                        storedData.flexTradeOf("1001", STOCK_CON_ID, BUY_DAY.atTime(10, 0), "10", "100",
                                BigDecimal.ZERO),
                        storedData.flexTradeOf("1002", STOCK_CON_ID, SALE_DAY.atTime(11, 0), "-20", "75",
                                new BigDecimal("498"))),
                List.of(new FlexReportCorporateAction("4001", "5001", "FS", STOCK_CON_ID, "ABCD", "STK",
                        LocalDateTime.of(2026, 4, 1, 20, 25), new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO,
                        "ABCD SPLIT 2 FOR 1")));

        CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax =
                capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow();

        assertThat(capitalGainsTax.accountTax().sales()).singleElement()
                .satisfies(sale -> assertThat(sale.isComputed()).isFalse());
    }

    /**
     * Bought 20 at $50 under an old contract, a reverse split of 1 for 2 into the stock's contract, then a sale of the 10
     * at $150: stored and read back, the sale takes the translated buy — 10 at $100 — and is computed, its gain IB's
     * $498.
     */
    @Test
    void aStoredReverseSplitIsFollowed() {
        storedData.syncTheHolding();
        storedData.storeTheRates();
        int oldConId = 8100;
        LocalDateTime splitTime = LocalDateTime.of(2026, 4, 1, 20, 25);
        storedData.storeFlexReport(List.of(
                        storedData.flexTradeOf("1001", oldConId, BUY_DAY.atTime(10, 0), "20", "50", BigDecimal.ZERO),
                        storedData.flexTradeOf("1002", STOCK_CON_ID, SALE_DAY.atTime(11, 0), "-10", "150",
                                new BigDecimal("498"))),
                List.of(
                        new FlexReportCorporateAction("4001", "5001", "RS", oldConId, "ABCD.OLD", "STK", splitTime,
                                new BigDecimal("-20"), BigDecimal.ZERO, BigDecimal.ZERO, "ABCD SPLIT 1 FOR 2"),
                        new FlexReportCorporateAction("4002", "5001", "RS", STOCK_CON_ID, "ABCD", "STK", splitTime,
                                new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO, "ABCD SPLIT 1 FOR 2")));
        stockSplitDetectionService.storeNewSplits();

        CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax =
                capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors().orElseThrow();

        assertThat(capitalGainsTax.accountTax().sales()).singleElement()
                .satisfies(sale -> assertThat(sale.isComputed()).isTrue());
        assertThat(capitalGainsTax.ourFifoAgainstIbsFifo().salesWhoseGainDiffersFromIbs()).isEmpty();
    }
}
