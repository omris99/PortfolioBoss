package portfolioboss.tax;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

/** Reads and writes {@link FlexReportEntity} rows, the log of the downloads. */
public interface FlexReportRepository extends JpaRepository<FlexReportEntity, Long> {

    /** The last day of the stored reports — the latest report's last day —; empty before the first download. */
    @Query("select max(report.toDate) from FlexReportEntity report")
    Optional<LocalDate> findLatestToDate();
}
