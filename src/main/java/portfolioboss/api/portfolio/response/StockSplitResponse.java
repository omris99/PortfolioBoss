package portfolioboss.api.portfolio.response;

import portfolioboss.calculation.splits.StockSplit;

import java.time.LocalDate;

/**
 * One split of a holding as the UI receives it (STOCK_SPLITS_TODO.md): a line in the drawer's list of trades, at its day.
 * "SPLIT 4 FOR 1" is {@code sharesBeforeTheSplit} 1 and {@code sharesAfterTheSplit} 4.
 *
 * @param splitDay the day IB booked it, after that day's market closed: a trade of that day was before it
 */
public record StockSplitResponse(LocalDate splitDay, int sharesBeforeTheSplit, int sharesAfterTheSplit) {

    public StockSplitResponse(StockSplit split) {
        this(split.splitDay(), split.sharesBeforeTheSplit(), split.sharesAfterTheSplit());
    }
}
