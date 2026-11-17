package portfolioboss.db.tax;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Reads and writes {@link TaxConfirmationEntity} rows; Spring generates the class at startup, like the other repositories. */
public interface TaxConfirmationRepository extends JpaRepository<TaxConfirmationEntity, Long> {

    /** Every confirmation, the earliest settlement day first. */
    List<TaxConfirmationEntity> findAllByOrderBySettlementDay();

    /** The confirmations of these days, if any is confirmed already; {@code settlementDays} not empty. */
    List<TaxConfirmationEntity> findBySettlementDayIn(Collection<LocalDate> settlementDays);
}
