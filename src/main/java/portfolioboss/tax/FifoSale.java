package portfolioboss.tax;

import java.math.BigDecimal;
import java.util.List;

/**
 * One sale, with the buys FIFO took its shares from ({@link FifoSalesCalculator}).
 *
 * @param sale                      the sale itself
 * @param sharesSoldFromEachBuy     the shares taken from each buy, the earliest buy first
 * @param quantitySoldBeyondTheBuys the shares sold beyond every buy still held at that moment: 0, unless a buy, a split
 *                                  or a transfer is missing from the trades
 */
record FifoSale(TaxCalculationTrade sale, List<SharesSoldFromOneBuy> sharesSoldFromEachBuy,
                BigDecimal quantitySoldBeyondTheBuys) {

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
}
