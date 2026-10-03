package portfolioboss.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A position bought and then sold back to zero: a finished position period of {@link HoldingHistory}. It keeps only the raw
 * totals; the averages, the realized P&amp;L and its percentage are derived here, so they are written once.
 *
 * <p>Example: 10 shares bought at 150 and later sold at 180 are {@code quantity} 10, {@code buyCost} 1,500 and
 * {@code sellProceeds} 1,800 — a realized P&amp;L of +300, or +20%.
 *
 * @param openDate     the buy that opened the position
 * @param closeDate    the sell that brought it back to zero
 * @param quantity     every share bought in between
 * @param buyCost      quantity × price over every buy, or {@code null} if any buy has no price entered
 * @param sellProceeds quantity × price over every sell, or {@code null} if any sell has no price entered
 */
public record ClosedPosition(LocalDate openDate, LocalDate closeDate, BigDecimal quantity, BigDecimal buyCost,
                             BigDecimal sellProceeds) {

    /** 16 significant digits: plenty for a price or a percentage, and a stop for the endless decimals of 1/3. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public long holdingDays() {
        return ChronoUnit.DAYS.between(openDate, closeDate);
    }

    public BigDecimal averageBuyPrice() {
        return perShare(buyCost);
    }

    /**
     * Over the shares bought, which is as many as were sold — unless more were sold than bought, a data-entry mistake
     * the quantity warning ({@link HoldingHistory#warnings}) already points at.
     */
    public BigDecimal averageSellPrice() {
        return perShare(sellProceeds);
    }

    /** {@code null} unless every buy and every sell has a price: a missing one is never guessed. */
    public BigDecimal realizedPnl() {
        if (buyCost == null || sellProceeds == null) {
            return null;
        }
        return sellProceeds.subtract(buyCost);
    }

    /** Of the buy cost; {@code null} without a realized P&amp;L, or when nothing was paid to measure it against. */
    public BigDecimal realizedPnlPercent() {
        BigDecimal realizedPnl = realizedPnl();
        if (realizedPnl == null || buyCost.signum() == 0) {
            return null;
        }
        return realizedPnl.multiply(ONE_HUNDRED).divide(buyCost, DIVISION_PRECISION);
    }

    private BigDecimal perShare(BigDecimal total) {
        return total == null ? null : total.divide(quantity, DIVISION_PRECISION);
    }
}
