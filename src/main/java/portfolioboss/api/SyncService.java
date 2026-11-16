package portfolioboss.api;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import portfolioboss.api.response.SyncRunResponse;
import portfolioboss.calculation.ScanSchedule;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.TwsReader;
import portfolioboss.ib.TwsReading;
import portfolioboss.stocks.FinnhubClient;
import portfolioboss.stocks.Stock;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One sync from TWS, the same for its two callers — {@code ./run.sh}'s startup and the Sync now button
 * (MY_HOLDINGS_TODO.md, session 5א): whether a scan is due, then the reading from TWS through {@link TwsReader} — the
 * portfolio, the scan when due, the holdings' contract details and Dow Jones targets, the daily closes —, then what
 * Finnhub knows about the holdings in dollars, then all of it stored in one transaction
 * ({@link PortfolioSyncService}), then a {@link PortfolioSyncedEvent} for whoever reacts to a sync — the alerts —, then
 * the days of IB's Flex reports not stored yet ({@link FlexDownloadService}, CAPITAL_GAINS_TAX_TODO.md, session 1), then
 * the Bank of Israel's dollar rates of the settlement days that have none yet ({@link SettlementDayRatesService},
 * session 2), and last the capital gains tax they make, printed ({@link CapitalGainsTaxService}, session 3).
 *
 * <p>Not {@code @Transactional}: reading TWS, asking Finnhub, waiting for IB to prepare a Flex report and asking the Bank
 * of Israel take seconds, and no database connection should wait on them. Each store is a transaction of its own.
 */
@Service
public class SyncService {

    /** A time in the console, in the local time zone: "26.10 22:00". */
    private static final DateTimeFormatter CONSOLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    /**
     * Finnhub is asked only about holdings in dollars: on a foreign exchange IB's symbol is not Finnhub's, and may
     * belong to another, American company (MY_HOLDINGS_TODO.md, decision 10).
     */
    private static final String US_DOLLAR = "USD";

    private final TwsReader twsReader;
    private final PortfolioSyncService portfolioSyncService;
    private final FinnhubClient finnhubClient;
    private final ApplicationEventPublisher eventPublisher;
    private final FlexDownloadService flexDownloadService;
    private final SettlementDayRatesService settlementDayRatesService;
    private final CapitalGainsTaxService capitalGainsTaxService;

    public SyncService(TwsReader twsReader, PortfolioSyncService portfolioSyncService, FinnhubClient finnhubClient,
                       ApplicationEventPublisher eventPublisher, FlexDownloadService flexDownloadService,
                       SettlementDayRatesService settlementDayRatesService,
                       CapitalGainsTaxService capitalGainsTaxService) {
        this.twsReader = twsReader;
        this.portfolioSyncService = portfolioSyncService;
        this.finnhubClient = finnhubClient;
        this.eventPublisher = eventPublisher;
        this.flexDownloadService = flexDownloadService;
        this.settlementDayRatesService = settlementDayRatesService;
        this.capitalGainsTaxService = capitalGainsTaxService;
    }

    /**
     * {@code ./run.sh}'s startup. Waits for a reading already running to end. When TWS can't be read, says so and stores
     * nothing: the API serves whatever the last sync stored. A database failure is thrown — the startup exits on it.
     */
    public void syncAtStartup() throws InterruptedException {
        TwsReading reading = twsReader.readPortfolio(isScanDue());
        if (reading == null) {
            // TwsReader already printed why.
            System.out.println("[db] TWS unreachable; serving the portfolio from the last sync, if any");
            return;
        }
        storeWithFinnhubData(reading);
        downloadMissingFlexReports(reading.snapshot());
        downloadMissingDollarRates(reading.snapshot());
        printCapitalGainsTax();
    }

    /**
     * The Sync now button: what the startup reads, without restarting the app.
     *
     * @throws TwsBusyException        while another reading from TWS runs — 409 ({@link ApiErrorHandler})
     * @throws TwsUnavailableException when TWS can't be read — 503
     */
    public SyncRunResponse syncNow() throws InterruptedException {
        TwsReading reading = twsReader.readPortfolioUnlessBusy(isScanDue());
        storeWithFinnhubData(reading);
        downloadMissingFlexReports(reading.snapshot());
        downloadMissingDollarRates(reading.snapshot());
        printCapitalGainsTax();
        PortfolioSnapshot snapshot = reading.snapshot();
        return new SyncRunResponse(snapshot.asOf(), snapshot.holdings().size());
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
     * After the connection to TWS has closed: Finnhub, then everything stored together, then the news that a sync is
     * stored — the alerts are checked on it ({@link PortfolioSyncedEvent}).
     */
    private void storeWithFinnhubData(TwsReading reading) {
        portfolioSyncService.sync(reading, readFinnhubDataOfHoldings(reading.snapshot()));
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
     * The sync's day, by the local calendar: Finnhub's "today", and the day the Flex reports and the exchange rates are
     * downloaded up to.
     */
    private LocalDate syncDateOf(PortfolioSnapshot snapshot) {
        return LocalDate.ofInstant(snapshot.asOf(), ZoneId.systemDefault());
    }

    /**
     * What Finnhub knows about each holding in dollars — its next report and four of the company's figures
     * (MY_HOLDINGS_TODO.md, decision 10) —, every holding at once. Only the symbol leaves the app. Empty without the
     * key; a holding whose call failed is left out, and keeps what was stored before.
     */
    private Map<Integer, Stock> readFinnhubDataOfHoldings(PortfolioSnapshot snapshot) {
        if (!finnhubClient.hasApiKey()) {
            return Map.of();
        }
        LocalDate today = syncDateOf(snapshot);
        List<Holding> holdingsInDollars = snapshot.holdings().stream()
                .filter(holding -> US_DOLLAR.equals(holding.currency()))
                .toList();
        Map<Integer, Future<Stock>> pendingFinnhubDataByConId = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Holding holding : holdingsInDollars) {
                pendingFinnhubDataByConId.put(holding.conId(),
                        executor.submit(() -> finnhubClient.stockOrNullOnFailure(holding.symbol(), today)));
            }
        }   // closing the executor waits for every call to end

        Map<Integer, Stock> finnhubDataByConId = new LinkedHashMap<>();
        pendingFinnhubDataByConId.forEach((conId, pendingFinnhubData) -> {
            Stock finnhubData = pendingFinnhubData.resultNow();
            if (finnhubData != null) {
                finnhubDataByConId.put(conId, finnhubData);
            }
        });
        System.out.printf("[stocks] Finnhub answered for %d of %d holdings in dollars%n",
                finnhubDataByConId.size(), holdingsInDollars.size());
        return finnhubDataByConId;
    }
}
