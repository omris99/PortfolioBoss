package portfolioboss.db.splits;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import portfolioboss.calculation.splits.StockSplit;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * One row of the {@code stock_split} table ({@code V20__stock_splits.sql}): a split the sync found in IB's Flex reports
 * — a {@link StockSplit}. Written only by {@code sync.StockSplitDetectionService}, once per split, and never changed.
 */
@Entity
@Table(name = "stock_split")
public class StockSplitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ibActionId;
    private String symbol;
    private int conIdBeforeTheSplit;
    private int conIdAfterTheSplit;
    private LocalDateTime splitTime;
    private int sharesBeforeTheSplit;
    private int sharesAfterTheSplit;
    private Instant storedAt;

    /** Required by JPA, which creates entities by reflection. */
    protected StockSplitEntity() {
    }

    public StockSplitEntity(StockSplit split, Instant storedAt) {
        this.ibActionId = split.ibActionId();
        this.symbol = split.symbol();
        this.conIdBeforeTheSplit = split.conIdBeforeTheSplit();
        this.conIdAfterTheSplit = split.conIdAfterTheSplit();
        this.splitTime = split.splitTime();
        this.sharesBeforeTheSplit = split.sharesBeforeTheSplit();
        this.sharesAfterTheSplit = split.sharesAfterTheSplit();
        this.storedAt = storedAt;
    }

    /** The split as stored, for the calculation, which works on its records rather than on entities. */
    public StockSplit toStockSplit() {
        return new StockSplit(ibActionId, symbol, conIdBeforeTheSplit, conIdAfterTheSplit, splitTime,
                sharesBeforeTheSplit, sharesAfterTheSplit);
    }
}
