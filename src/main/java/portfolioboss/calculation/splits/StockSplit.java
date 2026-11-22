package portfolioboss.calculation.splits;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One split of a stock, as stored (STOCK_SPLITS_TODO.md): IB's "SPLIT 4 FOR 1" — every share held at the split became 4
 * — or a reverse split, "SPLIT 1 FOR 10". Read from IB's Flex reports by {@code calculation.tax
 * .SplitsInFlexCorporateActions}, only when IB's words and the shares agree (decision 3).
 *
 * <p>Example: 10 shares bought at $400, then a split of 4 for 1: after it they are 40 shares at $100 — the same money.
 *
 * @param ibActionId           IB's {@code actionID}, unique to the action: a split is stored once by it (decision 2)
 * @param symbol               the stock's symbol after the split, as IB names it
 * @param conIdBeforeTheSplit  the contract the shares were in before it — the same as after it, unless a reverse split
 *                             moved the stock to a new contract
 * @param conIdAfterTheSplit   the contract the shares are in after it
 * @param splitTime            when IB booked it, in New York's time, as IB writes it — 20:25 on the day, after the market
 *                             closed
 * @param sharesBeforeTheSplit 1 in "SPLIT 4 FOR 1"
 * @param sharesAfterTheSplit  4 in "SPLIT 4 FOR 1"
 */
public record StockSplit(String ibActionId, String symbol, int conIdBeforeTheSplit, int conIdAfterTheSplit,
                         LocalDateTime splitTime, int sharesBeforeTheSplit, int sharesAfterTheSplit) {

    /**
     * 16 significant digits, as in {@code calculation.portfolio.ClosedPosition}: a split of 1 for 3 leaves a fraction no
     * number of digits holds exactly.
     */
    private static final MathContext DIVISION_PRECISION = MathContext.DECIMAL64;

    public LocalDate splitDay() {
        return splitTime.toLocalDate();
    }

    /**
     * Whether a trade typed in for this day was made before the split. A typed trade has a day and no time, and IB books
     * the split after that day's market closed, so a trade of the split's own day was before it (decision 4).
     */
    public boolean isBeforeTheSplit(LocalDate tradeDay) {
        return !tradeDay.isAfter(splitDay());
    }

    /** Whether an execution in IB's reports, which has its time, was made before the split. */
    public boolean isBeforeTheSplit(LocalDateTime tradeTime) {
        return tradeTime.isBefore(splitTime);
    }

    /** The shares a quantity held at the split became: 10 → 40 in a split of 4 for 1. */
    public BigDecimal quantityAfterTheSplit(BigDecimal quantity) {
        return quantity.multiply(BigDecimal.valueOf(sharesAfterTheSplit))
                .divide(BigDecimal.valueOf(sharesBeforeTheSplit), DIVISION_PRECISION);
    }

    /** The price of one share in the shares of after the split: $400 → $100 in a split of 4 for 1. */
    public BigDecimal priceAfterTheSplit(BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(sharesBeforeTheSplit))
                .divide(BigDecimal.valueOf(sharesAfterTheSplit), DIVISION_PRECISION);
    }

    /**
     * The split didn't move the stock to a new contract. Only such a split is a holding's: after one that moved it, the
     * sync closed the old holding and opened a new one (STOCK_SPLITS_TODO.md, out of scope).
     */
    public boolean keepsTheSameContract() {
        return conIdBeforeTheSplit == conIdAfterTheSplit;
    }

    /** "4 for 1", the shares after first, as IB writes it ("SPLIT 4 FOR 1"). */
    public String ratioInIbsWords() {
        return sharesAfterTheSplit + " for " + sharesBeforeTheSplit;
    }
}
