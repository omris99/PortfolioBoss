package portfolioboss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Application identity: name, version and a per-run id. The changelog below the class has one
 * entry per VERSION, newest first (see the conventions in CLAUDE.md).
 */
public final class AppMetadata {

    public static final String VERSION = "0.4.0";
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
 * VERSION 0.4.0: [PostgreSQL, Flyway and the database schema]
 * pom.xml / application.properties: JPA, Flyway and the PostgreSQL driver added; at startup the app now connects to the portfolioboss database, and exits with code 1 before reading TWS if PostgreSQL is down.
 * V1__portfolio_schema.sql added: the holding, trade and account_state tables, created by Flyway at startup; a migration that has run is never edited, a later change is a new V2__ file.
 * HoldingEntity / TradeEntity / AccountStateEntity added (portfolioboss.db): JPA mappings that Hibernate checks against the schema at startup; IB figures are DOUBLE PRECISION, hand-entered trade amounts NUMERIC.
 * HoldingRepository added: finds a holding by account and IB contract id, or by account and status; nothing reads or writes the tables yet, so the API and the console report are unchanged.
 * scripts/backup-db.sh added: dumps the database to ~/PortfolioBossBackups, outside the repo, through a .partial file so a failed dump never leaves a file that looks like a good backup.
 *
 * VERSION 0.3.0: [Maven and Spring Boot: Spring MVC API, first tests]
 * pom.xml added: Maven build (Java 21, Spring Boot 4.1.1, JUnit 5) replaces the javac build; protobuf-java is now an ordinary dependency instead of a jar directory on the classpath.
 * run.sh now wraps mvn spring-boot:run and registers the TWS API jar in the local Maven repository once, since the jar is not on Maven Central; usage (./run.sh 7497 102) is unchanged.
 * Main is now a Spring Boot application: TwsPortfolioRunner reads TWS after the web server is up, then stores the snapshot and opens the UI; it still exits at once when TWS is unreachable or silent.
 * PortfolioController / SnapshotStore replace ApiServer / PortfolioJson (deleted): Spring MVC serves the same GET /api/portfolio, now answering 503 until the snapshot has been read from TWS.
 * application.properties added: the API stays bound to 127.0.0.1:8080 and Spring's startup banner is turned off so it does not mix with the [ib] console lines.
 * PortfolioResponse / HoldingResponse added: Jackson writes the same JSON field names as before, so the UI is unchanged; JsonNumbers turns IB's NaN and infinity into null rather than the string NaN.
 * HoldingTest / PortfolioControllerTest added: the first JUnit tests — derived cost basis and P&L percent, and the UI's JSON contract (field names, null figures, 503, GET only); no TWS needed.
 *
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
