package portfolioboss.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.ScanCandidate;
import portfolioboss.utils.Utils;

import java.time.LocalDate;
import java.util.Map;

/**
 * One row of the {@code scan_candidate} table: a stock one scan kept, with the price the measurement starts from.
 * Written only with its {@link ScanEntity}, never changed.
 */
@Entity
@Table(name = "scan_candidate")
public class ScanCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id")
    private ScanEntity scan;

    /**
     * The same column again, as a plain id, so that which scan the stock belongs to can be read without loading the scan
     * — after the transaction too. Read only ({@code insertable} and {@code updatable} false): {@code scan} writes it.
     */
    @Column(name = "scan_id", insertable = false, updatable = false)
    private Long scanId;

    private int conId;
    private String symbol;
    private String companyName;
    private String exchange;
    private String industry;
    private String category;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Integer> rankByScanCode;

    private Double closeAtScan;
    private LocalDate closeDate;

    /** Required by JPA, which creates entities by reflection. */
    protected ScanCandidateEntity() {
    }

    /**
     * The name, industry and category as IB sent them, but an empty text stored as {@code NULL}: IB sent nothing.
     *
     * @param lastClose the stock's last daily close read in the same run; {@code null} when its closes did not come
     */
    protected ScanCandidateEntity(ScanEntity scan, ScanCandidate candidate, DailyClose lastClose) {
        this.scan = scan;
        this.conId = candidate.conId();
        this.symbol = candidate.symbol();
        this.companyName = Utils.trimmedOrNull(candidate.companyName());
        this.exchange = candidate.exchange();
        this.industry = Utils.trimmedOrNull(candidate.industry());
        this.category = Utils.trimmedOrNull(candidate.category());
        this.rankByScanCode = candidate.rankByScanCode();
        this.closeAtScan = lastClose == null ? null : lastClose.close();
        this.closeDate = lastClose == null ? null : lastClose.date();
    }

    public Long id() {
        return id;
    }

    public Long scanId() {
        return scanId;
    }

    public int conId() {
        return conId;
    }

    public String symbol() {
        return symbol;
    }

    public String companyName() {
        return companyName;
    }

    public String exchange() {
        return exchange;
    }

    public String industry() {
        return industry;
    }

    public String category() {
        return category;
    }

    public Map<String, Integer> rankByScanCode() {
        return rankByScanCode;
    }

    public Double closeAtScan() {
        return closeAtScan;
    }

    public LocalDate closeDate() {
        return closeDate;
    }
}
