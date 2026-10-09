package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import portfolioboss.calculation.DailyClose;

import java.time.LocalDate;

/**
 * One row of the {@code daily_close} table: one trading day's closing price of a contract, written only by the sync.
 * A generated {@code id} rather than a key of {@code (con_id, bar_date)} — JPA would need a separate key class for
 * that; the table's {@code UNIQUE} constraint still keeps one row per contract and day.
 */
@Entity
@Table(name = "daily_close")
public class DailyCloseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int conId;
    private LocalDate barDate;
    private double closePrice;

    /** Required by JPA, which creates entities by reflection. */
    protected DailyCloseEntity() {
    }

    protected DailyCloseEntity(int conId, DailyClose dailyClose) {
        this.conId = conId;
        this.barDate = dailyClose.date();
        this.closePrice = dailyClose.close();
    }

    public int conId() {
        return conId;
    }

    public DailyClose toDailyClose() {
        return new DailyClose(barDate, closePrice);
    }
}
