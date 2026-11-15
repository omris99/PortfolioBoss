package portfolioboss.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link FlexCashTransactionEntity} rows, like {@link FlexTradeRepository} the trades. */
public interface FlexCashTransactionRepository extends JpaRepository<FlexCashTransactionEntity, Long> {

    List<FlexCashTransactionEntity> findByIbTransactionIdIn(Collection<String> ibTransactionIds);

    /** The rows already stored for these IB ids, in one query, by IB's id; an id not stored yet is missing. */
    default Map<String, FlexCashTransactionEntity> storedCashTransactionsByIbTransactionId(
            Collection<String> ibTransactionIds) {
        if (ibTransactionIds.isEmpty()) {
            return Map.of();
        }
        return findByIbTransactionIdIn(ibTransactionIds).stream()
                .collect(Collectors.toMap(FlexCashTransactionEntity::ibTransactionId,
                        storedCashTransaction -> storedCashTransaction));
    }
}
