package portfolioboss.calculation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The shares sold in one position period of {@link HoldingHistory}, at average cost: a period a sell brought back to
 * zero, or one still open that has had a sell already. It keeps only the raw totals; the averages, the realized P&amp;L
 * and its percentage are derived here, so they are written once — for a holding's trades and a manual position's alike.
 *
 * <p>Example: 10 shares bought at 100 and 10 at 200, then 10 sold at 180. The average cost is 150, so this is
 * {@code soldQuantity} 10, {@code soldCost} 1,500, {@code sellProceeds} 1,800 and {@code remainingQuantity} 10 — a
 * realized P&amp;L of +300 before commissions. Selling the other 10 later closes the period, and the same row grows.
 *
 * @param openDate          the buy that opened the period
 * @param closeDate         the period's last sell so far — the one that brought it back to zero, once it is closed
 * @param boughtQuantity    every share bought in the period
 * @param soldQuantity      every share sold in it: no more than were bought, unless a sell was entered too large — a
 *                          data-entry mistake that leaves the realized P&amp;L unknown ({@link #soldMoreThanBought})
 * @param soldCost          what the shares sold cost, each at the average cost of the moment it was sold; {@code null}
 *                          if a buy before one of the sells has no price entered
 * @param sellProceeds      quantity × price over every sell, or {@code null} if a sell has no price entered
 * @param commissions       of every sell, and of the buys the part that went with the shares sold
 * @param remainingQuantity still held: 0 once the period is closed
 * @param tradeIds          the trades of the period, in date order
 */
public record ClosedPosition(LocalDate openDate, LocalDate closeDate, BigDecimal boughtQuantity,
                             BigDecimal soldQuantity, BigDecimal soldCost, BigDecimal sellProceeds,
                             BigDecimal commissions, BigDecimal remainingQuantity, List<Long> tradeIds) {

    /** 16 significant digits: plenty for a price or a percentage, and a stop for the endless decimals of 1/3. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    /** From the first buy of the period to its last sell so far. */
    public long holdingDays() {
        return ChronoUnit.DAYS.between(openDate, closeDate);
    }

    /**
     * The average cost of the shares sold. Over the shares sold that were bought: with more sold than bought, the extra
     * shares have no cost.
     */
    public BigDecimal averageBuyPrice() {
        return perShare(soldCost, soldQuantity.min(boughtQuantity));
    }

    /** Over the shares sold, so it is always a price one of the sells could have had. */
    public BigDecimal averageSellPrice() {
        return perShare(sellProceeds, soldQuantity);
    }

    /**
     * More sold than bought: a sell entered with too large a quantity, or a buy not entered yet. The proceeds of the
     * extra shares have no cost to set against them, so they would count as pure profit.
     */
    public boolean soldMoreThanBought() {
        return soldQuantity.compareTo(boughtQuantity) > 0;
    }

    /**
     * What the sells brought in, less what the shares sold cost and the commissions. {@code null} unless every sell and
     * every buy before it has a price and no more was sold than bought: a missing price is never guessed, and neither
     * is which of the shares sold were really bought.
     */
    public BigDecimal realizedPnl() {
        if (soldCost == null || sellProceeds == null || soldMoreThanBought()) {
            return null;
        }
        return sellProceeds.subtract(soldCost).subtract(commissions);
    }

    /** Of what the shares sold cost; {@code null} without a realized P&amp;L, or when they cost nothing. */
    public BigDecimal realizedPnlPercent() {
        BigDecimal realizedPnl = realizedPnl();
        if (realizedPnl == null || soldCost.signum() == 0) {
            return null;
        }
        return realizedPnl.multiply(ONE_HUNDRED).divide(soldCost, DIVISION_PRECISION);
    }

    /** What to fix, in English and shown as it is; {@code null} when nothing needs checking. */
    public String warning() {
        if (!soldMoreThanBought()) {
            return null;
        }
        return "Sold %s shares but bought %s in this period: check its trades."
                .formatted(HoldingHistory.plainNumber(soldQuantity), HoldingHistory.plainNumber(boughtQuantity));
    }

    private BigDecimal perShare(BigDecimal total, BigDecimal shares) {
        return total == null ? null : total.divide(shares, DIVISION_PRECISION);
    }
}
