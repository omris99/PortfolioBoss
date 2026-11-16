package portfolioboss.tax;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the {@code flex_report} table ({@code V16__flex_reports.sql}), the log of the downloads: a report stored in
 * the database — the days it covered, as IB wrote them in it, and how many rows it brought. The latest {@code toDate} is
 * how far the stored reports reach ({@link FlexDownloadSchedule}).
 */
@Entity
@Table(name = "flex_report")
public class FlexReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fromDate;
    private LocalDate toDate;
    private Instant downloadedAt;
    private int tradeCount;
    private int cashTransactionCount;
    private int corporateActionCount;

    /** Required by JPA, which creates entities by reflection. */
    protected FlexReportEntity() {
    }

    protected FlexReportEntity(FlexReport report, Instant downloadedAt) {
        this.fromDate = report.period().fromDate();
        this.toDate = report.period().toDate();
        this.downloadedAt = downloadedAt;
        this.tradeCount = report.trades().size();
        this.cashTransactionCount = report.cashTransactions().size();
        this.corporateActionCount = report.corporateActions().size();
    }
}
