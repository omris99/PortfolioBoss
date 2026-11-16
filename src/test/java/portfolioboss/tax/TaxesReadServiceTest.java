package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.model.TradeSide;
import portfolioboss.tax.response.MonthlySettlementResponse;
import portfolioboss.tax.response.SaleResponse;
import portfolioboss.tax.response.SettlementDayResponse;
import portfolioboss.tax.response.TaxYearResponse;
import portfolioboss.tax.response.TaxesResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestDataInTheDatabase.BUY_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.FEE_SETTLEMENT_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.SALE_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.SALE_SETTLEMENT_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.SYNCED_AT;

/**
 * {@code GET /api/taxes} put together from what is stored, against the real PostgreSQL test database: the made-up year
 * of {@link TaxTestDataInTheDatabase} — a fee day calculated as −9.25 and a sale day as 410.73 — and the confirmations.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TaxesReadService.class, CapitalGainsTaxService.class, FlexReportStorageService.class,
        PortfolioSyncService.class, TaxTestDataInTheDatabase.class})
class TaxesReadServiceTest {

    @Autowired
    private TaxesReadService taxesReadService;

    @Autowired
    private TaxConfirmationRepository taxConfirmationRepository;

    @Autowired
    private TaxTestDataInTheDatabase storedData;

    @Test
    void nothingBeforeTheFirstSync() {
        assertThat(taxesReadService.taxes()).isEmpty();
    }

    @Test
    void theAccountsDaysWaitWithTheirCalculatedAmounts() {
        storedData.storeTheAccountsYear();

        TaxesResponse taxes = taxesReadService.taxes().orElseThrow();

        assertThat(taxes.lastSyncDay()).isEqualTo(LocalDate.ofInstant(SYNCED_AT, ZoneId.systemDefault()));
        TaxYearResponse accountsYear = taxes.accountTaxYears().get(0);
        assertThat(accountsYear.year()).isEqualTo(2026);
        assertThat(accountsYear.taxOfTheYearSoFar()).isEqualByComparingTo("401.48");
        assertThat(accountsYear.settlementDays()).extracting(SettlementDayResponse::settlementDay)
                .containsExactly(FEE_SETTLEMENT_DAY, SALE_SETTLEMENT_DAY);
        assertThat(accountsYear.settlementDays()).extracting(SettlementDayResponse::computedTaxAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("-9.25"), new BigDecimal("410.73"));
        assertThat(accountsYear.settlementDays()).allSatisfy(day -> assertThat(day.confirmation()).isNull());
    }

    /** April's fee is a shield that stays — nothing paid yet to refund —; June pays the year's 401.48. */
    @Test
    void theMonthsSettleAndTheFeeComesWithItsRate() {
        storedData.storeTheAccountsYear();

        TaxYearResponse accountsYear = taxesReadService.taxes().orElseThrow().accountTaxYears().get(0);

        assertThat(accountsYear.monthlySettlements()).extracting(MonthlySettlementResponse::month)
                .containsExactly(YearMonth.of(2026, 4), YearMonth.of(2026, 6));
        assertThat(accountsYear.monthlySettlements()).extracting(MonthlySettlementResponse::settlementAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0"), new BigDecimal("401.48"));
        assertThat(accountsYear.settlementDays().get(0).expenses()).singleElement().satisfies(fee -> {
            assertThat(fee.rate()).isEqualByComparingTo("3.70");
            assertThat(fee.amountInShekels()).isEqualByComparingTo("-37.00");
        });
    }

    /**
     * The sale took its 10 shares from the one buy: a cost of $1,001 and proceeds of $1,499 — 1,743.00 ₪ in dollars at
     * 3.50, 1,642.90 ₪ in shekels —, so 1,642.90 is taxed.
     */
    @Test
    void aSaleComesWithTheSharesItTookFromEachBuyAndBothWaysOfItsGain() {
        storedData.storeTheAccountsYear();

        SaleResponse sale = taxesReadService.taxes().orElseThrow().accountTaxYears().get(0).settlementDays().get(1)
                .sales().get(0);

        assertThat(sale.tradeId()).isEqualTo("1002");
        assertThat(sale.notComputedReason()).isNull();
        assertThat(sale.taxableGain()).isEqualByComparingTo("1642.90");
        assertThat(sale.sharesSoldFromEachBuy()).singleElement().satisfies(sharesSold -> {
            assertThat(sharesSold.buyTradeId()).isEqualTo("1001");
            assertThat(sharesSold.buySettlementDay()).isEqualTo(LocalDate.of(2026, 3, 3));
            assertThat(sharesSold.cost()).isEqualByComparingTo("1001");
            assertThat(sharesSold.proceeds()).isEqualByComparingTo("1499");
            assertThat(sharesSold.buyRate()).isEqualByComparingTo("3.60");
            assertThat(sharesSold.saleRate()).isEqualByComparingTo("3.50");
            assertThat(sharesSold.gainInDollarsAtTheSaleRate()).isEqualByComparingTo("1743.00");
            assertThat(sharesSold.gainInShekels()).isEqualByComparingTo("1642.90");
            assertThat(sharesSold.taxableGain()).isEqualByComparingTo("1642.90");
        });
    }

    /** June confirmed as 400.00: the year's tax is 400.00 − 9.25, and the day still shows what was calculated. */
    @Test
    void aConfirmedAmountCountsInTheYearInPlaceOfTheCalculatedOne() {
        storedData.storeTheAccountsYear();
        confirm(SALE_SETTLEMENT_DAY, "400.00", "as in Phoenix's report");

        TaxYearResponse accountsYear = taxesReadService.taxes().orElseThrow().accountTaxYears().get(0);

        SettlementDayResponse saleDay = accountsYear.settlementDays().get(1);
        assertThat(saleDay.computedTaxAmount()).isEqualByComparingTo("410.73");
        assertThat(saleDay.confirmation().taxAmount()).isEqualByComparingTo("400.00");
        assertThat(saleDay.confirmation().note()).isEqualTo("as in Phoenix's report");
        assertThat(accountsYear.settlementDays().get(0).confirmation()).isNull();
        assertThat(accountsYear.taxOfTheYearSoFar()).isEqualByComparingTo("390.75");
        assertThat(accountsYear.monthlySettlements().get(1).settlementAmount()).isEqualByComparingTo("390.75");
    }

    /**
     * IB's report downloaded again with the sale at $160: the day's calculation moves to 498.23, and its confirmed amount
     * — what Phoenix recorded — stays the one that counts.
     */
    @Test
    void aConfirmedDayKeepsItsAmountWhenTheCalculationChanges() {
        storedData.storeTheAccountsYear();
        confirm(SALE_SETTLEMENT_DAY, "410.73", null);
        storedData.storeTheAccountsBuyAndSaleAt("160");

        TaxYearResponse accountsYear = taxesReadService.taxes().orElseThrow().accountTaxYears().get(0);

        SettlementDayResponse saleDay = accountsYear.settlementDays().get(1);
        assertThat(saleDay.computedTaxAmount()).isEqualByComparingTo("498.23");
        assertThat(saleDay.confirmation().taxAmount()).isEqualByComparingTo("410.73");
        assertThat(accountsYear.taxOfTheYearSoFar()).isEqualByComparingTo("401.48");
    }

    /** Avi's 4 shares: a tax of 163.23, from his own trades — shown, never confirmed, even on a confirmed day. */
    @Test
    void anotherInvestorsYearsAreCalculatedAndNeverConfirmed() {
        storedData.storeTheAccountsYear();
        InvestorEntity avi = storedData.storeAnInvestor("Avi");
        storedData.typeInATradeOf(avi, BUY_DAY, TradeSide.BUY, "4", "100");
        storedData.typeInATradeOf(avi, SALE_DAY, TradeSide.SELL, "4", "150");
        confirm(SALE_SETTLEMENT_DAY, "410.73", null);

        TaxesResponse taxes = taxesReadService.taxes().orElseThrow();

        assertThat(taxes.otherInvestorsTax()).singleElement().satisfies(aviTax -> {
            assertThat(aviTax.investorName()).isEqualTo("Avi");
            TaxYearResponse avisYear = aviTax.taxYears().get(0);
            assertThat(avisYear.taxOfTheYearSoFar()).isEqualByComparingTo("163.23");
            assertThat(avisYear.settlementDays()).singleElement()
                    .satisfies(day -> assertThat(day.confirmation()).isNull());
        });
    }

    private void confirm(LocalDate settlementDay, String taxAmount, String note) {
        taxConfirmationRepository.save(new TaxConfirmationEntity(settlementDay, new BigDecimal(taxAmount), note,
                Instant.parse("2026-07-01T08:00:00Z")));
    }
}
