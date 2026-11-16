package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.tax.TaxTestData.tradeOf;

/**
 * Our FIFO against IB's own (CAPITAL_GAINS_TAX_TODO.md, session 3.2): IB writes six decimals for each execution, so a
 * difference of more than a cent is a different calculation. Made-up trades: a buy of 10 at $10 with a $5 commission, and
 * a sale of 4 at $15 with $1 — 60 − 1 − (40 + 2) = $17.
 */
class OurFifoAgainstIbsFifoTest {

    private static final List<FifoSale> ACCOUNT_SALES = new FifoSalesCalculator(List.of(
            tradeOf("1", TradeSide.BUY, "10", "10", "5", LocalDate.of(2025, 2, 4)),
            tradeOf("2", TradeSide.SELL, "4", "15", "1", LocalDate.of(2025, 6, 3)))).sales();

    @Test
    void aSaleWithinACentOfIbsGainMatches() {
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo =
                new OurFifoAgainstIbsFifo(ACCOUNT_SALES, Map.of("2", new BigDecimal("17.004321")));

        assertThat(ourFifoAgainstIbsFifo.salesCompared()).hasSize(1);
        assertThat(ourFifoAgainstIbsFifo.salesWhoseGainDiffersFromIbs()).isEmpty();
    }

    @Test
    void aSaleMoreThanACentFromIbsGainIsListedWithBothGains() {
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo =
                new OurFifoAgainstIbsFifo(ACCOUNT_SALES, Map.of("2", new BigDecimal("19.5")));

        assertThat(ourFifoAgainstIbsFifo.salesWhoseGainDiffersFromIbs()).singleElement().satisfies(sale -> {
            assertThat(sale.ourFifoGain()).isEqualByComparingTo("17");
            assertThat(sale.ibsFifoGain()).isEqualByComparingTo("19.5");
            assertThat(sale.difference()).isEqualByComparingTo("-2.5");
        });
    }

    /** A row stored before the field was in the query has no gain of IB's: nothing to compare it with. */
    @Test
    void aSaleWithoutIbsGainIsNotCompared() {
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo = new OurFifoAgainstIbsFifo(ACCOUNT_SALES, Map.of());

        assertThat(ourFifoAgainstIbsFifo.salesCompared()).isEmpty();
        assertThat(ourFifoAgainstIbsFifo.salesWhoseGainDiffersFromIbs()).isEmpty();
    }

    /** The buy itself has a gain of 0 in IB's report, and is no sale. */
    @Test
    void onlySalesAreCompared() {
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo = new OurFifoAgainstIbsFifo(ACCOUNT_SALES,
                Map.of("1", BigDecimal.ZERO, "2", new BigDecimal("17")));

        assertThat(ourFifoAgainstIbsFifo.salesCompared()).extracting(fifoSale -> fifoSale.sale().tradeId())
                .containsExactly("2");
    }
}
