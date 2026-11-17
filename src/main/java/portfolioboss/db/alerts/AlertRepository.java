package portfolioboss.db.alerts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import portfolioboss.model.alerts.AlertType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Reads and writes {@link AlertEntity} rows; Spring generates the class at startup, like the other repositories. */
public interface AlertRepository extends JpaRepository<AlertEntity, Long> {

    /** The open alerts — their condition was still true at the last check —, found through the table's partial index. */
    List<AlertEntity> findByEndedAtIsNull();

    /** What the bell shows: the alerts of these types, open or ended, the newest first. {@code alertTypes} not empty. */
    List<AlertEntity> findByAlertTypeInOrderByCreatedAtDescIdDesc(Collection<AlertType> alertTypes);

    /**
     * The bell was opened: every alert of these types not seen before is seen now — one {@code UPDATE} statement, like
     * {@code DailyCloseRepository.deleteForContracts}. {@code alertTypes} not empty.
     */
    @Modifying
    @Query("update AlertEntity alert set alert.seenAt = :seenAt "
            + "where alert.seenAt is null and alert.alertType in :alertTypes")
    void markAsSeen(@Param("alertTypes") Collection<AlertType> alertTypes, @Param("seenAt") Instant seenAt);
}
