package portfolioboss.db.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link FlexCorporateActionEntity} rows, like {@link FlexTradeRepository} the trades. */
public interface FlexCorporateActionRepository extends JpaRepository<FlexCorporateActionEntity, Long> {

    List<FlexCorporateActionEntity> findByIbTransactionIdIn(Collection<String> ibTransactionIds);

    /** The rows already stored for these IB ids, in one query, by IB's id; an id not stored yet is missing. */
    default Map<String, FlexCorporateActionEntity> storedCorporateActionsByIbTransactionId(
            Collection<String> ibTransactionIds) {
        if (ibTransactionIds.isEmpty()) {
            return Map.of();
        }
        return findByIbTransactionIdIn(ibTransactionIds).stream()
                .collect(Collectors.toMap(FlexCorporateActionEntity::ibTransactionId,
                        storedCorporateAction -> storedCorporateAction));
    }
}
