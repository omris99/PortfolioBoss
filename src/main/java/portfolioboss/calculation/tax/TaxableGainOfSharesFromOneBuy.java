package portfolioboss.calculation.tax;

import java.math.BigDecimal;

/**
 * The taxable gain of the shares a sale took from one buy, as Phoenix computes it (CAPITAL_GAINS_TAX_TODO.md, decision
 * 11): the gain is worked out two ways —
 * <ul>
 *     <li>in dollars: the gain in dollars, at the sale's rate;</li>
 *     <li>in shekels: the proceeds at the sale's rate, less the cost at the buy's rate —</li>
 * </ul>
 * and the one closer to zero is taxed: both a gain, the smaller gain; both a loss, the smaller loss; a gain one way and a
 * loss the other, 0 — no tax and no shield. Each rate is of the trade's settlement day (decision 1).
 *
 * <p>Example (decision 11): bought for $100 at 3.5 (350 ₪), sold for $110 at 3.2 (352 ₪) — in dollars +$10 × 3.2 = 32,
 * in shekels +2: the taxable gain is 2.
 *
 * <p>Per buy, never over the whole sale: a sale from two buys taxed as one comes out about 17% lower than what Phoenix
 * withheld (decision 11's source).
 *
 * @param sharesSold the shares the sale took from the buy, with their cost and proceeds in the trade's currency
 * @param buyRate    shekels for one unit of the trade's currency on the buy's settlement day
 * @param saleRate   the same, on the sale's settlement day
 */
public record TaxableGainOfSharesFromOneBuy(SharesSoldFromOneBuy sharesSold, BigDecimal buyRate, BigDecimal saleRate) {

    /** The gain in the trade's currency — dollars, the only one the Bank of Israel has rates for —, at the sale's rate. */
    public BigDecimal gainInDollarsAtTheSaleRate() {
        return sharesSold.gain().multiply(saleRate);
    }

    /** The proceeds in shekels at the sale's rate, less the cost in shekels at the buy's rate. */
    public BigDecimal gainInShekels() {
        return sharesSold.proceeds().multiply(saleRate).subtract(sharesSold.cost().multiply(buyRate));
    }

    /** In shekels: the one of the two ways closer to zero, or 0 when one is a gain and the other a loss. */
    public BigDecimal taxableGain() {
        BigDecimal gainInDollarsAtTheSaleRate = gainInDollarsAtTheSaleRate();
        BigDecimal gainInShekels = gainInShekels();
        if (gainInDollarsAtTheSaleRate.signum() != gainInShekels.signum()) {
            return BigDecimal.ZERO;
        }
        return gainInDollarsAtTheSaleRate.abs().compareTo(gainInShekels.abs()) <= 0
                ? gainInDollarsAtTheSaleRate
                : gainInShekels;
    }
}
