package portfolioboss.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestData.entryWithTaxableGainOf;

/**
 * A tax year as calculated, on the plan's example 2 — made-up figures, one account, one year: its taxable gain, and its
 * balance from each entry's tax amount. How a balance settles is {@code TaxYearBalanceTest}'s.
 */
class TaxYearCalculatorTest {

    private static final LocalDate AFTER_THE_EXAMPLE = LocalDate.of(2026, 7, 10);

    /** Gains of 4,000 and 2,000, losses of 1,000 and 5,000: a quarter of each is that day's tax amount. */
    @Test
    void theBalanceIsOfEachEntrysTaxAmount() {
        List<MonthlySettlement> monthlySettlements = exampleTwo().balance().monthlySettlements();

        assertThat(monthlySettlements).extracting(MonthlySettlement::entriesOfTheMonth)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("-250"), new BigDecimal("-1250"),
                        new BigDecimal("500"));
    }

    /** "In the end, paid 0 = 25% × max(0, 4,000 − 1,000 − 5,000 + 2,000)." */
    @Test
    void theYearsTaxIsAQuarterOfItsNetTaxableGainNeverLessThanZero() {
        TaxYearCalculator taxYear = exampleTwo();

        assertThat(taxYear.taxableGain()).isEqualByComparingTo("0");
        assertThat(taxYear.balance().taxOfTheYearSoFar()).isEqualByComparingTo("0");
    }

    @Test
    void theSalesAreEveryEntrysInOrder() {
        assertThat(exampleTwo().sales()).extracting(sale -> sale.sale().settlementDay())
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 14), LocalDate.of(2026, 5, 12),
                        LocalDate.of(2026, 6, 9));
    }

    private TaxYearCalculator exampleTwo() {
        return new TaxYearCalculator(2026, List.of(
                entryWithTaxableGainOf("4000", LocalDate.of(2026, 3, 10)),
                entryWithTaxableGainOf("-1000", LocalDate.of(2026, 4, 14)),
                entryWithTaxableGainOf("-5000", LocalDate.of(2026, 5, 12)),
                entryWithTaxableGainOf("2000", LocalDate.of(2026, 6, 9))), AFTER_THE_EXAMPLE);
    }
}
