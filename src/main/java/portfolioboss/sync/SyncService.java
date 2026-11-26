package portfolioboss.sync;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import portfolioboss.calculation.recommendations.ScanSchedule;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.TwsReader;
import portfolioboss.ib.TwsReading;
import portfolioboss.recommendationlists.ListedStocksToRead;
import portfolioboss.recommendationlists.RecommendationListsDownloadService;
import portfolioboss.sources.CompanyFiguresSource;
import portfolioboss.sources.SourcesInOrder;
import portfolioboss.sources.Stock;
import portfolioboss.tax.CapitalGainsTaxService;
import portfolioboss.tax.FlexDownloadService;
import portfolioboss.tax.SettlementDayRatesService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One sync from TWS, the same for its two callers — {@code ./run.sh}'s startup and the Sync now button
 * (MY_HOLDINGS_TODO.md, session 5א): the Recommendations page's lists from Finviz once a trading day
 * ({@link RecommendationListsDownloadService}, RECOMMENDATIONS_FINVIZ_TODO.md, session 2) — before TWS, so that the same
 * connection finds their stocks —, whether a scan is due, then the reading from TWS through {@link TwsReader} — the
 * portfolio, the scan when due, the holdings' contract details and Dow Jones targets, the contracts of the lists' stocks,
 * the daily closes —, then the
 * company figures of the holdings in dollars (Finviz first, then Finnhub: RECOMMENDATIONS_FINVIZ_TODO.md, decision 9),
 * then all of it stored in one transaction
 * ({@link PortfolioSyncService}), then a {@link PortfolioSyncedEvent} for whoever reacts to a sync — the alerts —, then
 * the days of IB's Flex reports not stored yet ({@link FlexDownloadService}, CAPITAL_GAINS_TAX_TODO.md, session 1), then
 * the splits they brought ({@link StockSplitDetectionService}, STOCK_SPLITS_TODO.md), then the Bank of Israel's dollar
 * rates of the settlement days that have none yet ({@link SettlementDayRatesService},
 * session 2), and last the capital gains tax they make, printed ({@link CapitalGainsTaxService}, session 3).
 *
 * <p>Not {@code @Transactional}: reading TWS, asking for the company figures, waiting for IB to prepare a Flex report and
 * asking the Bank of Israel take seconds, and no database connection should wait on them. Each store is a transaction of
 * its own.
 */
@Service
public class SyncService {

    /** A time in the console, in the local time zone: "26.10 22:00". */
    private static final DateTimeFormatter CONSOLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    /**
     * The company figures are asked for only about holdings in dollars: on a foreign exchange IB's symbol is not
     * Finviz's or Finnhub's, and may belong to another, American company (MY_HOLDINGS_TODO.md, decision 10).
     */
    private static final String US_DOLLAR = "USD";

    private final TwsReader twsReader;
    private final PortfolioSyncService portfolioSyncService;
    private final SourcesInOrder<Stock> companyFigures;
    private final ApplicationEventPublisher eventPublisher;
    private final FlexDownloadService flexDownloadService;
    private final SettlementDayRatesService settlementDayRatesService;
    private final CapitalGainsTaxService capitalGainsTaxService;
    private final StockSplitDetectionService stockSplitDetectionService;
    private final RecommendationListsDownloadService recommendationListsDownloadService;

    /**
     * @param companyFiguresSources every source of the company figures, in their {@code @Order} — Spring hands them so
     */
    public SyncService(TwsReader twsReader, PortfolioSyncService portfolioSyncService,
                       List<CompanyFiguresSource> companyFiguresSources,
                       ApplicationEventPublisher eventPublisher, FlexDownloadService flexDownloadService,
                       SettlementDayRatesService settlementDayRatesService,
                       CapitalGainsTaxService capitalGainsTaxService,
                       StockSplitDetectionService stockSplitDetectionService,
                       RecommendationListsDownloadService recommendationListsDownloadService) {
        this.twsReader = twsReader;
        this.portfolioSyncService = portfolioSyncService;
        this.companyFigures = new SourcesInOrder<>("company figures", companyFiguresSources);
        this.eventPublisher = eventPublisher;
        this.flexDownloadService = flexDownloadService;
        this.settlementDayRatesService = settlementDayRatesService;
        this.capitalGainsTaxService = capitalGainsTaxService;
        this.stockSplitDetectionService = stockSplitDetectionService;
        this.recommendationListsDownloadService = recommendationListsDownloadService;
    }

