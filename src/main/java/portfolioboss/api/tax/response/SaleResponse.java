package portfolioboss.api.tax.response;

import portfolioboss.model.tax.SaleNotComputedReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One sale with its taxable gain (decision 11), or why it was not computed.
 *
 * @param tradeId                   IB's {@code tradeID} for one of the account's executions, the {@code trade} row's id
 *                                  for an investor's typed trade
 * @param symbol                    the stock's symbol
 * @param currency                  the trade's currency, of the price, the commission and the parts' cost and proceeds
 * @param tradeDate                 the day of the sale
 * @param settlementDay             the day it settled, whose rate it takes (decision 1)
 * @param quantity                  the shares sold
 * @param price                     the price of one share
 * @param commission                what the broker charged for it
 * @param quantitySoldBeyondTheBuys the shares sold beyond every buy still held: 0, unless a trade is missing
 * @param notComputedReason         {@code null} when the sale is computed
 * @param taxableGain               in shekels, negative for a loss; {@code null} when not computed
 * @param sharesSoldFromEachBuy     the shares FIFO took from each buy, the earliest buy first — there even when the sale
 *                                  is not computed, without their shekels
 * @param fifoCostDiffersFromAverageCost whether the sale's cost first in first out (the tax's, decision 3) is more
 *                                       than a cent from its cost at average cost (the Closed page's) — then the page
 *                                       says why the sale's gain differs there
 */
public record SaleResponse(String tradeId, String symbol, String currency, LocalDate tradeDate, LocalDate settlementDay,
                           BigDecimal quantity, BigDecimal price, BigDecimal commission,
                           BigDecimal quantitySoldBeyondTheBuys, SaleNotComputedReason notComputedReason,
                           BigDecimal taxableGain, List<SharesSoldFromOneBuyResponse> sharesSoldFromEachBuy,
                           boolean fifoCostDiffersFromAverageCost) {
}
