package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import portfolioboss.stocks.Stock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link StockEntity} rows, like {@link DailyCloseRepository} the daily closes. */
public interface StockRepository extends JpaRepository<StockEntity, Long> {

    List<StockEntity> findByConIdIn(Collection<Integer> conIds);

    /** What Finnhub answered about each of these contracts, in one query; one it never answered for is missing. */
    default Map<Integer, Stock> finnhubDataByConId(Collection<Integer> conIds) {
        if (conIds.isEmpty()) {
            return Map.of();
        }
        return findByConIdIn(conIds).stream()
                .collect(Collectors.toMap(StockEntity::conId, StockEntity::finnhubData));
    }

    /** One {@code DELETE} statement for all of these contracts' rows, like {@code DailyCloseRepository}'s. */
    @Modifying
    @Query("delete from StockEntity stock where stock.conId in :conIds")
    void deleteForContracts(@Param("conIds") Collection<Integer> conIds);
}
