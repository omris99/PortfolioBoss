package portfolioboss.db;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Scan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row of the {@code scan} table: one run of the hot-stock scans, written only by the sync and never changed — every
 * scan is kept for the measurement. Its candidates are saved with it ({@code cascade = PERSIST}: saving the scan saves
 * the candidate rows too).
 */
@Entity
@Table(name = "scan")
public class ScanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant scannedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> scanCodes;

    @OneToMany(mappedBy = "scan", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<ScanCandidateEntity> candidates = new ArrayList<>();

    /** Required by JPA, which creates entities by reflection. */
    protected ScanEntity() {
    }

    /**
     * @param lastCloseByConId each stock's last daily close read in the same run; a stock missing from it is stored
     *                         without a close
     */
    protected ScanEntity(Scan scan, Map<Integer, DailyClose> lastCloseByConId) {
        this.scannedAt = scan.scannedAt();
        this.scanCodes = scan.scanCodes();
        scan.candidates().forEach(candidate -> candidates.add(
                new ScanCandidateEntity(this, candidate, lastCloseByConId.get(candidate.conId()))));
    }

    public Long id() {
        return id;
    }

    public Instant scannedAt() {
        return scannedAt;
    }

    public List<String> scanCodes() {
        return scanCodes;
    }

    /** The best placed first, as {@code Scan.candidates()} ordered them when they were saved. */
    public List<ScanCandidateEntity> candidates() {
        return candidates;
    }
}
