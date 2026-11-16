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

    /** Every sale of the stock with the buys it took its shares from, in the order of the trades. */
    protected List<FifoSale> sales() {
        Deque<BuyStillHeld> buysStillHeldEarliestFirst = new ArrayDeque<>();
        List<FifoSale> sales = new ArrayList<>();
        for (TaxCalculationTrade trade : tradesInOrder) {
            if (trade.isSale()) {
                sales.add(saleFromTheEarliestBuys(trade, buysStillHeldEarliestFirst));
            } else {
                buysStillHeldEarliestFirst.addLast(new BuyStillHeld(trade, trade.quantity()));
            }
        }
        return sales;
    }

    /**
     * Takes the sale's shares from the earliest buys still held, and puts back what is left of the last buy it took from.
     * What the buys can't cover is kept as {@code quantitySoldBeyondTheBuys}.
     */
    private FifoSale saleFromTheEarliestBuys(TaxCalculationTrade sale, Deque<BuyStillHeld> buysStillHeldEarliestFirst) {
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
        return new FifoSale(sale, sharesSoldFromEachBuy, quantitySoldBeyondTheBuys);
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
}
