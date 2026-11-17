package portfolioboss.tax;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Which shares each sale of one stock sold, first in first out, as Phoenix computes the tax (CAPITAL_GAINS_TAX_TODO.md,
 * decision 3): it goes over the stock's trades in the order they were made, keeps the buys still held — each with the
 * shares of it not sold yet —, and every sale takes its shares from the earliest of them. A buy's commission goes into
 * the cost of its shares, a sale's commission comes off the proceeds of its shares, each spread over the shares by
 * quantity.
 *
 * <p>Example (decision 3): buy 10 at $10, buy 10 at $20, sell 10 at $25 — the sale takes all of the first buy, a gain of
 * $150 (at average cost it would be $100), and the 10 of the second buy stay held.
 *
 * <p>Only for the tax: the closed positions stay at average cost ({@code calculation.HoldingHistory}), as IB shows them.
 *
 * @param tradesInOrder one stock's buys and sales, the earliest first ({@link TradesOfOneStock})
 */
record FifoSalesCalculator(List<TaxCalculationTrade> tradesInOrder) {

    /**
     * Below this a quantity counts as none: a split of 1 for 3 turns a whole number of shares into a fraction that 16
     * significant digits can't hold exactly.
     */
    private static final BigDecimal QUANTITY_TOLERANCE = new BigDecimal("0.000001");

    /** 16 significant digits, as in {@code calculation.ClosedPosition}: the part of a commission that goes with a part. */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    /**
     * Every sale of the stock with the buys it took its shares from, in the order of the trades. Beside the buys still
     * held, the same shares are followed at average cost, as the Closed page counts them, so that each sale knows its cost
     * both ways ({@link FifoSale#fifoCostDiffersFromTheAverageCost}).
     */
    protected List<FifoSale> sales() {
        Deque<BuyStillHeld> buysStillHeldEarliestFirst = new ArrayDeque<>();
        SharesHeldAtTheAverageCost sharesHeldAtTheAverageCost =
                new SharesHeldAtTheAverageCost(BigDecimal.ZERO, BigDecimal.ZERO);
        List<FifoSale> sales = new ArrayList<>();
        for (TaxCalculationTrade trade : tradesInOrder) {
            if (trade.isSale()) {
                BigDecimal costAtTheAverageCost = sharesHeldAtTheAverageCost.costOfSelling(trade.quantity());
                sales.add(saleFromTheEarliestBuys(trade, buysStillHeldEarliestFirst, costAtTheAverageCost));
                sharesHeldAtTheAverageCost = sharesHeldAtTheAverageCost.afterSale(trade.quantity());
            } else {
                buysStillHeldEarliestFirst.addLast(new BuyStillHeld(trade, trade.quantity()));
                sharesHeldAtTheAverageCost = sharesHeldAtTheAverageCost.afterBuy(trade);
            }
        }
        return sales;
    }

    /**
     * Takes the sale's shares from the earliest buys still held, and puts back what is left of the last buy it took from.
     * What the buys can't cover is kept as {@code quantitySoldBeyondTheBuys}.
     */
    private FifoSale saleFromTheEarliestBuys(TaxCalculationTrade sale, Deque<BuyStillHeld> buysStillHeldEarliestFirst,
                                             BigDecimal costAtTheAverageCost) {
        List<SharesSoldFromOneBuy> sharesSoldFromEachBuy = new ArrayList<>();
        BigDecimal quantityLeftToSell = sale.quantity();
        while (isMoreThanNothing(quantityLeftToSell) && !buysStillHeldEarliestFirst.isEmpty()) {
            BuyStillHeld earliestBuy = buysStillHeldEarliestFirst.removeFirst();
            BigDecimal quantityTaken = earliestBuy.sharesNotSoldYet().min(quantityLeftToSell);
            sharesSoldFromEachBuy.add(sharesSoldFrom(earliestBuy.buy(), quantityTaken, sale));

            BigDecimal sharesLeftOfTheBuy = earliestBuy.sharesNotSoldYet().subtract(quantityTaken);
            if (isMoreThanNothing(sharesLeftOfTheBuy)) {
                buysStillHeldEarliestFirst.addFirst(new BuyStillHeld(earliestBuy.buy(), sharesLeftOfTheBuy));
            }
            quantityLeftToSell = quantityLeftToSell.subtract(quantityTaken);
        }
        BigDecimal quantitySoldBeyondTheBuys =
                isMoreThanNothing(quantityLeftToSell) ? quantityLeftToSell : BigDecimal.ZERO;
        return new FifoSale(sale, sharesSoldFromEachBuy, quantitySoldBeyondTheBuys, costAtTheAverageCost);
    }

