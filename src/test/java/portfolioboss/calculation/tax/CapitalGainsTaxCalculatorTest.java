package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.exchangerates.ExchangeRates;
import portfolioboss.model.portfolio.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.calculation.tax.TaxTestData.buy;
import static portfolioboss.calculation.tax.TaxTestData.dollarRatesOfOneForTheYears;
import static portfolioboss.calculation.tax.TaxTestData.sale;
import static portfolioboss.calculation.tax.TaxTestData.stockOf;

/**
 * The whole calculation (CAPITAL_GAINS_TAX_TODO.md, session 3.5): the account's and an investor's, the tax years, the
 * expenses. Made-up trades at a rate of 1, so that every gain in dollars is the same in shekels.
 */
class CapitalGainsTaxCalculatorTest {

    private static final LocalDate LAST_SYNC_DAY = LocalDate.of(2026, 11, 13);
    private static final Map<String, ExchangeRates> RATES_OF_ONE = dollarRatesOfOneForTheYears(2024, 2027);

    /**
     * The plan's example 1: the account owner loses 4,000 in February, the other investor gains 10,000 in May, the owner
     * gains 2,000 in August. The account pays 2,000 — its loss lowered the tax on the other investor's gain —; the other
     * investor's own tax is 2,500.
     */
    @Test
    void theAccountNetsTheInvestorsOutAndAnInvestorIsTaxedAlone() {
        TradesOfOneStock ownersLoss = dayTradeWithGain("-4000", LocalDate.of(2026, 2, 3));
        TradesOfOneStock otherInvestorsGain = dayTradeWithGain("10000", LocalDate.of(2026, 5, 5));
        TradesOfOneStock ownersGain = dayTradeWithGain("2000", LocalDate.of(2026, 8, 4));

        TaxYearCalculator accountsYear = taxYearsOf(List.of(ownersLoss, otherInvestorsGain, ownersGain)).get(0);
        TaxYearCalculator otherInvestorsYear = taxYearsOf(List.of(otherInvestorsGain)).get(0);

        assertThat(accountsYear.balance().monthlySettlements()).extracting(MonthlySettlement::settlementAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0"), new BigDecimal("1500"), new BigDecimal("500"));
        assertThat(accountsYear.balance().taxOfTheYearSoFar()).isEqualByComparingTo("2000");
        assertThat(otherInvestorsYear.balance().taxOfTheYearSoFar()).isEqualByComparingTo("2500");
    }

    /**
     * The account's FIFO takes the earliest buy of the stock whoever bought it; the investor's only their own. The owner
     * bought 10 at $10, the other investor 10 at $20 and sold 10 at $25: for the account a gain of 150, for the investor 50.
     */
    @Test
    void theAccountsFifoIsOverEveryInvestorsTradesAndAnInvestorsOverTheirOwn() {
        TaxCalculationTrade ownersBuy = buy("1", "10", "10", LocalDate.of(2026, 1, 6));
        TaxCalculationTrade otherInvestorsBuy = buy("2", "10", "20", LocalDate.of(2026, 3, 3));
        TaxCalculationTrade otherInvestorsSale = sale("3", "10", "25", LocalDate.of(2026, 6, 2));

        TaxYearCalculator accountsYear = taxYearsOf(List.of(stockOf(ownersBuy, otherInvestorsBuy, otherInvestorsSale)))
                .get(0);
        TaxYearCalculator otherInvestorsYear = taxYearsOf(List.of(stockOf(otherInvestorsBuy, otherInvestorsSale)))
                .get(0);

        assertThat(accountsYear.taxableGain()).isEqualByComparingTo("150");
        assertThat(otherInvestorsYear.taxableGain()).isEqualByComparingTo("50");
    }

    /** Decision 4: the tax belongs to each sale's own year, not to the position as a whole. */
    @Test
    void aPartialSaleIn2025AndTheRestIn2026AreTwoTaxYears() {
        List<TaxYearCalculator> taxYears = taxYearsOf(List.of(stockOf(
                buy("1", "10", "100", LocalDate.of(2024, 12, 3)),
                sale("2", "4", "150", LocalDate.of(2025, 6, 3)),
                sale("3", "6", "120", LocalDate.of(2026, 2, 3)))));

        assertThat(taxYears).extracting(TaxYearCalculator::year).containsExactly(2025, 2026);
        assertThat(taxYears.get(0).taxableGain()).isEqualByComparingTo("200");
        assertThat(taxYears.get(1).taxableGain()).isEqualByComparingTo("120");
    }

    /** Decision 16: the year of the settlement day — 31 December 2026 was a Thursday, so its sale settled on 1 January. */
    @Test
    void aSaleOn31DecemberThatSettlesOn1JanuaryBelongsToTheNewYear() {
        TaxCalculationTrade saleOnNewYearsEve = new TaxCalculationTrade("2", "ABCD", "USD", LocalDate.of(2026, 12, 31),
                LocalDate.of(2027, 1, 1), TradeSide.SELL, BigDecimal.ONE, new BigDecimal("150"), BigDecimal.ZERO);

        List<TaxYearCalculator> taxYears = taxYearsOf(List.of(stockOf(
                buy("1", "1", "100", LocalDate.of(2026, 6, 2)), saleOnNewYearsEve)));

        assertThat(taxYears).extracting(TaxYearCalculator::year).containsExactly(2027);
    }

    /** IB's market data fee of a day without a sale is a shield of its own: 25% of $10 at a rate of 1. */
    @Test
    void aFeeOnADayWithoutASaleIsAnEntryOfItsOwn() {
        LocalDate dayOfTheFee = LocalDate.of(2026, 4, 1);
        TaxDeductibleExpense fee = new TaxDeductibleExpense("3001", dayOfTheFee, "USD", new BigDecimal("-10"),
                "MARKETDATA FOR APR 2026");

        List<SettlementDayEntry> entries = new CapitalGainsTaxCalculator(
                List.of(dayTradeWithGain("100", LocalDate.of(2026, 6, 2))), List.of(fee), RATES_OF_ONE, LAST_SYNC_DAY)
                .settlementDayEntries();

        assertThat(entries).extracting(SettlementDayEntry::settlementDay)
                .containsExactly(dayOfTheFee, LocalDate.of(2026, 6, 2));
        assertThat(entries.get(0).taxAmount()).isEqualByComparingTo("-2.50");
    }

    private List<TaxYearCalculator> taxYearsOf(List<TradesOfOneStock> tradesOfEveryStock) {
        return new CapitalGainsTaxCalculator(tradesOfEveryStock, List.of(), RATES_OF_ONE, LAST_SYNC_DAY).taxYears();
    }

    /** One share bought and sold the same day, with that gain in dollars — and shekels, at a rate of 1. */
    private TradesOfOneStock dayTradeWithGain(String gain, LocalDate settlementDay) {
        BigDecimal buyPrice = new BigDecimal("100000");
        return stockOf(
                buy("buy on " + settlementDay, "1", buyPrice.toPlainString(), settlementDay),
                sale("sale on " + settlementDay, "1", buyPrice.add(new BigDecimal(gain)).toPlainString(), settlementDay));
    }
}
