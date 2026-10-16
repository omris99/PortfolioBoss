package portfolioboss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.IbGateway;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ui.UiLauncher;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The startup flow: once the web server is up, reads the portfolio from TWS — and then, on the same connection, the
 * daily closes for the momentum — stores it in the database (which is where the API serves it from) and opens the UI. If TWS is unreachable, the sync is skipped and
 * the app comes up anyway, serving whatever the last successful sync stored — an empty database still
 * answers 503, exactly like before any sync has ever happened. It is the only place that connects to TWS,
 * and it owns the TWS host, port and client id. {@code ./run.sh 7497 102} arrives as the two non-option arguments (port, client id).
 *
 * <p>This is a class of its own rather than a {@code @Bean} method on {@link Main} on purpose:
 * Spring's test slices load everything declared on the main class, and every
 * {@code ApplicationRunner} runs when a test context starts — a runner there connected to the real
 * TWS (and exited the JVM) from every test. Slices skip plain {@code @Component}s like this one.
 */
@Component
class TwsPortfolioRunner implements ApplicationRunner {

    private static final String TWS_HOST = "127.0.0.1";
    private static final int DEFAULT_TWS_PORT = 7496;      // live TWS; use 7497 for paper
    private static final int DEFAULT_TWS_CLIENT_ID = 101;  // must differ from the trading bot (id 0)
    private static final int PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS = 15;
    private static final int DAILY_CLOSES_TIMEOUT_SECONDS = 20;
    private static final int UI_PORT = 5174;               // ui/vite.config.ts serves the dev UI here

    private final PortfolioSyncService syncService;
    private final ApplicationContext applicationContext;
    private final int apiPort;

    private TwsPortfolioRunner(PortfolioSyncService syncService,
                               ApplicationContext applicationContext,
                               @Value("${server.port}") int apiPort) {
        this.syncService = syncService;
        this.applicationContext = applicationContext;
        this.apiPort = apiPort;
    }

    @Override
    public void run(ApplicationArguments arguments) throws InterruptedException {
        List<String> positionalArguments = arguments.getNonOptionArgs();
        int twsPort = intArgumentOrDefault(positionalArguments, 0, DEFAULT_TWS_PORT);
        int twsClientId = intArgumentOrDefault(positionalArguments, 1, DEFAULT_TWS_CLIENT_ID);

        TwsReading reading = readFromTws(twsPort, twsClientId);
        if (reading != null) {
            storeInDatabase(reading);
        } else {
            // TWS is down or silent; readSnapshotFromTws already printed why. The database keeps whatever
            // the last successful sync stored, so the app still comes up and serves that instead of exiting.
            System.out.println("[db] TWS unreachable; serving the portfolio from the last sync, if any");
        }

        System.out.println("[api] serving the portfolio at http://localhost:" + apiPort + "/api/portfolio");
        new UiLauncher(Path.of("ui"), UI_PORT).launch();
    }

    /** Without the database the API has nothing to serve, so a failure here ends the program. */
    private void storeInDatabase(TwsReading reading) {
        try {
            syncService.sync(reading.snapshot(), reading.dailyCloses());
        } catch (DataAccessException | TransactionException e) {
            System.err.println("[db error] could not store the portfolio: " + e.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }

    private int intArgumentOrDefault(List<String> positionalArguments, int index, int defaultValue) {
        return positionalArguments.size() > index ? Integer.parseInt(positionalArguments.get(index)) : defaultValue;
    }

    /** What one connection read: the portfolio, and the daily closes of its holdings and of SPY (maybe none). */
    private record TwsReading(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses) {
    }

    /** {@code null} when the portfolio could not be read; the reason has been printed. */
    private TwsReading readFromTws(int twsPort, int twsClientId) throws InterruptedException {
        TwsReading reading = null;
        IbGateway gateway = new IbGateway();
        try {
            gateway.connect(TWS_HOST, twsPort, twsClientId);
            boolean portfolioReceived = gateway.awaitPortfolio(PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (gateway.connectionFailed()) {
                System.err.println("Connection was refused by TWS. Check the port and that API access is enabled.");
            } else if (!portfolioReceived) {
                System.err.println("Timed out after " + PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS + "s waiting for " +
                        "portfolio data. Is TWS running and logged in on port " + twsPort + "?");
            } else {
                PortfolioSnapshot snapshot = gateway.snapshot();
                reading = new TwsReading(snapshot, readDailyCloses(gateway, snapshot));
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } finally {
            gateway.disconnect();
        }
        return reading;
    }

    /**
     * Still connected, after the portfolio: a year of daily closes for every holding and for SPY, for the momentum —
     * SPY whether or not the account holds it. A contract whose closes fail or come late keeps the ones an earlier sync
     * stored; the portfolio is synced either way.
     */
    private Map<Integer, List<DailyClose>> readDailyCloses(IbGateway gateway, PortfolioSnapshot snapshot)
            throws InterruptedException {
        Set<Integer> conIds = new LinkedHashSet<>();
        snapshot.holdings().forEach(holding -> conIds.add(holding.conId()));
        conIds.add(Benchmark.SPY_CON_ID);
        gateway.requestDailyCloses(conIds);
        if (!gateway.awaitDailyCloses(DAILY_CLOSES_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.out.println("[ib] daily closes still missing after " + DAILY_CLOSES_TIMEOUT_SECONDS
                    + "s; keeping what arrived");
        }
        Map<Integer, List<DailyClose>> dailyCloses = gateway.dailyCloses();
        System.out.printf("[ib] daily closes received for %d of %d contracts%n", dailyCloses.size(), conIds.size());
        return dailyCloses;
    }
}
