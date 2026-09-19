package portfolioboss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Application identity: name, version and a per-run id. The changelog below the class has one
 * entry per VERSION, newest first (see the conventions in CLAUDE.md).
 */
public final class AppMetadata {

    public static final String VERSION = "0.2.0";
    public static final String APP_NAME = "PortfolioBoss";

    private static final String STARTUP_TIME = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyyyyHHmm"));

    /** For example {@code PortfolioBoss v0.2.0 (RunID: 190920261035)}; identifies one run in the output. */
    public static String getSignature() {
        return String.format("%s v%s (RunID: %s)", APP_NAME, VERSION, STARTUP_TIME);
    }

    public static String getVersion() {
        return VERSION;
    }

    private AppMetadata() {
    }
}


/*
 * Changelog:
 * VERSION 0.2.0: [First UI slice: local API, positions table, automatic launch]
 * PortfolioSnapshot added: account, timestamp, net liquidation, cash and holdings of one IB download; PortfolioWrapper stores it and IbGateway.snapshot() exposes it.
 * ApiServer / PortfolioJson added: GET /api/portfolio on the loopback interface only (JDK HttpServer, hand-written JSON, NaN becomes null); one read-only endpoint, other methods get 405.
 * Main no longer exits after printing: it disconnects, serves the snapshot through ApiServer until Ctrl+C, and still exits at once when TWS is unreachable or silent.
 * ui/ added: React 19 + Vite + Tailwind app in IBBot's dark style — account summary and a sortable positions table (same columns as the console) with loading, empty and error states.
 * The UI shows a static snapshot taken when Main starts; there is no refresh yet (re-run to update).
 * UiLauncher added, modeled on IBBot's launchVisualizer(): starts npm run dev, opens http://localhost:5174 once it answers, stops Vite with the JVM, reuses one that is already running.
 * UiLauncher readiness probe tries every address localhost resolves to, because Vite listens on IPv6 ([::1]) only on this machine and a 127.0.0.1 probe never succeeds.
 * run.sh now cd's to the project root so ui/ resolves from any directory; ui/vite.config.ts uses strictPort so the browser always opens the port the launcher expects.
 * AppMetadata added (copied from IBBot): VERSION, APP_NAME and a per-run RunID printed as the startup signature; this changelog lives below the class, newest entry first.
 *
 * VERSION 0.1.0: [Milestone 0: read-only portfolio reader]
 * IbGateway, PortfolioWrapper, Holding and Main added: read-only TWS connection that prints holdings with average cost, net liquidation and cash via reqAccountUpdates, then exits.
 */
