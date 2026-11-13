package portfolioboss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import portfolioboss.api.SyncService;
import portfolioboss.ui.UiLauncher;

import java.nio.file.Path;

/**
 * The startup flow: once the web server is up, syncs from TWS — the portfolio, the scan for hot stocks when one is due,
 * the holdings' contract details and analysts' price targets, the daily closes for the momentum, and what Finnhub knows
 * about the holdings — through {@link SyncService}, the same sync as the Sync now button, and opens the UI. If TWS is
 * unreachable, the sync is skipped and the app comes up anyway, serving whatever the last successful sync stored — an
 * empty database still answers 503, exactly like before any sync has ever happened.
 *
 * <p>This is a class of its own rather than a {@code @Bean} method on {@link Main} on purpose:
 * Spring's test slices load everything declared on the main class, and every
 * {@code ApplicationRunner} runs when a test context starts — a runner there connected to the real
 * TWS (and exited the JVM) from every test. Slices skip plain {@code @Component}s like this one.
 */
@Component
class TwsPortfolioRunner implements ApplicationRunner {

    private static final int UI_PORT = 5174;               // ui/vite.config.ts serves the dev UI here

    private final SyncService syncService;
    private final ApplicationContext applicationContext;
    private final int apiPort;

    private TwsPortfolioRunner(SyncService syncService,
                               ApplicationContext applicationContext,
                               @Value("${server.port}") int apiPort) {
        this.syncService = syncService;
        this.applicationContext = applicationContext;
        this.apiPort = apiPort;
    }

    /** The TWS port and client id {@code ./run.sh} passes are read by {@code TwsReader}, not here. */
    @Override
    public void run(ApplicationArguments arguments) throws InterruptedException {
        syncAtStartupOrExit();
        System.out.println("[api] serving the portfolio at http://localhost:" + apiPort + "/api/portfolio");
        new UiLauncher(Path.of("ui"), UI_PORT).launch();
    }

    /** Without the database the API has nothing to serve, so a failure here ends the program; TWS being off doesn't. */
    private void syncAtStartupOrExit() throws InterruptedException {
        try {
            syncService.syncAtStartup();
        } catch (DataAccessException | TransactionException e) {
            System.err.println("[db error] could not store the portfolio: " + e.getMessage());
            System.exit(SpringApplication.exit(applicationContext, () -> 1));
        }
    }
}
