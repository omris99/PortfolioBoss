package portfolioboss.db.recommendationlists;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import portfolioboss.model.recommendations.RecommendationList;
import portfolioboss.model.recommendations.ScreenedStock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One row of the {@code stock_lists_download} table: one download of the Recommendations page's three lists
 * (RECOMMENDATIONS_FINVIZ_TODO.md, session 2), saved together with its stocks ({@code cascade = PERSIST}). Every download
 * is kept; afterwards only when its stocks' daily closes were last read changes.
 */
@Entity
@Table(name = "stock_lists_download")
public class StockListsDownloadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;
    private Instant downloadedAt;
    private Instant closesReadAt;

    @OneToMany(mappedBy = "download", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<ListedStockEntity> listedStocks = new ArrayList<>();

    /** Required by JPA, which creates entities by reflection. */
    protected StockListsDownloadEntity() {
    }

    /**
     * Every list's stocks, saved in the order given — the source's; the app sorts each list itself (decision 13).
     *
     * @param source             the source's name, as the screens show it: "Finviz"
     * @param stocksByList       each list's stocks
     * @param knownConIdBySymbol the IB contracts earlier downloads found, by symbol: a stock of one of them gets its
     *                           contract at once, without asking TWS again
     */
    public StockListsDownloadEntity(String source, Instant downloadedAt,
                                    Map<RecommendationList, List<ScreenedStock>> stocksByList,
                                    Map<String, Integer> knownConIdBySymbol) {
        this.source = source;
        this.downloadedAt = downloadedAt;
        stocksByList.forEach((recommendationList, stocks) -> stocks.forEach(stock -> listedStocks.add(
                new ListedStockEntity(this, recommendationList, stock, knownConIdBySymbol.get(stock.symbol())))));
    }

    public Long id() {
        return id;
    }

    public String source() {
        return source;
    }

    public Instant downloadedAt() {
        return downloadedAt;
    }

    /** {@code null} until the closes of the download's stocks are read for the first time. */
    public Instant closesReadAt() {
        return closesReadAt;
    }

    /** Every list's stocks, in the order they were saved: the lists one after another, each in the source's order. */
    public List<ListedStockEntity> listedStocks() {
        return listedStocks;
    }

    /** The daily closes of the download's stocks were read from IB at {@code readAt}. */
    public void markClosesRead(Instant readAt) {
        this.closesReadAt = readAt;
    }
}
