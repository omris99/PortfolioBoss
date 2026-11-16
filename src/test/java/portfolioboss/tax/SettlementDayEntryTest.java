package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.exchangerates.ExchangeRates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestData.buy;
import static portfolioboss.tax.TaxTestData.dollarRates;
import static portfolioboss.tax.TaxTestData.sale;
import static portfolioboss.tax.TaxTestData.saleWithTaxableGainOf;

/**
 * One settlement day's entry (CAPITAL_GAINS_TAX_TODO.md, decision 4): every sale and expense of the day net out into one
 * "tax to pay" or "tax shield" of 25%. Made-up figures.
 */
class SettlementDayEntryTest {

    private static final LocalDate SETTLEMENT_DAY = LocalDate.of(2026, 3, 2);

    /** Decision 4: six short trades of one day were one shield of 25% of the day's net loss. */
    @Test
    void theSalesOfOneDayNetOutIntoOneEntry() {
        List<SaleTaxableGain> sixSales = List.of(
                saleWithTaxableGainOf("100", SETTLEMENT_DAY),
                saleWithTaxableGainOf("-300", SETTLEMENT_DAY),
                saleWithTaxableGainOf("50", SETTLEMENT_DAY),
                saleWithTaxableGainOf("-200", SETTLEMENT_DAY),
                saleWithTaxableGainOf("80", SETTLEMENT_DAY),
                saleWithTaxableGainOf("-30", SETTLEMENT_DAY));

        SettlementDayEntry entry = new SettlementDayEntry(SETTLEMENT_DAY, sixSales, List.of(), Map.of());

        assertThat(entry.taxableGainOfTheDay()).isEqualByComparingTo("-300");
        assertThat(entry.taxAmount()).isEqualByComparingTo("-75.00");
    }

    /** A sale of $40 at 3.5 is 140 ₪; IB's $4 fee of the same day, 14 ₪, comes off it: 126 ₪, a tax of 31.50. */
    @Test
    void aFeeLowersTheDaysTaxableGainAtItsRate() {
        Map<String, ExchangeRates> rates = dollarRates("2026-03-02", "3.5");

        SettlementDayEntry entry = new SettlementDayEntry(SETTLEMENT_DAY, List.of(saleOfFortyDollarsAt(rates)),
                List.of(feeOf("-4", SETTLEMENT_DAY)), rates);

        assertThat(entry.expensesInShekels()).isEqualByComparingTo("-14");
        assertThat(entry.taxableGainOfTheDay()).isEqualByComparingTo("126");
        assertThat(entry.taxAmount()).isEqualByComparingTo("31.50");
    }

    @Test
    void aFeeIbCancelledAddsItBack() {
        Map<String, ExchangeRates> rates = dollarRates("2026-03-02", "3.5");

        SettlementDayEntry entry = new SettlementDayEntry(SETTLEMENT_DAY, List.of(saleOfFortyDollarsAt(rates)),
                List.of(feeOf("-4", SETTLEMENT_DAY), feeOf("4", SETTLEMENT_DAY)), rates);

        assertThat(entry.taxableGainOfTheDay()).isEqualByComparingTo("140");
    }

    /** The rates stored reach 2 March only: a fee of the 3rd waits for its rate, as a sale would. */
    @Test
    void aFeeWithoutItsRateYetIsLeftOut() {
        LocalDate dayAfterTheLastStoredRate = LocalDate.of(2026, 3, 3);

        SettlementDayEntry entry = new SettlementDayEntry(dayAfterTheLastStoredRate, List.of(),
                List.of(feeOf("-4", dayAfterTheLastStoredRate)), dollarRates("2026-03-02", "3.5"));

        assertThat(entry.expensesWithTheirRate()).isEmpty();
        assertThat(entry.taxAmount()).isEqualByComparingTo("0");
    }

    @Test
    void aSaleNotComputedIsLeftOutOfTheEntry() {
        SaleTaxableGain computedSale = saleWithTaxableGainOf("100", SETTLEMENT_DAY);
        SaleTaxableGain saleNotComputed = new SaleTaxableGain(saleWithTaxableGainOf("500", SETTLEMENT_DAY).fifoSale(),
                SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED, Map.of());

        SettlementDayEntry entry =
                new SettlementDayEntry(SETTLEMENT_DAY, List.of(computedSale, saleNotComputed), List.of(), Map.of());

        assertThat(entry.salesNotComputed()).containsExactly(saleNotComputed);
        assertThat(entry.taxableGainOfTheDay()).isEqualByComparingTo("100");
    }

    /** 25% of 10.02 is 2.505, of 10.01 2.5025. */
    @Test
    void theTaxIsRoundedToAgorotHalfUp() {
        assertThat(entryOfOneSale("10.02").taxAmount()).isEqualByComparingTo("2.51");
        assertThat(entryOfOneSale("10.01").taxAmount()).isEqualByComparingTo("2.50");
    }

    private SettlementDayEntry entryOfOneSale(String taxableGain) {
        return new SettlementDayEntry(SETTLEMENT_DAY, List.of(saleWithTaxableGainOf(taxableGain, SETTLEMENT_DAY)),
                List.of(), Map.of());
    }

    /** One share bought for $100 and sold for $140 the same day: $40 both ways. */
    private SaleTaxableGain saleOfFortyDollarsAt(Map<String, ExchangeRates> rates) {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("1", "1", "100", SETTLEMENT_DAY),
                sale("2", "1", "140", SETTLEMENT_DAY))).sales().get(0);
        return new SaleTaxableGain(fifoSale, null, rates);
    }

    private TaxDeductibleExpense feeOf(String amount, LocalDate settlementDay) {
        return new TaxDeductibleExpense("3" + amount, settlementDay, TaxTestData.US_DOLLAR, new BigDecimal(amount),
                "MARKETDATA FOR MAR 2026");
    }
}
