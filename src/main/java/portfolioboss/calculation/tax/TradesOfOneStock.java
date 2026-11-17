package portfolioboss.calculation.tax;

import portfolioboss.model.tax.SaleNotComputedReason;

import java.util.List;

/**
 * Every trade of one stock, of the account or of one investor, in the order they were made — what FIFO goes over
 * ({@link FifoSalesCalculator}). Each source groups and orders its own trades: the Flex executions by IB's time, the typed
 * trades by their day.
 *
 * @param conId                        IB's contract id of the stock; {@code null} for a manual position, which IB never
 *                                     reported
 * @param tradesInOrder                its buys and sales, the earliest first
 * @param notComputedReasonOfEverySale a reason that holds for every sale of the stock — a corporate action this code
 *                                     can't follow, say —; {@code null} when there is none
 */
public record TradesOfOneStock(Integer conId, List<TaxCalculationTrade> tradesInOrder,
                        SaleNotComputedReason notComputedReasonOfEverySale) {
}
