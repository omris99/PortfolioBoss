package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.exchangerates.ExchangeRates;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestData.buy;
import static portfolioboss.tax.TaxTestData.dollarRates;
import static portfolioboss.tax.TaxTestData.sale;

/**
 * A sale's taxable gain in shekels (CAPITAL_GAINS_TAX_TODO.md, session 3.3): per buy it took shares from, each at its own
 * settlement day's rate — or why it can't be computed. Made-up trades and rates.
 */
class SaleTaxableGainTest {

    private static final LocalDate FIRST_BUY_SETTLEMENT_DAY = LocalDate.of(2026, 3, 2);
    private static final LocalDate SECOND_BUY_SETTLEMENT_DAY = LocalDate.of(2026, 3, 4);
    private static final LocalDate SALE_SETTLEMENT_DAY = LocalDate.of(2026, 6, 2);

    /**
     * One share of each buy, sold together for $104 at 3.2 (332.80 ₪). The share bought at 3.0 gains 12.80 in dollars
     * and 32.80 in shekels — 12.80; the one bought at 3.5 gains 12.80 in dollars but loses 17.20 in shekels — 0. Taxed
     * as one, the sale would come out 15.60 (25.60 in dollars, 15.60 in shekels).
     */
    @Test
    void theGainIsWorkedOutPerBuyNotOverTheWholeSale() {
        SaleTaxableGain saleTaxableGain = saleTaxableGainOf(List.of(
                        buy("1", "1", "100", FIRST_BUY_SETTLEMENT_DAY),
                        buy("2", "1", "100", SECOND_BUY_SETTLEMENT_DAY),
                        sale("3", "2", "104", SALE_SETTLEMENT_DAY)),
                dollarRates("2026-03-02", "3.0", "2026-03-04", "3.5", "2026-06-02", "3.2"));

        assertThat(saleTaxableGain.isComputed()).isTrue();
        assertThat(saleTaxableGain.taxableGainsOfEachBuy()).extracting(TaxableGainOfSharesFromOneBuy::taxableGain)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("12.80"), BigDecimal.ZERO);
        assertThat(saleTaxableGain.taxableGain()).isEqualByComparingTo("12.80");
    }

    /** No rate of the Bank of Israel on 3 March, a holiday inside the stored stretch: the one of the 2nd (decision 1). */
    @Test
    void aBuySettledOnADayWithoutARateTakesTheLastRateBeforeIt() {
        SaleTaxableGain saleTaxableGain = saleTaxableGainOf(List.of(
                        buy("1", "1", "100", LocalDate.of(2026, 3, 3)),
                        sale("2", "1", "104", SALE_SETTLEMENT_DAY)),
                dollarRates("2026-03-02", "3.6", "2026-03-04", "3.7", "2026-06-02", "3.2"));

        assertThat(saleTaxableGain.taxableGainsOfEachBuy()).singleElement()
                .satisfies(gain -> assertThat(gain.buyRate()).isEqualByComparingTo("3.6"));
    }

    @Test
    void aSaleInACurrencyWithoutRatesIsNotComputed() {
        List<TaxCalculationTrade> tradesInHongKongDollars = List.of(
                tradeInHongKongDollars("1", TradeSide.BUY, FIRST_BUY_SETTLEMENT_DAY),
                tradeInHongKongDollars("2", TradeSide.SELL, SALE_SETTLEMENT_DAY));

        SaleTaxableGain saleTaxableGain = saleTaxableGainOf(tradesInHongKongDollars,
                dollarRates("2026-03-02", "3.6", "2026-06-02", "3.2"));

        assertThat(saleTaxableGain.notComputedReason()).isEqualTo(SaleNotComputedReason.NO_RATE_FOR_THE_CURRENCY);
        assertThat(saleTaxableGain.taxableGain()).isNull();
        assertThat(saleTaxableGain.taxableGainsOfEachBuy()).isEmpty();
    }

    /** The rates stored reach the 1st of June; the sale settles on the 2nd, whose rate isn't published yet. */
    @Test
    void aSaleSettledAfterTheLastStoredRateIsNotComputedYet() {
        SaleTaxableGain saleTaxableGain = saleTaxableGainOf(List.of(
                        buy("1", "1", "100", FIRST_BUY_SETTLEMENT_DAY),
                        sale("2", "1", "104", SALE_SETTLEMENT_DAY)),
                dollarRates("2026-03-02", "3.6", "2026-06-01", "3.2"));

        assertThat(saleTaxableGain.notComputedReason()).isEqualTo(SaleNotComputedReason.RATE_NOT_PUBLISHED_YET);
        assertThat(saleTaxableGain.taxableGain()).isNull();
    }

    @Test
    void aSaleOfMoreThanWasBoughtIsNotComputed() {
        SaleTaxableGain saleTaxableGain = saleTaxableGainOf(List.of(
                        buy("1", "1", "100", FIRST_BUY_SETTLEMENT_DAY),
                        sale("2", "3", "104", SALE_SETTLEMENT_DAY)),
                dollarRates("2026-03-02", "3.6", "2026-06-02", "3.2"));

        assertThat(saleTaxableGain.notComputedReason()).isEqualTo(SaleNotComputedReason.SOLD_MORE_THAN_BOUGHT);
    }

    /** A reason of the whole stock is checked first: the shares the sale took can't be trusted. */
    @Test
    void aReasonThatHoldsForEverySaleOfTheStockComesFirst() {
        FifoSale saleOfMoreThanWasBought = new FifoSalesCalculator(List.of(
                buy("1", "1", "100", FIRST_BUY_SETTLEMENT_DAY),
                sale("2", "3", "104", SALE_SETTLEMENT_DAY))).sales().get(0);

        SaleTaxableGain saleTaxableGain = new SaleTaxableGain(saleOfMoreThanWasBought,
                SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED, dollarRates("2026-03-02", "3.6"));

        assertThat(saleTaxableGain.notComputedReason())
                .isEqualTo(SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED);
    }

    /** The stock's only sale, with no reason holding for every sale of it. */
    private SaleTaxableGain saleTaxableGainOf(List<TaxCalculationTrade> tradesInOrder,
                                              Map<String, ExchangeRates> exchangeRatesByCurrency) {
        FifoSale fifoSale = new FifoSalesCalculator(tradesInOrder).sales().get(0);
        return new SaleTaxableGain(fifoSale, null, exchangeRatesByCurrency);
    }

    private TaxCalculationTrade tradeInHongKongDollars(String tradeId, TradeSide side, LocalDate settlementDay) {
        return new TaxCalculationTrade(tradeId, "1234", "HKD", settlementDay.minusDays(1), settlementDay, side,
                new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO);
    }
}
