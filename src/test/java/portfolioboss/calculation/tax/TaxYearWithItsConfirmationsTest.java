package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.calculation.tax.TaxTestData.entryWithTaxableGainOf;

/**
 * A tax year of the account with what the user confirmed (CAPITAL_GAINS_TAX_TODO.md, session 4), on the plan's example 2
 * — made-up figures: tax amounts of 1,000 in March, −250 in April, −1,250 in May and 500 in June.
 */
class TaxYearWithItsConfirmationsTest {

    private static final LocalDate AFTER_THE_EXAMPLE = LocalDate.of(2026, 7, 10);
    private static final LocalDate MARCH_DAY = LocalDate.of(2026, 3, 10);
    private static final LocalDate APRIL_DAY = LocalDate.of(2026, 4, 14);
    private static final LocalDate MAY_DAY = LocalDate.of(2026, 5, 12);
    private static final LocalDate JUNE_DAY = LocalDate.of(2026, 6, 9);

    @Test
    void everyDayComesWithItsConfirmationOrWaits() {
        TaxYearWithItsConfirmations taxYear = exampleTwoWith(List.of(confirmationOf(APRIL_DAY, "-250")));

        assertThat(taxYear.settlementDays()).extracting(SettlementDayWithItsConfirmation::settlementDay)
                .containsExactly(MARCH_DAY, APRIL_DAY, MAY_DAY, JUNE_DAY);
        assertThat(taxYear.settlementDays()).extracting(SettlementDayWithItsConfirmation::isConfirmed)
                .containsExactly(false, true, false, false);
    }

    /**
     * April confirmed as −300 instead of the calculated −250: April refunds 300; May then refunds the 700 still paid and
     * keeps a shield of 550; June's 500 leaves 50 of it. The calculated balance doesn't move.
     */
    @Test
    void theRecordedBalanceTakesTheConfirmedAmountInPlaceOfTheCalculatedOne() {
        TaxYearWithItsConfirmations taxYear = exampleTwoWith(List.of(confirmationOf(APRIL_DAY, "-300")));

        TaxYearBalance recordedBalance = taxYear.recordedBalance();

        assertThat(recordedBalance.monthlySettlements()).extracting(MonthlySettlement::settlementAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("-300"), new BigDecimal("-700"),
                        new BigDecimal("0"));
        assertThat(recordedBalance.shieldLeft()).isEqualByComparingTo("50");
        assertThat(taxYear.taxYear().balance().shieldLeft()).isEqualByComparingTo("0");
    }

    @Test
    void withoutAConfirmationTheRecordedBalanceIsTheCalculatedOne() {
        TaxYearWithItsConfirmations taxYear = exampleTwoWith(List.of());

        assertThat(taxYear.recordedBalance().monthlySettlements())
                .isEqualTo(taxYear.taxYear().balance().monthlySettlements());
    }

    /**
     * A confirmation of 20 April, a day the calculation no longer has: it comes in its place as a day calculated as 0, and
     * its amount counts — April's month adds it.
     */
    @Test
    void aConfirmationWhoseDayIsNoLongerCalculatedIsNotLeftOut() {
        LocalDate dayNoLongerCalculated = LocalDate.of(2026, 4, 20);
        TaxYearWithItsConfirmations taxYear = exampleTwoWith(List.of(confirmationOf(dayNoLongerCalculated, "40")));

        assertThat(taxYear.settlementDays()).extracting(SettlementDayWithItsConfirmation::settlementDay)
                .containsExactly(MARCH_DAY, APRIL_DAY, dayNoLongerCalculated, MAY_DAY, JUNE_DAY);
        SettlementDayWithItsConfirmation dayOfTheConfirmation = taxYear.settlementDays().get(2);
        assertThat(dayOfTheConfirmation.entry().taxAmount()).isEqualByComparingTo("0");
        assertThat(dayOfTheConfirmation.entry().sales()).isEmpty();
        assertThat(taxYear.recordedBalance().monthlySettlements().get(1).entriesOfTheMonth())
                .isEqualByComparingTo("-210");
    }

    private TaxYearWithItsConfirmations exampleTwoWith(List<TaxConfirmation> confirmations) {
        TaxYearCalculator exampleTwo = new TaxYearCalculator(2026, List.of(
                entryWithTaxableGainOf("4000", MARCH_DAY),
                entryWithTaxableGainOf("-1000", APRIL_DAY),
                entryWithTaxableGainOf("-5000", MAY_DAY),
                entryWithTaxableGainOf("2000", JUNE_DAY)), AFTER_THE_EXAMPLE);
        return new TaxYearWithItsConfirmations(exampleTwo, confirmations);
    }

    private TaxConfirmation confirmationOf(LocalDate settlementDay, String taxAmount) {
        return new TaxConfirmation(1, settlementDay, new BigDecimal(taxAmount), Instant.parse("2026-07-01T08:00:00Z"),
                null);
    }
}
