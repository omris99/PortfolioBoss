package portfolioboss.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

/**
 * The figures on one investor's summary card: what they have in the IB account and what they made on it, as
 * {@link InvestorSummaryCalculator} works it out. Never stored — derived on every read. It keeps only the raw totals;
 * the profit figures are derived here, like {@link ClosedPosition}'s. Everything is in USD except the realized P&amp;L,
 * which is by currency. A figure that needs something unknown — a trade without a price, a figure IB did not report —
 * is {@code null}, and so is everything derived from it.
 *
 * <p>Example (INVESTORS_TODO.md): deposited 30,000 and bought 24 NVDA at 120, which is now at 180. Cash 27,120, shares
 * worth 4,320, total value 31,440, shares cost 2,880: an unrealized +1,440 (+50%).
 *
 * @param depositsMinusWithdrawals {@code null} for the account owner, who has no deposits — their cash comes from IB
 * @param sharesValue              their shares at IB's market price
 * @param totalValue               cash and shares — for the account owner IB's net liquidation value less the others',
 *                                 so it also holds what IB counts beyond cash and shares (accrued interest, say)
 * @param sharesCost               what their shares cost, at average cost with their buy commissions
 * @param realizedPnlByCurrency    the realized P&amp;L of their closed positions, by currency ({@code "HKD"},
 *                                 {@code "USD"}); a closed position without one is left out, and a warning says so
 * @param warnings                 what to check in what was entered for them
 */
public record InvestorSummary(BigDecimal depositsMinusWithdrawals, BigDecimal cash, BigDecimal sharesValue,
                              BigDecimal totalValue, BigDecimal sharesCost,
                              Map<String, BigDecimal> realizedPnlByCurrency, List<InvestorWarning> warnings) {

    /** The currency of every figure but the realized P&amp;L: the account's, in which IB reports its cash and NAV. */
    protected static final String ACCOUNT_CURRENCY = "USD";

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

    /** Of what their shares cost, like IB's column; {@code null} when they hold nothing. */
    public BigDecimal unrealizedPnlPercent() {
        BigDecimal unrealizedPnl = unrealizedPnl();
        if (unrealizedPnl == null || sharesCost.signum() == 0) {
            return null;
        }
        return unrealizedPnl.multiply(ONE_HUNDRED).divide(sharesCost, DIVISION_PRECISION);
    }

    /** Unrealized and realized, in USD only: a realized P&amp;L in another currency is not added in. */
    public BigDecimal totalPnl() {
        BigDecimal unrealizedPnl = unrealizedPnl();
        if (unrealizedPnl == null) {
            return null;
        }
        return unrealizedPnl.add(realizedPnlByCurrency.getOrDefault(ACCOUNT_CURRENCY, BigDecimal.ZERO));
    }
}
