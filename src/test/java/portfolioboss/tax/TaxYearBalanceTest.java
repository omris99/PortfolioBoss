package portfolioboss.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A tax year's balance and its monthly settlements (CAPITAL_GAINS_TAX_TODO.md, decision 5), on the plan's example 2 —
 * made-up figures, one account, one year, each settlement day's tax amount given.
 */
class TaxYearBalanceTest {

    private static final LocalDate AFTER_THE_EXAMPLE = LocalDate.of(2026, 7, 10);

    /**
     * Example 2: tax amounts of 1,000 in March, −250 in April, −1,250 in May and 500 in June. March pays 1,000; April
     * refunds 250; May refunds the 750 still paid and keeps a shield of 500; June uses it up, and nothing moves.
     */
    @Test
    void theMonthsSettleAsInExampleTwo() {
        List<MonthlySettlement> monthlySettlements = exampleTwoUpTo(AFTER_THE_EXAMPLE).monthlySettlements();

        assertThat(monthlySettlements).extracting(MonthlySettlement::entriesOfTheMonth)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(amounts("1000", "-250", "-1250", "500"));
        assertThat(monthlySettlements).extracting(MonthlySettlement::settlementAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(amounts("1000", "-250", "-750", "0"));
        assertThat(monthlySettlements).extracting(MonthlySettlement::shieldLeft)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(amounts("0", "0", "500", "0"));
        assertThat(monthlySettlements).allSatisfy(month -> assertThat(month.settled()).isTrue());
    }

    /** "In the end, paid 0 = 25% × max(0, 4,000 − 1,000 − 5,000 + 2,000)." */
    @Test
    void theYearsTaxIsNeverLessThanZero() {
        TaxYearBalance balance = exampleTwoUpTo(AFTER_THE_EXAMPLE);

        assertThat(balance.taxOfTheYearSoFar()).isEqualByComparingTo("0");
        assertThat(balance.shieldLeft()).isEqualByComparingTo("0");
    }

    @Test
    void aShieldBeyondWhatWasPaidStays() {
        TaxYearBalance balance = balanceOf(AFTER_THE_EXAMPLE,
                LocalDate.of(2026, 2, 3), "-1000",
                LocalDate.of(2026, 8, 4), "500");

        assertThat(balance.taxOfTheYearSoFar()).isEqualByComparingTo("0");
        assertThat(balance.shieldLeft()).isEqualByComparingTo("500");
    }

    /** Two settlement days of one month settle together, at its end. */
    @Test
    void theDaysOfAMonthSettleTogether() {
        TaxYearBalance balance = balanceOf(AFTER_THE_EXAMPLE,
                LocalDate.of(2026, 3, 3), "100",
                LocalDate.of(2026, 3, 24), "80");

        assertThat(balance.monthlySettlements()).singleElement()
                .satisfies(march -> assertThat(march.settlementAmount()).isEqualByComparingTo("180"));
    }

    /** Phoenix settles on the month's last day: a sync on 30 June, June's last day, hasn't seen it settle yet. */
    @Test
    void theMonthOfTheLastSyncIsNotSettledYet() {
        List<MonthlySettlement> monthlySettlements = exampleTwoUpTo(LocalDate.of(2026, 6, 30)).monthlySettlements();

        assertThat(monthlySettlements).extracting(MonthlySettlement::settled).containsExactly(true, true, true, false);
    }

    private TaxYearBalance exampleTwoUpTo(LocalDate lastSyncDay) {
        return balanceOf(lastSyncDay,
                LocalDate.of(2026, 3, 10), "1000",
                LocalDate.of(2026, 4, 14), "-250",
                LocalDate.of(2026, 5, 12), "-1250",
                LocalDate.of(2026, 6, 9), "500");
    }

    /** A settlement day and its tax amount after another: {@code balanceOf(lastSyncDay, day, "1000", day, "-250")}. */
    private TaxYearBalance balanceOf(LocalDate lastSyncDay, Object... daysAndTaxAmounts) {
        Map<LocalDate, BigDecimal> taxAmountBySettlementDay = new TreeMap<>();
        for (int index = 0; index < daysAndTaxAmounts.length; index += 2) {
            taxAmountBySettlementDay.put((LocalDate) daysAndTaxAmounts[index],
                    new BigDecimal((String) daysAndTaxAmounts[index + 1]));
        }
        return new TaxYearBalance(taxAmountBySettlementDay, lastSyncDay);
    }

    private BigDecimal[] amounts(String... amounts) {
        BigDecimal[] decimals = new BigDecimal[amounts.length];
        for (int index = 0; index < amounts.length; index++) {
            decimals[index] = new BigDecimal(amounts[index]);
        }
        return decimals;
    }
}
