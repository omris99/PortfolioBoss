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
import portfolioboss.ib.TwsReader;
import portfolioboss.ib.TwsReading;
import portfolioboss.ui.UiLauncher;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The startup flow: once the web server is up, reads the portfolio from TWS — and then, on the same connection, the
 * scans for hot stocks when one is due and the daily closes for the momentum — through {@link TwsReader}, stores it in
 * the database (which is where the API serves it from) and opens the UI. If TWS is unreachable, the sync is skipped and
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

    private final TwsReader twsReader;
    private final PortfolioSyncService syncService;
    private final ApplicationContext applicationContext;
    private final int apiPort;

    private TwsPortfolioRunner(TwsReader twsReader,
                               PortfolioSyncService syncService,
                               ApplicationContext applicationContext,
                               @Value("${server.port}") int apiPort) {
        this.twsReader = twsReader;
        this.syncService = syncService;
        this.applicationContext = applicationContext;
        this.apiPort = apiPort;
    }

    /** The TWS port and client id {@code ./run.sh} passes are read by {@link TwsReader}, not here. */
    @Override
    public void run(ApplicationArguments arguments) throws InterruptedException {
        TwsReading reading = twsReader.readPortfolio(isScanDue());
        if (reading != null) {
            storeInDatabase(reading);
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

    /** Without the database the API has nothing to serve, so a failure here ends the program. */
    private void storeInDatabase(TwsReading reading) {
        try {
            syncService.sync(reading.snapshot(), reading.dailyCloses(), reading.scan());
        } catch (DataAccessException | TransactionException e) {
            System.err.println("[db error] could not store the portfolio: " + e.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