    /**
     * {@code ./run.sh}'s startup. Waits for a reading already running to end. When TWS can't be read, says so and stores
     * nothing from IB — only the splits in the Flex reports stored before, if any is new: the API serves whatever the
     * last sync stored. A database failure of the sync is thrown — the startup exits on it.
     */
    public void syncAtStartup() throws InterruptedException {
        ListedStocksToRead listedStocksToRead = downloadTheListsWhenDue();
        TwsReading reading = twsReader.readPortfolio(isScanDue(), listedStocksToRead.symbolsToFind(),
                listedStocksToRead.conIdsToReadClosesOf());
        if (reading == null) {
            // TwsReader already printed why.
            System.out.println("[db] TWS unreachable; serving the portfolio from the last sync, if any");
            storeNewStockSplits();
            return;
        }
        storeWithCompanyFigures(reading);
        storeTheContractsOfTheListedStocks(listedStocksToRead, reading);
        downloadMissingFlexReports(reading.snapshot());
        storeNewStockSplits();
        downloadMissingDollarRates(reading.snapshot());
        printCapitalGainsTax();
    }

    /**
     * The Sync now button: what the startup reads, without restarting the app.
     *
     * @return the portfolio IB sent, as now stored
     * @throws TwsBusyException        while another reading from TWS runs
     * @throws TwsUnavailableException when TWS can't be read
     */
    public PortfolioSnapshot syncNow() throws InterruptedException {
        ListedStocksToRead listedStocksToRead = downloadTheListsWhenDue();
        TwsReading reading = twsReader.readPortfolioUnlessBusy(isScanDue(), listedStocksToRead.symbolsToFind(),
                listedStocksToRead.conIdsToReadClosesOf());
        storeWithCompanyFigures(reading);
        storeTheContractsOfTheListedStocks(listedStocksToRead, reading);
        downloadMissingFlexReports(reading.snapshot());
        storeNewStockSplits();
        downloadMissingDollarRates(reading.snapshot());
        printCapitalGainsTax();
        return reading.snapshot();
    }

    /**
     * Before TWS: the lists from Finviz when a download is due — never failing the sync —, then what the reading asks IB
     * for them: the symbols without a contract and the closes, once a trading day, Finviz connected or not.
     */
    private ListedStocksToRead downloadTheListsWhenDue() {
        Instant now = Instant.now();
        recommendationListsDownloadService.downloadWhenDue(now);
        return recommendationListsDownloadService.listedStocksToRead(now);
    }

    /**
     * After the store, which holds their closes: the contracts found for the lists' symbols, and when their closes were
     * read. A failure is printed and the sync, already stored, goes on: the next sync after the next US market close
     * reads them again.
     */
    private void storeTheContractsOfTheListedStocks(ListedStocksToRead listedStocksRead, TwsReading reading) {
        try {
            recommendationListsDownloadService.storeContractsFound(listedStocksRead, reading.conIdsFoundBySymbol(),
                    reading.snapshot().asOf());
        } catch (RuntimeException e) {
            System.err.println("[db error] could not store the contracts of the lists' stocks: " + e.getMessage());
        }
    }

    /** Once a trading day ({@link ScanSchedule}); when it is not due yet, says why. */
    private boolean isScanDue() {
        Instant lastScannedAt = portfolioSyncService.lastScannedAt().orElse(null);
        ScanSchedule scanSchedule = new ScanSchedule(lastScannedAt, Instant.now());
        boolean scanDue = scanSchedule.isScanDue();
        if (!scanDue) {
            System.out.println("[ib] scan skipped: the last scan, at " + CONSOLE_TIME_FORMAT.format(lastScannedAt)
                    + ", came after the latest US market close, at "
                    + CONSOLE_TIME_FORMAT.format(scanSchedule.latestMarketClose()));
        }
        return scanDue;
    }

