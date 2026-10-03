package portfolioboss.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A position bought and then sold back to zero: a finished position period of {@link HoldingHistory}, or a round trip
 * entered by hand ({@code ManualClosedPositionEntity}). It keeps only the raw totals; the averages, the realized
 * P&amp;L and its percentage are derived here, so they are written once for both.
 *
 * <p>Example: 10 shares bought at 150 and later sold at 180, $5 commission on each order, are {@code quantity} 10,
 * {@code soldQuantity} 10, {@code buyCost} 1,500, {@code sellProceeds} 1,800 and {@code commissions} 10 — a realized
 * P&amp;L of +290, or +19.33%.
 *
 * @param openDate     the buy that opened the position
 * @param closeDate    the sell that brought it back to zero
 * @param quantity     every share bought in between
 * @param soldQuantity every share sold in between: as many as were bought, unless more were sold — a data-entry
 *                     mistake that leaves the realized P&amp;L unknown ({@link #soldMoreThanBought})
 * @param buyCost      quantity × price over every buy, or {@code null} if any buy has no price entered
 * @param sellProceeds quantity × price over every sell, or {@code null} if any sell has no price entered
 * @param commissions  what the broker charged for every buy and sell in between; never {@code null}
 */
public record ClosedPosition(LocalDate openDate, LocalDate closeDate, BigDecimal quantity, BigDecimal soldQuantity,
                             BigDecimal buyCost, BigDecimal sellProceeds, BigDecimal commissions) {

    /** 16 significant digits: plenty for a price or a percentage, and a stop for the endless decimals of 1/3. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public long holdingDays() {
        return ChronoUnit.DAYS.between(openDate, closeDate);
    }

    public BigDecimal averageBuyPrice() {
        return perShare(buyCost, quantity);
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
        return soldQuantity.compareTo(quantity) > 0;
    }

    /**
     * What the sells brought in, less what the buys cost and the commissions. {@code null} unless every buy and every
     * sell has a price and no more was sold than bought: a missing price is never guessed, and neither is which of the
     * shares sold were really bought.
     */
    public BigDecimal realizedPnl() {
        if (buyCost == null || sellProceeds == null || soldMoreThanBought()) {
            return null;
        }
        return sellProceeds.subtract(buyCost).subtract(commissions);
    }

    /** Of the buy cost; {@code null} without a realized P&amp;L, or when nothing was paid to measure it against. */
    public BigDecimal realizedPnlPercent() {
        BigDecimal realizedPnl = realizedPnl();
        if (realizedPnl == null || buyCost.signum() == 0) {
            return null;
        }
        return realizedPnl.multiply(ONE_HUNDRED).divide(buyCost, DIVISION_PRECISION);
    }

    /** What to fix, in English and shown as it is; {@code null} when nothing needs checking. */
    public String warning() {
        if (!soldMoreThanBought()) {
            return null;
        }
        return "Sold %s shares but bought %s in this period: check this holding's trades."
                .formatted(HoldingHistory.plainNumber(soldQuantity), HoldingHistory.plainNumber(quantity));
    }

    private BigDecimal perShare(BigDecimal total, BigDecimal shares) {
        return total == null ? null : total.divide(shares, DIVISION_PRECISION);
    }
}
