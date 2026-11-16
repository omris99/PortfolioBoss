package portfolioboss.tax.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The shares a sale took from one buy (decision 3), and their taxable gain worked out the two ways of decision 11 — the
 * one closer to zero is taxed. The last five are {@code null} when the sale is not computed.
 *
 * @param buyTradeId                 the buy's id, as {@link SaleResponse#tradeId}
 * @param buyTradeDate               the day of the buy
 * @param buySettlementDay           the day it settled, whose rate its cost in shekels takes
 * @param quantity                   how many of the sale's shares came from it
 * @param cost                       what those shares cost, in the trade's currency, the buy's commission included
 * @param proceeds                   what they brought, in the trade's currency, the sale's commission taken off
 * @param buyRate                    shekels for one unit of the trade's currency on the buy's settlement day
 * @param saleRate                   the same, on the sale's settlement day
 * @param gainInDollarsAtTheSaleRate the gain in dollars, at the sale's rate, in shekels
 * @param gainInShekels              the proceeds at the sale's rate less the cost at the buy's rate, in shekels
 * @param taxableGain                the one of the two closer to zero, or 0 when one is a gain and the other a loss
 */
public record SharesSoldFromOneBuyResponse(String buyTradeId, LocalDate buyTradeDate, LocalDate buySettlementDay,
                                           BigDecimal quantity, BigDecimal cost, BigDecimal proceeds,
                                           BigDecimal buyRate, BigDecimal saleRate,
                                           BigDecimal gainInDollarsAtTheSaleRate, BigDecimal gainInShekels,
                                           BigDecimal taxableGain) {
}
