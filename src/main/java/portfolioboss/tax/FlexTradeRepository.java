package portfolioboss.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link FlexTradeEntity} rows; Spring generates the class at startup, like the other repositories. */
public interface FlexTradeRepository extends JpaRepository<FlexTradeEntity, Long> {

    List<FlexTradeEntity> findByIbTradeIdIn(Collection<String> ibTradeIds);

    /** The rows already stored for these IB ids, in one query, by IB's id; an id not stored yet is missing. */
    default Map<String, FlexTradeEntity> storedTradesByIbTradeId(Collection<String> ibTradeIds) {
        if (ibTradeIds.isEmpty()) {
            return Map.of();
        }
        return findByIbTradeIdIn(ibTradeIds).stream()
                .collect(Collectors.toMap(FlexTradeEntity::ibTradeId, storedTrade -> storedTrade));
    }
}