    /**
     * After the connection to TWS has closed: the company figures, then everything stored together, then the news that a
     * sync is stored — the alerts are checked on it ({@link PortfolioSyncedEvent}).
     */
    private void storeWithCompanyFigures(TwsReading reading) {
        portfolioSyncService.sync(reading, readCompanyFiguresOfHoldings(reading.snapshot()));
        eventPublisher.publishEvent(new PortfolioSyncedEvent());
    }

    /**
     * The days of IB's Flex reports not stored yet, up to the sync's day ({@link FlexDownloadService}). After the store
     * and the alerts: a day's first sync waits for IB to prepare a report, and nothing before it needs one. A failure
     * here never fails the sync — {@code FlexDownloadService} prints it.
     */
    private void downloadMissingFlexReports(PortfolioSnapshot snapshot) throws InterruptedException {
        flexDownloadService.downloadWhatIsMissing(syncDateOf(snapshot));
    }

    /**
     * The splits the Flex reports brought, stored once ({@link StockSplitDetectionService}) — after the reports, and
     * before the tax that is translated by them. A failure is printed and the sync, already stored, goes on: the next
     * sync tries again.
     */
    private void storeNewStockSplits() {
        try {
            stockSplitDetectionService.storeNewSplits();
        } catch (RuntimeException e) {
            System.err.println("[db error] could not store the stock splits: " + e.getMessage());
        }
    }

    /**
     * The Bank of Israel's dollar rates of the settlement days that have none yet, up to the sync's day
     * ({@link SettlementDayRatesService}). After the Flex reports: they bring the account's settlement days. A failure
     * here never fails the sync — it is printed as {@code [exchangerates error]}.
     */
    private void downloadMissingDollarRates(PortfolioSnapshot snapshot) {
        settlementDayRatesService.downloadMissingDollarRates(syncDateOf(snapshot));
    }

    /**
     * The capital gains tax of every year, and our FIFO against IB's, in the console ({@link CapitalGainsTaxService}) —
     * after the Flex reports and the rates it is computed from. Like the alerts, a failure is printed and the sync, already
     * stored, goes on.
     */
    private void printCapitalGainsTax() {
        try {
            capitalGainsTaxService.printCapitalGainsTax();
        } catch (RuntimeException e) {
            System.err.println("[tax error] could not compute the capital gains tax: " + e.getMessage());
        }
    }

    /**
     * The sync's day, by the local calendar: the company figures' "today", and the day the Flex reports and the exchange
     * rates are downloaded up to.
     */
    private LocalDate syncDateOf(PortfolioSnapshot snapshot) {
        return LocalDate.ofInstant(snapshot.asOf(), ZoneId.systemDefault());
    }

    /**
     * The company figures of each holding in dollars — its next report and four of the company's figures
     * (MY_HOLDINGS_TODO.md, decision 10) —, from Finviz first and Finnhub for what Finviz didn't answer
     * ({@link SourcesInOrder}, which prints who answered). Only the symbols leave the app. Empty while no source is
     * connected; a holding no source answered for is left out, and keeps what was stored before.
     */
    private Map<Integer, Stock> readCompanyFiguresOfHoldings(PortfolioSnapshot snapshot) {
        if (!companyFigures.isAnySourceConnected()) {
            return Map.of();
        }
        List<Holding> holdingsInDollars = snapshot.holdings().stream()
                .filter(holding -> US_DOLLAR.equals(holding.currency()))
                .toList();
        List<String> symbols = holdingsInDollars.stream().map(Holding::symbol).toList();
        Map<String, Stock> companyFiguresBySymbol = companyFigures.answersFor(symbols, syncDateOf(snapshot));

        Map<Integer, Stock> companyFiguresByConId = new LinkedHashMap<>();
        for (Holding holding : holdingsInDollars) {
            Stock companyFiguresOfTheHolding = companyFiguresBySymbol.get(holding.symbol());
            if (companyFiguresOfTheHolding != null) {
                companyFiguresByConId.put(holding.conId(), companyFiguresOfTheHolding);
            }
        }
        return companyFiguresByConId;
    }
}
