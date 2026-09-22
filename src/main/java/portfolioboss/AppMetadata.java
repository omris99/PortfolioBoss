package portfolioboss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Application identity: name, version and a per-run id. The changelog below the class has one
 * entry per VERSION, newest first (see the conventions in CLAUDE.md).
 */
public final class AppMetadata {

    public static final String VERSION = "0.6.0";
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
 * VERSION 0.6.0: [Derived buy/sell dates and holding period]
 * HoldingHistory and TradeFact added (portfolioboss.domain, pure computation, no Spring): derive a holding's buy/sell dates and holding period from its trade rows instead of storing them.
 * The derivation resets on every full sell followed by a rebuy of the same holding (a new "episode"), so a stock sold and bought again later doesn't inherit an unrelated first-buy date.
 * A sell entered with nothing left to sell is ignored rather than rejected, on the assumption the matching buy just hasn't been entered yet.
 * HoldingRepository.findByAccountOrderById now loads a holding's trades in the same query (@EntityGraph) instead of one extra query per holding.
 * TradeResponse added; HoldingResponse gains firstBuyDate, lastSellDate, holdingDays and trades — the UI does not read them yet.
 * New tests: HoldingHistoryTest (pure, no Spring or database) and two PortfolioReadServiceTest scenarios that insert real trade rows and check the derived fields end to end.
 *
 * VERSION 0.5.0: [Sync at connection; the API reads from the database]
 * PortfolioSyncService added (portfolioboss.db): on every TWS connection, upserts holdings by (account, conId), refreshing IB's figures without touching sector or trades.
 * A holding TWS no longer reports is marked CLOSED instead of deleted (reversible if it reappears), so manually-entered sector and trades survive; one account_state row is replaced per sync.
 * Holding gains conId, IB's stable contract id, as its second field; PortfolioWrapper now keys holdings by conId instead of symbol, so two positions sharing a symbol no longer collide.
 * PortfolioReadService added; PortfolioController now serves GET /api/portfolio from it instead of from SnapshotStore (deleted): the API always reads the database, never TWS directly.
 * HoldingResponse gains id, conId, sector and status; the UI does not read them yet.
 * TwsPortfolioRunner no longer exits when TWS is unreachable: it skips the sync and serves the last sync's data instead, printing [db]; only a database write failure still exits.
 * portfolioboss.utils.Utils added (finiteOrNull, nanIfNull), replacing JsonNumbers: the same NaN-to-null rule now serves both the JSON responses and the stored database figures.
 * AccountStateRepository, SyncResult, PortfolioSyncServiceTest and PortfolioReadServiceTest added: the sync and read path are tested against the real portfolioboss_test database.
 *
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
