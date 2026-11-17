package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Our FIFO set against IB's own, sale by sale (CAPITAL_GAINS_TAX_TODO.md, session 3.2). For every execution IB writes
 * its own FIFO gain ({@code fifoPnlRealized}, in the trade's currency, commissions included); our FIFO works out the
 * same figure on its own ({@link FifoSale#gainOfTheSharesSoldFromBuys}). When the two agree for every sale, our FIFO is
 * right on the real trades — the buys each sale took, the commissions, the order of the trades, the splits followed —,
 * the Hong Kong sale too, whose tax isn't computed. The shekels side of decision 11 is not checked: IB doesn't have it.
 *
 * @param accountSales            every sale of the account, with the buys our FIFO took its shares from
 * @param ibsFifoGainByIbTradeId  IB's FIFO gain of each execution, by IB's trade id; an execution stored before the
 *                                field was in the query is missing
 */
public record OurFifoAgainstIbsFifo(List<FifoSale> accountSales, Map<String, BigDecimal> ibsFifoGainByIbTradeId) {

    /** IB writes six decimals for each execution: a cent apart is a different calculation, not a rounding. */
    private static final BigDecimal ONE_CENT = new BigDecimal("0.01");

    /** The sales IB reported its FIFO gain for. */
    public List<FifoSale> salesCompared() {
        return accountSales.stream()
                .filter(fifoSale -> ibsFifoGainOf(fifoSale) != null)
                .toList();
    }

    /** The sales whose gain here is more than a cent from IB's, in the order of {@code accountSales}. */
    public List<SaleWhoseGainDiffersFromIbs> salesWhoseGainDiffersFromIbs() {
        return salesCompared().stream()
                .map(fifoSale -> new SaleWhoseGainDiffersFromIbs(fifoSale, ibsFifoGainOf(fifoSale)))
                .filter(sale -> sale.difference().abs().compareTo(ONE_CENT) > 0)
                .toList();
    }

    private BigDecimal ibsFifoGainOf(FifoSale fifoSale) {
        return ibsFifoGainByIbTradeId.get(fifoSale.sale().tradeId());
    }

    /**
     * A sale whose FIFO gain here is not IB's.
     *
     * @param fifoSale    the sale, with the buys our FIFO took its shares from
     * @param ibsFifoGain IB's FIFO gain of it
     */
    public record SaleWhoseGainDiffersFromIbs(FifoSale fifoSale, BigDecimal ibsFifoGain) {

        public BigDecimal ourFifoGain() {
            return fifoSale.gainOfTheSharesSoldFromBuys();
        }

        /** Our gain less IB's. */
        protected BigDecimal difference() {
            return ourFifoGain().subtract(ibsFifoGain);
        }
    }
}
