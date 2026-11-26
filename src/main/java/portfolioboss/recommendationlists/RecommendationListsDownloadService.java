package portfolioboss.recommendationlists;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.calculation.recommendations.ScanSchedule;
import portfolioboss.db.recommendationlists.ListedStockEntity;
import portfolioboss.db.recommendationlists.ListedStockRepository;
import portfolioboss.db.recommendationlists.StockListsDownloadEntity;
import portfolioboss.db.recommendationlists.StockListsDownloadRepository;
import portfolioboss.model.recommendations.RecommendationList;
import portfolioboss.model.recommendations.ScreenedStock;
import portfolioboss.sources.finviz.FinvizClient;
import portfolioboss.sources.finviz.FinvizScreen;
import portfolioboss.sources.finviz.FinvizScreens;
import portfolioboss.sources.finviz.FinvizStock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Recommendations page's three lists (RECOMMENDATIONS_FINVIZ_TODO.md, session 2), for the sync: downloaded from
 * Finviz once a trading day, each Finviz row translated into the app's {@link ScreenedStock} — the rest of the app never
 * sees Finviz's record (decision 4) —, and stored with the IB contracts already known for their symbols; then, once a
 * trading day, what IB is asked for — the contracts not found yet, and the daily closes —, and what it found. While
 * Finviz is off nothing is downloaded, and the closes of the lists stored before are still read (decision 10).
 *
 * <p>Not {@code @Transactional} as a whole: no database connection waits on Finviz. Each store is a transaction of its
 * own.
 */
@Service
public class RecommendationListsDownloadService {

    /** A time in the console, in the local time zone: "26.11 23:05". */
    private static final DateTimeFormatter CONSOLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final FinvizScreens finvizScreens;
    private final StockListsDownloadRepository downloadRepository;
    private final ListedStockRepository listedStockRepository;

    public RecommendationListsDownloadService(FinvizScreens finvizScreens,
                                              StockListsDownloadRepository downloadRepository,
                                              ListedStockRepository listedStockRepository) {
        this.finvizScreens = finvizScreens;
        this.downloadRepository = downloadRepository;
        this.listedStockRepository = listedStockRepository;
    }

    /**
     * The three lists from Finviz, once a trading day: not while Finviz is off — its startup line says so —, and not when
     * the latest download came after the latest US market close ({@link ScanSchedule}, the scan's rule). The three are
     * stored together or not at all: a download is the lists of one moment. Never throws: a failure is printed, nothing
     * is stored, and the next sync tries again.
     */
    public void downloadWhenDue(Instant now) {
        if (!finvizScreens.isConnected()) {
            return;
        }
        Instant lastDownloadedAt = downloadRepository.findFirstByOrderByDownloadedAtDesc()
                .map(StockListsDownloadEntity::downloadedAt)
                .orElse(null);
        if (!new ScanSchedule(lastDownloadedAt, now).isScanDue()) {
            System.out.println("[recommendationlists] Finviz skipped: the lists were downloaded at "
                    + CONSOLE_TIME_FORMAT.format(lastDownloadedAt) + ", after the latest US market close");
            return;
        }
        try {
            Map<RecommendationList, List<ScreenedStock>> stocksByList =
                    downloadEveryList(LocalDate.ofInstant(now, ZoneId.systemDefault()));
            storeTheDownload(stocksByList, now);
        } catch (RuntimeException e) {
            System.err.println("[recommendationlists error] the lists were not downloaded from Finviz: "
                    + e.getMessage());
        }
    }

