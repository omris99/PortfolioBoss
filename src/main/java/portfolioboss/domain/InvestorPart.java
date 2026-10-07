package portfolioboss.domain;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * One investor's part of one holding: how many of its shares are theirs, what those are worth and what they cost — as
 * {@link PositionTrades#partsByInvestor} works it out, the same way as the summary cards (INVESTORS_TODO.md, decision
 * 13). In the holding's currency. A figure that needs something unknown is {@code null}, and so is everything derived
 * from it.
 *
 * <p>Example: 39 NVDA at 180, 24 of them the other investor's, bought at 120. Their part is worth 4,320 and cost 2,880:
 * an unrealized +1,440 (+50%). The account owner's 15 are worth IB's 7,020 less those 4,320.
 *
 * @param sharesValue at IB's market price
 * @param sharesCost  at average cost, with the buy commissions — for the account owner IB's cost less the others'
 */
public record InvestorPart(BigDecimal quantity, BigDecimal sharesValue, BigDecimal sharesCost) {

    /** 16 significant digits, as in {@link ClosedPosition}. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** What their shares are worth less what they cost. */
    public BigDecimal unrealizedPnl() {
        if (sharesValue == null || sharesCost == null) {
            return null;
        }
        return sharesValue.subtract(sharesCost);
    }

    /** Of what their shares cost, like IB's column; {@code null} when the cost is 0. */
    public BigDecimal unrealizedPnlPercent() {
        BigDecimal unrealizedPnl = unrealizedPnl();
        if (unrealizedPnl == null || sharesCost.signum() == 0) {
            return null;
        }
        return unrealizedPnl.multiply(ONE_HUNDRED).divide(sharesCost, DIVISION_PRECISION);
    }
}