    private SharesSoldFromOneBuy sharesSoldFrom(TaxCalculationTrade buy, BigDecimal quantityTaken,
                                                TaxCalculationTrade sale) {
        BigDecimal cost = quantityTaken.multiply(buy.price())
                .add(partOfTheCommission(buy, quantityTaken));
        BigDecimal proceeds = quantityTaken.multiply(sale.price())
                .subtract(partOfTheCommission(sale, quantityTaken));
        return new SharesSoldFromOneBuy(buy, quantityTaken, cost, proceeds);
    }

    /** The trade's commission spread over its shares: all of it when the part is the whole trade. */
    private BigDecimal partOfTheCommission(TaxCalculationTrade trade, BigDecimal quantityOfThePart) {
        if (quantityOfThePart.compareTo(trade.quantity()) == 0) {
            return trade.commission();
        }
        return trade.commission().multiply(quantityOfThePart).divide(trade.quantity(), DIVISION_PRECISION);
    }

    private boolean isMoreThanNothing(BigDecimal quantity) {
        return quantity.compareTo(QUANTITY_TOLERANCE) > 0;
    }

    /**
     * A buy whose shares are not all sold yet.
     *
     * @param buy              the buy
     * @param sharesNotSoldYet how many of its shares are still held
     */
    private record BuyStillHeld(TaxCalculationTrade buy, BigDecimal sharesNotSoldYet) {
    }

    /**
     * The shares held, with their cost counted the way the Closed page counts it ({@code calculation.HoldingHistory}):
     * every share sold costs the average of the shares held at that moment, and the sale that sells everything takes all
     * that is left. Only to tell whether a sale's cost differs between the two ways.
     *
     * <p>Example: buy 10 at $10, buy 10 at $20, sell 10, sell 5. The second sale costs $75 here (5 × the $15 average that
     * stays), and $100 first in first out (5 of the $20 buy).
     *
     * @param quantity the shares held
     * @param cost     what they cost, the buys' commissions included
     */
    private record SharesHeldAtTheAverageCost(BigDecimal quantity, BigDecimal cost) {

        private SharesHeldAtTheAverageCost afterBuy(TaxCalculationTrade buy) {
            BigDecimal costOfTheBuy = buy.quantity().multiply(buy.price()).add(buy.commission());
            return new SharesHeldAtTheAverageCost(quantity.add(buy.quantity()), cost.add(costOfTheBuy));
        }

        /** Shares sold beyond those held cost nothing, as first in first out. */
        private BigDecimal costOfSelling(BigDecimal quantitySold) {
            if (sellsEverything(quantitySold)) {
                return cost;
            }
            return cost.multiply(quantitySold).divide(quantity, DIVISION_PRECISION);
        }

        private SharesHeldAtTheAverageCost afterSale(BigDecimal quantitySold) {
            if (sellsEverything(quantitySold)) {
                return new SharesHeldAtTheAverageCost(BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return new SharesHeldAtTheAverageCost(quantity.subtract(quantitySold),
                    cost.subtract(costOfSelling(quantitySold)));
        }

        private boolean sellsEverything(BigDecimal quantitySold) {
            return quantity.subtract(quantitySold).compareTo(QUANTITY_TOLERANCE) <= 0;
        }
    }
}
