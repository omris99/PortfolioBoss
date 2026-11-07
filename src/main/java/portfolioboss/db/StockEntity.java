package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.stocks.Stock;

import java.time.Instant;

/**
 * One row of the {@code stock} table: what Finnhub answered about one holding (MY_HOLDINGS_TODO.md, decision 10).
 * Written only by the sync, which replaces it whenever Finnhub answers again.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} has Hibernate store {@code finnhubData} as JSON in its {@code JSONB} column
 * and read it back into the record, as in {@link StockAnalysisEntity}.
 */
@Entity
@Table(name = "stock")
public class StockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int conId;
    private String symbol;
    private Instant fetchedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    private Stock finnhubData;

    /** Required by JPA, which creates entities by reflection. */
    protected StockEntity() {
    }

    protected StockEntity(int conId, Stock finnhubData, Instant fetchedAt) {
        this.conId = conId;
        this.symbol = finnhubData.symbol();
        this.fetchedAt = fetchedAt;
        this.finnhubData = finnhubData;
    }

    public int conId() {
        return conId;
    }

    public Stock finnhubData() {
        return finnhubData;
    }

    /** When Finnhub gave this answer: the sync that stored it. */
    public Instant fetchedAt() {
        return fetchedAt;
    }
}
