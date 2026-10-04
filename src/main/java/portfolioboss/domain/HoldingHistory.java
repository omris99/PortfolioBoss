package portfolioboss.domain;

import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * When a holding's current position period started, how long it has run, which earlier periods were closed
 * ({@link ClosedPosition}), and where the trades entered disagree with what IB reports ({@link #warnings}). Pure
 * computation, no Spring and no database — built from {@link TradeFact}s so it can be unit tested directly.
 *
 * <p>A position period is one stretch of owning the stock: from a buy while none is held to the sell that brings the
 * quantity back to zero. It matters because a long-term holding is often sold in full and bought again later: the
 * first-ever buy date would then belong to a different, unrelated stretch of ownership. Bought in 2024, sold in full
 * in 2025 and bought again in 2026 is two periods: a closed one (2024–2025) and the current, open one (2026).
 *
 * @param firstBuyDate    the first buy in the current (most recent) position period, or {@code null} if none
 * @param lastSellDate    the last sell in that same period, or {@code null} if none
 * @param netQuantity     buys minus sells over every trade entered, for the check against IB's own figure
 *                        ({@link #warnings}) — including a sell the dates ignore, so that the check still shows it
 * @param closedPositions the shares sold in every position period that has a sell, oldest first: each one a sell
 *                        brought back to zero, and the current one too if it has had a sell — at average cost
 * @param heldCost        what the shares still held in the current position period cost, at average cost and with the
 *                        part of the buy commissions that stays with them — as IB's average cost counts it. 0 when
 *                        nothing is held; {@code null} if a buy still held has no price entered
 */
public record HoldingHistory(LocalDate firstBuyDate, LocalDate lastSellDate, BigDecimal netQuantity,
                             List<ClosedPosition> closedPositions, BigDecimal heldCost) {

    /**
     * Below this, a quantity counts as "flat" (zero). Needed because summed {@code BigDecimal}s rarely
     * land on an exact zero, and {@code equals} treats {@code 0} and {@code 0.00} as different values
     * anyway (its scale is part of equality) — {@code compareTo} against a tolerance is the only
     * reliable zero check here.
     */
    private static final BigDecimal ZERO_TOLERANCE = new BigDecimal("0.000001");

    /**
     * How far the trades entered may be from IB's quantity and still count as matching: IB reports fractional
     * shares as a {@code double}, which is rarely the exact decimal that was typed in. Also used by
     * {@link InvestorSummaryCalculator}.
     */
    protected static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.0001");

    /** 16 significant digits, as in {@link ClosedPosition}: the part of the cost that leaves with a partial sell. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    /**
     * Sorts the trades chronologically (a buy before a sell on the same date, so the two never cancel out purely
     * because of entry order) and splits them into position periods ({@link #positionPeriodsOf}). The dates come from
     * the last period, the closed positions from every period that has a sell. A sell while already flat is a
     * data-entry mistake and belongs to no period, on the assumption the matching buy simply hasn't been entered
     * yet — the server never rejects it, and {@code netQuantity} still counts it, so the check against IB points at it.
     */
    public static HoldingHistory of(List<TradeFact> trades) {
        List<TradeFact> tradesInDateOrder = trades.stream()
                .sorted(Comparator.comparing(TradeFact::date).thenComparing(trade -> trade.side() == TradeSide.SELL))
                .toList();
        BigDecimal netQuantity = tradesInDateOrder.stream()
                .map(TradeFact::signedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PositionPeriod> positionPeriods = positionPeriodsOf(tradesInDateOrder);
        List<ClosedPosition> closedPositions = positionPeriods.stream()
                .filter(PositionPeriod::hasSell)
                .map(PositionPeriod::toClosedPosition)
                .toList();

        if (positionPeriods.isEmpty()) {
            return new HoldingHistory(null, null, netQuantity, closedPositions, BigDecimal.ZERO);
        }
        PositionPeriod currentPeriod = positionPeriods.getLast();
        return new HoldingHistory(currentPeriod.firstBuyDate(), currentPeriod.lastSellDate(), netQuantity,
                closedPositions, currentPeriod.heldCost());
    }

    /**
     * Tracks a running quantity: a buy while flat opens a position period, and the sell that brings the quantity
     * back to zero closes it — or below zero, when more was sold than bought, a mistake the quantity check points
     * at. Only the last period can still be open.
     */
    private static List<PositionPeriod> positionPeriodsOf(List<TradeFact> tradesInDateOrder) {
        List<PositionPeriod> positionPeriods = new ArrayList<>();
        List<TradeFact> currentPeriodTrades = new ArrayList<>();
        BigDecimal runningQuantity = BigDecimal.ZERO;

        for (TradeFact trade : tradesInDateOrder) {
            boolean isSell = trade.side() == TradeSide.SELL;
            boolean positionIsFlat = currentPeriodTrades.isEmpty();
            if (isSell && positionIsFlat) {
                continue;
            }
            currentPeriodTrades.add(trade);
            runningQuantity = runningQuantity.add(trade.signedQuantity());
            if (isSell && runningQuantity.compareTo(ZERO_TOLERANCE) <= 0) {
                positionPeriods.add(new PositionPeriod(currentPeriodTrades, true));
                currentPeriodTrades = new ArrayList<>();
                runningQuantity = BigDecimal.ZERO;
            }
        }
        if (!currentPeriodTrades.isEmpty()) {
            positionPeriods.add(new PositionPeriod(currentPeriodTrades, false));
        }
        return positionPeriods;
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
     * all, then a {@code CLOSED} holding whose current position period no sell ends, then a quantity other than IB's.
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

    /**
     * "90", "10.5", "-3": without the trailing zeros a {@code NUMERIC(20,6)} column adds ("90.000000"). Also used by
     * {@link ClosedPosition}'s warning.
     */
    protected static String plainNumber(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    /** The trades of one position period, in date order. It always starts with a buy. */
    private record PositionPeriod(List<TradeFact> trades, boolean isClosed) {

        private LocalDate firstBuyDate() {
            return trades.getFirst().date();
        }

        /** {@code null} while nothing of this period has been sold. */
        private LocalDate lastSellDate() {
            List<TradeFact> sells = tradesOnSide(TradeSide.SELL);
            return sells.isEmpty() ? null : sells.getLast().date();
        }

        /** Only a period with a sell has realized anything: one still open with a partial sell too. */
        private boolean hasSell() {
            return lastSellDate() != null;
        }

        private ClosedPosition toClosedPosition() {
            List<Long> tradeIds = trades.stream().map(TradeFact::id).toList();
            return averageCost().toClosedPosition(firstBuyDate(), lastSellDate(), isClosed, tradeIds);
        }

        /** What the shares still held cost: nothing once the period is closed. */
        private BigDecimal heldCost() {
            return isClosed ? BigDecimal.ZERO : averageCost().heldCostWithBuyCommissions();
        }

        /** Every trade of the period through an {@link AverageCostCalculator}, in date order. */
        private AverageCostCalculator averageCost() {
            AverageCostCalculator averageCostCalculator = new AverageCostCalculator();
            for (TradeFact trade : trades) {
                if (trade.side() == TradeSide.BUY) {
                    averageCostCalculator.addBuy(trade);
                } else {
                    averageCostCalculator.addSell(trade);
                }
            }
            return averageCostCalculator;
        }

        private List<TradeFact> tradesOnSide(TradeSide side) {
            return trades.stream().filter(trade -> trade.side() == side).toList();
        }
    }

    /**
     * Goes over the trades of one position period, in date order, and works out what the shares sold cost at average
     * cost. Every share sold costs the average of the shares held at that moment, and takes the same part of the buy
     * commissions with it; the sell that brings the quantity back to zero takes all that is left, so a closed period
     * adds up exactly. Static: it is used while the history is being built, before there is one.
     *
     * <p>Example: buy 10 at 100, sell 5 at 150, buy 10 at 200, sell 15 at 180. The first sell takes half the cost
     * (500); the 5 left and the 10 bought then average 166.67, and the last sell takes all of it (2,500). Sold: 20 shares
     * that cost 3,000 (150 on average) for 3,450 — the same +450 as all the proceeds less all the cost.
     */
    private static final class AverageCostCalculator {

        private BigDecimal heldQuantity = BigDecimal.ZERO;
        /** {@code null} once a buy without a price is held: the average is unknown from then on. */
        private BigDecimal heldCost = BigDecimal.ZERO;
        private BigDecimal heldBuyCommissions = BigDecimal.ZERO;

        private BigDecimal boughtQuantity = BigDecimal.ZERO;
        private BigDecimal soldQuantity = BigDecimal.ZERO;
        private BigDecimal soldCost = BigDecimal.ZERO;
        private BigDecimal sellProceeds = BigDecimal.ZERO;
        private BigDecimal commissions = BigDecimal.ZERO;

        private void addBuy(TradeFact buy) {
            heldQuantity = heldQuantity.add(buy.quantity());
            heldCost = sumOrNull(heldCost, buy.amount());
            heldBuyCommissions = heldBuyCommissions.add(buy.commission());
            boughtQuantity = boughtQuantity.add(buy.quantity());
        }

        /** A period only takes a sell while something is held, so {@code heldQuantity} is never 0 here. */
        private void addSell(TradeFact sell) {
            BigDecimal costLeaving = partLeaving(heldCost, sell.quantity());
            BigDecimal buyCommissionsLeaving = partLeaving(heldBuyCommissions, sell.quantity());
            heldCost = heldCost == null ? null : heldCost.subtract(costLeaving);
            heldBuyCommissions = heldBuyCommissions.subtract(buyCommissionsLeaving);
            heldQuantity = heldQuantity.subtract(sell.quantity());

            soldQuantity = soldQuantity.add(sell.quantity());
            soldCost = sumOrNull(soldCost, costLeaving);
            sellProceeds = sumOrNull(sellProceeds, sell.amount());
            commissions = commissions.add(sell.commission()).add(buyCommissionsLeaving);
        }

        /** The part of a held total that leaves with {@code quantitySold} shares: all of it when nothing stays held. */
        private BigDecimal partLeaving(BigDecimal heldTotal, BigDecimal quantitySold) {
            if (heldTotal == null) {
                return null;
            }
            boolean sellsEverything = heldQuantity.subtract(quantitySold).compareTo(ZERO_TOLERANCE) <= 0;
            if (sellsEverything) {
                return heldTotal;
            }
            return heldTotal.multiply(quantitySold).divide(heldQuantity, DIVISION_PRECISION);
        }

        /**
         * What the shares still held cost, with the part of the buy commissions that stays with them. In the example
         * above, after the first sell: 500 for the 5 left, plus half of the first buy's commission.
         */
        private BigDecimal heldCostWithBuyCommissions() {
            return heldCost == null ? null : heldCost.add(heldBuyCommissions);
        }

        private BigDecimal sumOrNull(BigDecimal runningTotal, BigDecimal amount) {
            return runningTotal == null || amount == null ? null : runningTotal.add(amount);
        }

        private ClosedPosition toClosedPosition(LocalDate openDate, LocalDate closeDate, boolean isClosed,
                                                List<Long> tradeIds) {
            BigDecimal remainingQuantity = isClosed ? BigDecimal.ZERO : heldQuantity;
            return new ClosedPosition(openDate, closeDate, boughtQuantity, soldQuantity, soldCost, sellProceeds,
                    commissions, remainingQuantity, tradeIds);
        }
    }
}
