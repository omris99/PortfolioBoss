package portfolioboss.domain;

import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * When a holding's current buy-sell episode started, and how long it has run. Pure computation, no
 * Spring and no database — built from {@link TradeFact}s so it can be unit tested directly.
 *
 * <p>"Episode" matters because a long-term holding is often sold in full and bought again later: the
 * first-ever buy date would then belong to a different, unrelated stretch of ownership. An episode is
 * the run of trades since the position was last at zero; {@link #of} walks the trades in order and
 * starts a fresh episode every time a sell brings the running quantity back to zero.
 *
 * @param firstBuyDate the first buy in the current (most recent) episode, or {@code null} if none
 * @param lastSellDate the last sell in that same episode, or {@code null} if none
 * @param netQuantity  the running quantity after all trades — for a check against IB's own figure
 */
public record HoldingHistory(LocalDate firstBuyDate, LocalDate lastSellDate, BigDecimal netQuantity) {

    /**
     * Below this, a quantity counts as "flat" (zero). Needed because summed {@code BigDecimal}s rarely
     * land on an exact zero, and {@code equals} treats {@code 0} and {@code 0.00} as different values
     * anyway (its scale is part of equality) — {@code compareTo} against a tolerance is the only
     * reliable zero check here.
     */
    private static final BigDecimal ZERO_TOLERANCE = new BigDecimal("0.000001");

    /**
     * Walks the trades in chronological order (a buy before a sell on the same date, so the two never
     * cancel out purely because of entry order) and tracks a running quantity. A buy while flat opens a
     * new episode; a sell while already flat is a data-entry mistake and is ignored, on the assumption
     * the matching buy simply hasn't been entered yet — the server never rejects it.
     */
    public static HoldingHistory of(List<TradeFact> trades) {
        List<TradeFact> tradesInDateOrder = trades.stream()
                .sorted(Comparator.comparing(TradeFact::date).thenComparing(trade -> trade.side() == TradeSide.SELL))
                .toList();

        LocalDate firstBuyDate = null;
        LocalDate lastSellDate = null;
        BigDecimal runningQuantity = BigDecimal.ZERO;

        for (TradeFact trade : tradesInDateOrder) {
            boolean positionIsFlat = runningQuantity.abs().compareTo(ZERO_TOLERANCE) <= 0;
            if (trade.side() == TradeSide.BUY) {
                if (positionIsFlat) {
                    firstBuyDate = trade.date();
                    lastSellDate = null;
                }
                runningQuantity = runningQuantity.add(trade.quantity());
            } else if (!positionIsFlat) {
                runningQuantity = runningQuantity.subtract(trade.quantity());
                lastSellDate = trade.date();
            }
        }
        return new HoldingHistory(firstBuyDate, lastSellDate, runningQuantity);
    }

    /**
     * {@code OPEN} counts to {@code snapshotDate} (the last sync, not the system clock, so the number
     * stays the same between syncs); {@code CLOSED} counts to {@code lastSellDate} — {@code null} if no
     * sell was ever entered for it. {@code null} throughout if there is no buy to start counting from.
     *
     * <p>Never negative: a buy dated after the last sync (entered today while the portfolio is served from an
     * older sync, e.g. with TWS off) counts as 0 days held, not as minus the days since that sync.
     */
    public Long holdingDays(HoldingStatus status, LocalDate snapshotDate) {
        if (firstBuyDate == null) {
            return null;
        }
        LocalDate endDate = status == HoldingStatus.CLOSED ? lastSellDate : snapshotDate;
        if (endDate == null) {
            return null;
        }
        return Math.max(0, ChronoUnit.DAYS.between(firstBuyDate, endDate));
    }
}
