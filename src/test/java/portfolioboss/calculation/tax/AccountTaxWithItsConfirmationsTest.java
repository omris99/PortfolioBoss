package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.calculation.tax.TaxTestData.buy;
import static portfolioboss.calculation.tax.TaxTestData.dollarRatesOfOneForTheYears;
import static portfolioboss.calculation.tax.TaxTestData.sale;
import static portfolioboss.calculation.tax.TaxTestData.stockOf;

/**
 * The account's years with the confirmations (CAPITAL_GAINS_TAX_TODO.md, session 4): each confirmation in the year of its
 * settlement day. Made-up trades at a rate of 1: a gain of 400 in 2025, a tax of 100.
 */
class AccountTaxWithItsConfirmationsTest {

    private static final LocalDate SALE_DAY_OF_2025 = LocalDate.of(2025, 6, 3);

    @Test
    void eachConfirmationGoesToTheYearOfItsSettlementDay() {
        AccountTaxWithItsConfirmations accountTax = accountTaxWith(List.of(confirmationOf(SALE_DAY_OF_2025, "100")));

        List<TaxYearWithItsConfirmations> taxYears = accountTax.taxYears();

        assertThat(taxYears).singleElement().satisfies(taxYear -> {
            assertThat(taxYear.year()).isEqualTo(2025);
            assertThat(taxYear.settlementDays()).singleElement()
                    .satisfies(settlementDay -> assertThat(settlementDay.isConfirmed()).isTrue());
        });
    }

    @Test
    void withoutConfirmationsEveryCalculatedYearWaits() {
        List<TaxYearWithItsConfirmations> taxYears = accountTaxWith(List.of()).taxYears();

        assertThat(taxYears).singleElement().satisfies(taxYear ->
                assertThat(taxYear.settlementDays()).noneMatch(SettlementDayWithItsConfirmation::isConfirmed));
    }

    /** A confirmation in 2026, a year the calculation has no day of: the year is there, and the confirmation in it. */
    @Test
    void aYearWithOnlyAConfirmationIsNotLeftOut() {
        LocalDate dayOf2026 = LocalDate.of(2026, 3, 3);
        AccountTaxWithItsConfirmations accountTax = accountTaxWith(List.of(confirmationOf(dayOf2026, "-5")));

        List<TaxYearWithItsConfirmations> taxYears = accountTax.taxYears();

        assertThat(taxYears).extracting(TaxYearWithItsConfirmations::year).containsExactly(2025, 2026);
        assertThat(taxYears.get(1).settlementDays()).singleElement()
                .satisfies(settlementDay -> assertThat(settlementDay.settlementDay()).isEqualTo(dayOf2026));
        assertThat(taxYears.get(1).recordedBalance().shieldLeft()).isEqualByComparingTo("5");
    }

    private AccountTaxWithItsConfirmations accountTaxWith(List<TaxConfirmation> confirmations) {
        CapitalGainsTaxCalculator calculatedAccountTax = new CapitalGainsTaxCalculator(List.of(stockOf(
                buy("1", "10", "100", LocalDate.of(2025, 2, 4)),
                sale("2", "10", "140", SALE_DAY_OF_2025))), List.of(), dollarRatesOfOneForTheYears(2025, 2026),
                LocalDate.of(2026, 11, 13));
        return new AccountTaxWithItsConfirmations(calculatedAccountTax, confirmations);
    }

    private TaxConfirmation confirmationOf(LocalDate settlementDay, String taxAmount) {
        return new TaxConfirmation(1, settlementDay, new BigDecimal(taxAmount), Instant.parse("2026-07-01T08:00:00Z"),
                null);
    }
}
