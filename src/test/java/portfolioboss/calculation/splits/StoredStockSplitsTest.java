package portfolioboss.calculation.splits;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which stored splits are a holding's, and which actions are stored already (STOCK_SPLITS_TODO.md). */
class StoredStockSplitsTest {

    private static final int HOLDING_CON_ID = 1111;

    private final StockSplit laterSplit =
            new StockSplit("A2", "WXYZ", HOLDING_CON_ID, HOLDING_CON_ID, LocalDateTime.of(2026, 3, 10, 20, 25), 1, 4);
    private final StockSplit earlierSplit =
            new StockSplit("A1", "WXYZ", HOLDING_CON_ID, HOLDING_CON_ID, LocalDateTime.of(2025, 5, 12, 20, 25), 1, 2);
    private final StockSplit intoTheHoldingsContract =
            new StockSplit("A3", "WXYZ", 9999, HOLDING_CON_ID, LocalDateTime.of(2024, 6, 10, 20, 25), 10, 1);
    private final StockSplit ofAnotherStock =
            new StockSplit("A4", "ABCD", 2222, 2222, LocalDateTime.of(2026, 1, 5, 20, 25), 1, 3);

    private final StoredStockSplits storedSplits =
            new StoredStockSplits(List.of(laterSplit, ofAnotherStock, intoTheHoldingsContract, earlierSplit));

    @Test
    void theSplitsAreKeptInTimeOrder() {
        assertThat(storedSplits.splits())
                .containsExactly(intoTheHoldingsContract, earlierSplit, ofAnotherStock, laterSplit);
    }

    /** One that moved another contract into the holding's is not the holding's: the sync opened a new holding for it. */
    @Test
    void aHoldingsSplitsAreThoseThatKeptItsContractTheEarliestFirst() {
        assertThat(storedSplits.splitsOfTheHolding(HOLDING_CON_ID)).containsExactly(earlierSplit, laterSplit);
        assertThat(storedSplits.splitsOfTheHolding(3333)).isEmpty();
    }

    @Test
    void anActionIsStoredWhenASplitHasItsId() {
        assertThat(storedSplits.isStored("A3")).isTrue();
        assertThat(storedSplits.isStored("A5")).isFalse();
        assertThat(new StoredStockSplits().isStored("A1")).isFalse();
    }
}
