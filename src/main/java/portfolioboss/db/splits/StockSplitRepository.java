package portfolioboss.db.splits;

import org.springframework.data.jpa.repository.JpaRepository;
import portfolioboss.calculation.splits.StoredStockSplits;

/** Reads and writes {@link StockSplitEntity} rows; Spring generates the class at startup, like the other repositories. */
public interface StockSplitRepository extends JpaRepository<StockSplitEntity, Long> {

    /** Every stored split, in one query. */
    default StoredStockSplits storedStockSplits() {
        return new StoredStockSplits(findAll().stream().map(StockSplitEntity::toStockSplit).toList());
    }
}
