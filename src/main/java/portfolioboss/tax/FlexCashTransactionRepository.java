package portfolioboss.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link FlexCashTransactionEntity} rows, like {@link FlexTradeRepository} the trades. */
public interface FlexCashTransactionRepository extends JpaRepository<FlexCashTransactionEntity, Long> {

    List<FlexCashTransactionEntity> findByIbTransactionIdIn(Collection<String> ibTransactionIds);

    /** The rows of one of IB's types — {@code Other Fees}, the expenses that lower the account's taxable gain. */
    List<FlexCashTransactionEntity> findByTransactionType(String transactionType);

    /**
     * The settlement days of the stored cash transactions in this currency, each once: a fee is a tax shield, converted
     * at its day's rate, and so will a dividend be (session 7).
     */
    @Query("select distinct cashTransaction.settleDate from FlexCashTransactionEntity cashTransaction "
            + "where cashTransaction.currency = :currency")
    List<LocalDate> findSettleDatesInCurrency(@Param("currency") String currency);

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