    /**
     * What IB is asked for in this sync: the latest download's symbols without a contract, and its contracts' daily
     * closes — once a trading day, after the latest US market close, whether or not Finviz is connected.
     */
    @Transactional(readOnly = true)
    public ListedStocksToRead listedStocksToRead(Instant now) {
        Optional<StockListsDownloadEntity> latestDownload = downloadRepository.findFirstByOrderByDownloadedAtDesc();
        if (latestDownload.isEmpty() || !new ScanSchedule(latestDownload.get().closesReadAt(), now).isScanDue()) {
            return ListedStocksToRead.NOTHING;
        }
        List<ListedStockEntity> listedStocks = latestDownload.get().listedStocks();
        List<String> symbolsToFind = listedStocks.stream()
                .filter(listedStock -> listedStock.conId() == null)
                .map(ListedStockEntity::symbol)
                .distinct()
                .toList();
        List<Integer> conIdsToReadClosesOf = listedStocks.stream()
                .map(ListedStockEntity::conId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new ListedStocksToRead(latestDownload.get().id(), symbolsToFind, conIdsToReadClosesOf);
    }

    /**
     * After a sync that read them: the contracts TWS found for the download's symbols, and when its stocks' closes were
     * read — the next reading is after the next US market close. A symbol not found stays without a contract, and is
     * asked for again then. Nothing for {@link ListedStocksToRead#NOTHING}.
     *
     * @param readAt the sync's time, the one {@link #listedStocksToRead} is next asked against
     */
    @Transactional
    public void storeContractsFound(ListedStocksToRead listedStocksRead, Map<String, Integer> conIdsFoundBySymbol,
                                    Instant readAt) {
        if (listedStocksRead.isNothing()) {
            return;
        }
        StockListsDownloadEntity download = downloadRepository.findById(listedStocksRead.downloadId()).orElseThrow();
        download.listedStocks().stream()
                .filter(listedStock -> listedStock.conId() == null)
                .filter(listedStock -> conIdsFoundBySymbol.containsKey(listedStock.symbol()))
                .forEach(listedStock -> listedStock.changeConId(conIdsFoundBySymbol.get(listedStock.symbol())));
        download.markClosesRead(readAt);   // Hibernate writes the changes at commit
    }

    /** Every list in the order of {@link RecommendationList}; a list that fails fails the download. */
    private Map<RecommendationList, List<ScreenedStock>> downloadEveryList(LocalDate today) {
        Map<RecommendationList, List<ScreenedStock>> stocksByList = new EnumMap<>(RecommendationList.class);
        for (RecommendationList list : RecommendationList.values()) {
            stocksByList.put(list, screenedStocksOf(finvizScreens.stocksOf(finvizScreenOf(list), today)));
        }
        return stocksByList;
    }

    private void storeTheDownload(Map<RecommendationList, List<ScreenedStock>> stocksByList, Instant downloadedAt) {
        Set<String> symbols = stocksByList.values().stream()
                .flatMap(List::stream)
                .map(ScreenedStock::symbol)
                .collect(Collectors.toSet());
        downloadRepository.save(new StockListsDownloadEntity(FinvizClient.SOURCE_NAME, downloadedAt, stocksByList,
                listedStockRepository.knownConIdBySymbol(symbols)));
        String counts = stocksByList.entrySet().stream()
                .map(listAndStocks -> wordsOf(listAndStocks.getKey()) + " " + listAndStocks.getValue().size())
                .collect(Collectors.joining(", "));
        System.out.println("[recommendationlists] Finviz: " + counts + " stocks");
    }

    /** The screen of each list (decision 12): a new list without a screen doesn't compile. */
    private FinvizScreen finvizScreenOf(RecommendationList list) {
        return switch (list) {
            case FAST_GROWTH -> FinvizScreen.FAST_GROWTH;
            case SLOW_GROWTH -> FinvizScreen.SLOW_GROWTH;
            case VALUE -> FinvizScreen.VALUE;
        };
    }

    /** In Finviz's order; a symbol Finviz sends twice is kept once, as it first came. */
    private List<ScreenedStock> screenedStocksOf(List<FinvizStock> finvizStocks) {
        Map<String, ScreenedStock> stocksBySymbol = new LinkedHashMap<>();
        finvizStocks.forEach(finvizStock -> stocksBySymbol.putIfAbsent(ibSymbolOf(finvizStock.symbol()),
                screenedStockOf(finvizStock)));
        return List.copyOf(stocksBySymbol.values());
    }

    /** Finviz's row as the app keeps it: the same figures, the symbol as IB writes it. */
    private ScreenedStock screenedStockOf(FinvizStock finvizStock) {
        return new ScreenedStock(ibSymbolOf(finvizStock.symbol()), finvizStock.companyName(), finvizStock.sector(),
                finvizStock.industry(), finvizStock.country(), finvizStock.marketCapMillions(), finvizStock.price(),
                finvizStock.pe(), finvizStock.forwardPe(), finvizStock.peg(), finvizStock.priceToSales(),
                finvizStock.priceToBook(), finvizStock.priceToFreeCashFlow(),
                finvizStock.epsGrowthPast5YearsPercent(), finvizStock.salesGrowthPast5YearsPercent(),
                finvizStock.salesGrowthQuarterOverQuarterPercent(), finvizStock.grossMarginPercent(),
                finvizStock.operatingMarginPercent(), finvizStock.netProfitMarginPercent(), finvizStock.debtToEquity(),
                finvizStock.nextEarningsDate(), finvizStock.analystTargetPrice(),
                finvizStock.analystRecommendation());
    }

    /** Finviz writes a share class with a "-" where IB writes a space: "BRK-B", "BRK B". */
    private String ibSymbolOf(String finvizSymbol) {
        return finvizSymbol.replace('-', ' ');
    }

    /** "fast growth", for the console. */
    private String wordsOf(RecommendationList list) {
        return list.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
