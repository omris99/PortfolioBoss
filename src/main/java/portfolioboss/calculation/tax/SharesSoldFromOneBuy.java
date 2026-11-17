package portfolioboss.calculation.tax;

import java.math.BigDecimal;

/**
 * The part of a sale that came from one buy (CAPITAL_GAINS_TAX_TODO.md, decision 3): FIFO takes a sale's shares from the
 * earliest buys still held, so a sale of 15 shares after buys of 10 and 10 is two parts — 10 shares of the first buy and
 * 5 of the second. Each part keeps its own buy, whose settlement day's rate is its cost in shekels (decision 11).
 *
 * @param buy      the buy the shares came from
 * @param quantity how many of the sale's shares came from it
 * @param cost     what those shares cost, in the trade's currency: the quantity at the buy's price, and the same part of
 *                 the buy's commission
 * @param proceeds what they brought, in the trade's currency: the quantity at the sale's price, less the same part of the
 *                 sale's commission
 */
public record SharesSoldFromOneBuy(TaxCalculationTrade buy, BigDecimal quantity, BigDecimal cost, BigDecimal proceeds) {

    /** {@code proceeds − cost}, in the trade's currency: negative for a loss. */
    protected BigDecimal gain() {
        return proceeds.subtract(cost);
    }
}
