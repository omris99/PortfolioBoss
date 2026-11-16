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
import static portfolioboss.tax.TaxTestData.dollarRatesOfOneForTheYears;
import static portfolioboss.tax.TaxTestData.sale;
import static portfolioboss.tax.TaxTestData.stockOf;

/** What a sync prints of the tax (CAPITAL_GAINS_TAX_TODO.md, session 3.6). Made-up trades at a rate of 1. */
class CapitalGainsTaxConsoleLinesTest {

    private static final LocalDate LAST_SYNC_DAY = LocalDate.of(2026, 11, 13);
    private static final LocalDate SETTLEMENT_DAY = LocalDate.of(2026, 3, 3);
    private static final Map<String, ExchangeRates> RATES_OF_ONE = dollarRatesOfOneForTheYears(2026, 2026);

    /**
     * The account lost $1,200 on one sale and sold a stock in Hong Kong dollars, which has no rate; Avi gained $480. IB's
     * FIFO gain is known for the first sale only.
     */
    @Test
    void aLineForEachYearOfTheAccountAndOfEachInvestorThenTheFifoComparison() {
        TradesOfOneStock lossInDollars = stockOf(
                buy("1", "1", "2000", SETTLEMENT_DAY), sale("2", "1", "800", SETTLEMENT_DAY));
        TradesOfOneStock saleInHongKongDollars = new TradesOfOneStock(2222, List.of(
                tradeInHongKongDollars("3", TradeSide.BUY), tradeInHongKongDollars("4", TradeSide.SELL)), null);
        CapitalGainsTaxCalculator accountTax = new CapitalGainsTaxCalculator(
                List.of(lossInDollars, saleInHongKongDollars), List.of(), RATES_OF_ONE, LAST_SYNC_DAY);
        CapitalGainsTaxCalculator avisTax = new CapitalGainsTaxCalculator(List.of(stockOf(
                buy("5", "1", "100", SETTLEMENT_DAY), sale("6", "1", "580", SETTLEMENT_DAY))), List.of(), RATES_OF_ONE,
                LAST_SYNC_DAY);

        List<String> lines = new CapitalGainsTaxConsoleLines(new CapitalGainsTaxOfTheAccountAndInvestors(accountTax,
                List.of(new InvestorCapitalGainsTax(2L, "Avi", avisTax)),
                ourFifoAgainstIbsFifoOf(accountTax, Map.of("2", new BigDecimal("-1200"))))).lines();

        assertThat(lines).containsExactly(
                "[tax] capital gains tax 2026: 0.00 ILS on a taxable gain of -1,200.00 ILS, a shield of 300.00 ILS left"
                        + " (2 sales; not computed: 1 in a currency without exchange rates)",
                "[tax] capital gains tax of Avi 2026: 120.00 ILS on a taxable gain of 480.00 ILS (1 sale)",
                "[tax] our FIFO against IB's: 1 of 1 sales match, 1 sale without IB's FIFO gain");
    }

    @Test
    void aSaleWhoseGainDiffersFromIbsIsNamedWithBothGains() {
        CapitalGainsTaxCalculator accountTax = new CapitalGainsTaxCalculator(List.of(stockOf(
                buy("1", "1", "100", SETTLEMENT_DAY), sale("2", "1", "150", SETTLEMENT_DAY))), List.of(), RATES_OF_ONE,
                LAST_SYNC_DAY);

        List<String> lines = new CapitalGainsTaxConsoleLines(new CapitalGainsTaxOfTheAccountAndInvestors(accountTax,
                List.of(), ourFifoAgainstIbsFifoOf(accountTax, Map.of("2", new BigDecimal("47.5"))))).lines();

        assertThat(lines).endsWith(
                "[tax] our FIFO against IB's: 0 of 1 sales match",
                "[tax] our FIFO against IB's: ABCD sold on 2026-03-02 (IB trade 2): ours 50.00 USD, IB's 47.50 USD");
    }

    private OurFifoAgainstIbsFifo ourFifoAgainstIbsFifoOf(CapitalGainsTaxCalculator accountTax,
                                                         Map<String, BigDecimal> ibsFifoGainByIbTradeId) {
        return new OurFifoAgainstIbsFifo(accountTax.sales().stream().map(SaleTaxableGain::fifoSale).toList(),
                ibsFifoGainByIbTradeId);
    }

    private TaxCalculationTrade tradeInHongKongDollars(String tradeId, TradeSide side) {
        return new TaxCalculationTrade(tradeId, "1234", "HKD", SETTLEMENT_DAY.minusDays(1), SETTLEMENT_DAY, side,
                new BigDecimal("100"), new BigDecimal("40"), BigDecimal.ZERO);
    }
}
