package portfolioboss.calculation.splits;

import java.util.Comparator;
import java.util.List;

/**
 * Every split stored in the {@code stock_split} table (STOCK_SPLITS_TODO.md), the earliest first — what the holdings and
 * the tax are adjusted by. Built by {@code db.splits.StockSplitRepository.storedStockSplits()}.
 *
 * @param splits every stored split; kept in time order whatever order they are given in
 */
public record StoredStockSplits(List<StockSplit> splits) {

    public StoredStockSplits {
        splits = splits.stream().sorted(Comparator.comparing(StockSplit::splitTime)).toList();
    }

    /** No split stored: before the first split, and in a test that has none. */
    public StoredStockSplits() {
        this(List.of());
    }

    /**
     * A holding's splits, the earliest first: those of its contract that kept it ({@link StockSplit#keepsTheSameContract}).
     */
    public List<StockSplit> splitsOfTheHolding(int conId) {
        return splits.stream()
                .filter(StockSplit::keepsTheSameContract)
                .filter(split -> split.conIdAfterTheSplit() == conId)
                .toList();
    }

    /** Whether IB's action is stored already: a split is stored once, whatever number of reports bring it (decision 2). */
    public boolean isStored(String ibActionId) {
        return splits.stream().anyMatch(split -> split.ibActionId().equals(ibActionId));
    }
}
