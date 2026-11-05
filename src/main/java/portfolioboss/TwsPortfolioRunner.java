package portfolioboss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import portfolioboss.calculation.ScanSchedule;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.TwsReader;
import portfolioboss.ib.TwsReading;
import portfolioboss.stocks.FinnhubClient;
import portfolioboss.stocks.Stock;
import portfolioboss.ui.UiLauncher;

import java.nio.file.Path;
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
 * The startup flow: once the web server is up, reads the portfolio from TWS — and then, on the same connection, the
 * scans for hot stocks when one is due, the holdings' contract details and analysts' price targets, and the daily closes
 * for the momentum — through {@link TwsReader}, asks Finnhub about the holdings, stores it all in the database (which
 * is where the API serves it from) and opens the UI. If TWS is unreachable, the sync is skipped and
 * the app comes up anyway, serving whatever the last successful sync stored — an empty database still
 * answers 503, exactly like before any sync has ever happened.
 *
 * <p>This is a class of its own rather than a {@code @Bean} method on {@link Main} on purpose:
 * Spring's test slices load everything declared on the main class, and every
 * {@code ApplicationRunner} runs when a test context starts — a runner there connected to the real
 * TWS (and exited the JVM) from every test. Slices skip plain {@code @Component}s like this one.
 */
@Component
class TwsPortfolioRunner implements ApplicationRunner {

    /** A time in the console, in the local time zone: "26.10 22:00". */
    private static final DateTimeFormatter CONSOLE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());
    private static final int UI_PORT = 5174;               // ui/vite.config.ts serves the dev UI here
    /**
     * Finnhub is asked only about holdings in dollars: on a foreign exchange IB's symbol is not Finnhub's, and may
     * belong to another, American company (MY_HOLDINGS_TODO.md, decision 10).
     */
    private static final String US_DOLLAR = "USD";

    private final TwsReader twsReader;
    private final PortfolioSyncService syncService;
    private final FinnhubClient finnhubClient;
    private final ApplicationContext applicationContext;
    private final int apiPort;

    private TwsPortfolioRunner(TwsReader twsReader,
                               PortfolioSyncService syncService,
                               FinnhubClient finnhubClient,
                               ApplicationContext applicationContext,
                               @Value("${server.port}") int apiPort) {
        this.twsReader = twsReader;
        this.syncService = syncService;
        this.finnhubClient = finnhubClient;
        this.applicationContext = applicationContext;
        this.apiPort = apiPort;
    }

    /** The TWS port and client id {@code ./run.sh} passes are read by {@link TwsReader}, not here. */
    @Override
    public void run(ApplicationArguments arguments) throws InterruptedException {
        TwsReading reading = twsReader.readPortfolio(isScanDue());
        if (reading != null) {
            storeInDatabase(reading, readFinnhubDataOfHoldings(reading.snapshot()));
        } else {
            // TWS is down or silent; TwsReader already printed why. The database keeps whatever the last
            // successful sync stored, so the app still comes up and serves that instead of exiting.
            System.out.println("[db] TWS unreachable; serving the portfolio from the last sync, if any");
        }

        System.out.println("[api] serving the portfolio at http://localhost:" + apiPort + "/api/portfolio");
        new UiLauncher(Path.of("ui"), UI_PORT).launch();
    }

    /** Once a trading day ({@link ScanSchedule}); when it is not due yet, says why. */
    private boolean isScanDue() {
        Instant lastScannedAt = syncService.lastScannedAt().orElse(null);
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
     * After the connection to TWS has closed: what Finnhub knows about each holding in dollars — its next report and
     * four of the company's figures (MY_HOLDINGS_TODO.md, decision 10) —, every holding at once. Only the symbol leaves
     * the app. Empty without the key; a holding whose call failed is left out, and keeps what was stored before.
     */
    private Map<Integer, Stock> readFinnhubDataOfHoldings(PortfolioSnapshot snapshot) {
        if (!finnhubClient.hasApiKey()) {
            return Map.of();
        }
        LocalDate today = LocalDate.ofInstant(snapshot.asOf(), ZoneId.systemDefault());
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

    /** Without the database the API has nothing to serve, so a failure here ends the program. */
    private void storeInDatabase(TwsReading reading, Map<Integer, Stock> finnhubDataByConId) {
        try {
            syncService.sync(reading, finnhubDataByConId);
        } catch (DataAccessException | TransactionException e) {
            System.err.println("[db error] could not store the portfolio: " + e.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
