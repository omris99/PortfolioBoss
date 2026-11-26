package portfolioboss.db.marketdata;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.sources.Stock;

import java.time.Instant;

/**
 * One row of the {@code stock} table: the company figures of one holding (MY_HOLDINGS_TODO.md, decision 10), from
 * Finviz or Finnhub, the first that answered (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9). Written only by the sync,
 * which replaces it whenever a source answers again.
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} has Hibernate store {@code companyFigures} as JSON in its {@code JSONB} column
 * and read it back into the record, as in {@code StockAnalysisEntity}.
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
    private Stock companyFigures;

    /** Required by JPA, which creates entities by reflection. */
    protected StockEntity() {
    }

    public StockEntity(int conId, Stock companyFigures, Instant fetchedAt) {
        this.conId = conId;
        this.symbol = companyFigures.symbol();
        this.fetchedAt = fetchedAt;
        this.companyFigures = companyFigures;
    }

    public int conId() {
        return conId;
    }

    public Stock companyFigures() {
        return companyFigures;
    }

    /** When the source gave this answer: the sync that stored it. */
    public Instant fetchedAt() {
        return fetchedAt;
    }
}
