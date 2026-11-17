package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * One sale, with the buys FIFO took its shares from ({@link FifoSalesCalculator}).
 *
 * @param sale                      the sale itself
 * @param sharesSoldFromEachBuy     the shares taken from each buy, the earliest buy first
 * @param quantitySoldBeyondTheBuys the shares sold beyond every buy still held at that moment: 0, unless a buy, a split
 *                                  or a transfer is missing from the trades
 * @param costAtTheAverageCost      what the same shares cost the way the Closed page counts — at the average cost of the
 *                                  shares held at that moment, the buys' commissions included —, in the trade's currency
 */
public record FifoSale(TaxCalculationTrade sale, List<SharesSoldFromOneBuy> sharesSoldFromEachBuy,
                BigDecimal quantitySoldBeyondTheBuys, BigDecimal costAtTheAverageCost) {

    /** Closer than a cent of the trade's currency, the two costs are the same. */
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    protected boolean soldMoreThanBought() {
        return quantitySoldBeyondTheBuys.signum() > 0;
    }

    /**
     * What the shares taken from the buys gained, in the trade's currency, every commission counted — the figure IB's own
     * FIFO gives for the sale ({@link OurFifoAgainstIbsFifo}). Without the shares sold beyond the buys, when there are any.
     */
    protected BigDecimal gainOfTheSharesSoldFromBuys() {
        return sharesSoldFromEachBuy.stream()
                .map(SharesSoldFromOneBuy::gain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** What the shares taken from the buys cost, first in first out, in the trade's currency, the buys' commissions included. */
    protected BigDecimal fifoCost() {
        return sharesSoldFromEachBuy.stream()
                .map(SharesSoldFromOneBuy::cost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Whether the tax's cost of the sale (first in first out, decision 3) is more than a cent from the Closed page's
     * (average cost) — then the page says why the sale's gain differs there. Buy 10 at $10, buy 10 at $20, sell 10: $100
     * first in first out, $150 at average cost.
     */
    public boolean fifoCostDiffersFromTheAverageCost() {
        return fifoCost().subtract(costAtTheAverageCost).abs().compareTo(ONE_CENT) > 0;
    }
}
