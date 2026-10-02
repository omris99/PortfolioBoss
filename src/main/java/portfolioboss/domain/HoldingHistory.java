package portfolioboss.domain;

import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * When a holding's current buy-sell episode started, how long it has run, and where the trades entered
 * disagree with what IB reports ({@link #warnings}). Pure computation, no Spring and no database — built
 * from {@link TradeFact}s so it can be unit tested directly.
 *
 * <p>"Episode" matters because a long-term holding is often sold in full and bought again later: the
 * first-ever buy date would then belong to a different, unrelated stretch of ownership. An episode is
 * the run of trades since the position was last at zero; {@link #of} walks the trades in order and
 * starts a fresh episode every time a sell brings the running quantity back to zero.
 *
 * @param firstBuyDate the first buy in the current (most recent) episode, or {@code null} if none
 * @param lastSellDate the last sell in that same episode, or {@code null} if none
 * @param netQuantity  buys minus sells over every trade entered, for the check against IB's own figure
 *                     ({@link #warnings}) — including a sell the dates ignore, so that the check still shows it
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
     * How far the trades entered may be from IB's quantity and still count as matching: IB reports fractional
     * shares as a {@code double}, which is rarely the exact decimal that was typed in.
     */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.0001");

    /**
     * Walks the trades in chronological order (a buy before a sell on the same date, so the two never
     * cancel out purely because of entry order) and tracks a running quantity. A buy while flat opens a
     * new episode; a sell while already flat is a data-entry mistake and the dates ignore it, on the
     * assumption the matching buy simply hasn't been entered yet — the server never rejects it, and
     * {@code netQuantity} still counts it, so the check against IB points at it.
     */
    public static HoldingHistory of(List<TradeFact> trades) {
        List<TradeFact> tradesInDateOrder = trades.stream()
                .sorted(Comparator.comparing(TradeFact::date).thenComparing(trade -> trade.side() == TradeSide.SELL))
                .toList();

        LocalDate firstBuyDate = null;
        LocalDate lastSellDate = null;
        BigDecimal runningQuantity = BigDecimal.ZERO;
        BigDecimal netQuantity = BigDecimal.ZERO;

        for (TradeFact trade : tradesInDateOrder) {
            netQuantity = netQuantity.add(trade.signedQuantity());
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
        return new HoldingHistory(firstBuyDate, lastSellDate, netQuantity);
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

    /**
     * Where the trades entered disagree with IB — at most one warning, the one to fix first: no buy entered at
     * all, then a {@code CLOSED} holding whose current episode no sell ends, then a quantity other than IB's.
     * Without that order a holding with no trades would also be a quantity mismatch (0 against IB's figure), the
     * same gap reported twice. It is a list so that other kinds of warning can join later without changing the
     * JSON's shape.
     *
     * @param ibPosition IB's quantity; 0 for a {@code CLOSED} holding, which the sync sets when IB stops reporting it
     */
    public List<HoldingWarning> warnings(HoldingStatus status, double ibPosition) {
        if (firstBuyDate == null) {
            return List.of(new HoldingWarning(HoldingWarningType.NO_TRADES_LOGGED,
                    "No buy entered yet, so the buy date and holding period are unknown."));
        }
        if (status == HoldingStatus.CLOSED && lastSellDate == null) {
            return List.of(new HoldingWarning(HoldingWarningType.CLOSED_WITHOUT_SELL,
                    "IB no longer reports this holding, but no sell closing it was entered."));
        }
        // NaN can't become a BigDecimal (it throws), and there is nothing to compare it with anyway.
        if (!Double.isFinite(ibPosition)) {
            return List.of();
        }
        BigDecimal ibQuantity = BigDecimal.valueOf(ibPosition);
        boolean quantitiesMatch = netQuantity.subtract(ibQuantity).abs().compareTo(QUANTITY_TOLERANCE) <= 0;
        if (quantitiesMatch) {
            return List.of();
        }
        String message = "The trades entered add up to %s shares; IB reports %s."
                .formatted(plainNumber(netQuantity), plainNumber(ibQuantity));
        return List.of(new HoldingWarning(HoldingWarningType.QUANTITY_MISMATCH, message));
    }

    /** "90", "10.5", "-3": without the trailing zeros a {@code NUMERIC(20,6)} column adds ("90.000000"). */
    private String plainNumber(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }
}
