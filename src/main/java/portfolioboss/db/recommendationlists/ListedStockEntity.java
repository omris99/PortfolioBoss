package portfolioboss.db.recommendationlists;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import portfolioboss.model.recommendations.RecommendationList;
import portfolioboss.model.recommendations.ScreenedStock;

/**
 * One row of the {@code listed_stock} table: a stock of one of the lists of a download (RECOMMENDATIONS_FINVIZ_TODO.md,
 * session 2). Written with its {@link StockListsDownloadEntity}; afterwards only its IB contract is filled in, once TWS
 * finds it. No order is stored: the app sorts each list itself, from the figures (decision 13).
 *
 * <p>{@code @JdbcTypeCode(SqlTypes.JSON)} has Hibernate store {@code figures} as JSON in its {@code JSONB} column and read
 * it back into the record, as in {@code StockEntity}.
 */
@Entity
@Table(name = "listed_stock")
public class ListedStockEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "download_id")
    private StockListsDownloadEntity download;

    @Enumerated(EnumType.STRING)
    private RecommendationList recommendationList;

    private String symbol;
    private Integer conId;

    @JdbcTypeCode(SqlTypes.JSON)
    private ScreenedStock figures;

    /** Required by JPA, which creates entities by reflection. */
    protected ListedStockEntity() {
    }

    /** @param conId the IB contract of the symbol when an earlier download found it; {@code null} until TWS finds it */
    protected ListedStockEntity(StockListsDownloadEntity download, RecommendationList recommendationList,
                                ScreenedStock figures, Integer conId) {
        this.download = download;
        this.recommendationList = recommendationList;
        this.symbol = figures.symbol();
        this.conId = conId;
        this.figures = figures;
    }

    public Long id() {
        return id;
    }

    public RecommendationList recommendationList() {
        return recommendationList;
    }

    /** As IB writes it: "BRK B". */
    public String symbol() {
        return symbol;
    }

    /** {@code null} while TWS hasn't found the symbol's contract. */
    public Integer conId() {
        return conId;
    }

    public ScreenedStock figures() {
        return figures;
    }

    /** TWS found the symbol's contract. */
    public void changeConId(int conId) {
        this.conId = conId;
    }
}
