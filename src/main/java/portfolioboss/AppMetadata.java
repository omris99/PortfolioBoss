package portfolioboss;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Application identity: name, version and a per-run id. The changelog below the class has one
 * entry per VERSION, newest first (see the conventions in CLAUDE.md).
 */
public final class AppMetadata {

    public static final String VERSION = "0.13.0";
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
 * VERSION 0.13.0: [Several investors in one account]
 * V4 migration: investor (one account owner, "Me", created by the migration), investor_cash_movement for deposits and withdrawals, and trade.investor_id; every trade entered before is the owner's.
 * InvestorSummaryCalculator added (domain): each investor's cash, shares value, total value, cost and P&L; the owner gets IB's figures minus the others', so the cards always add up to IB.
 * Another investor's cash = deposits − withdrawals − buys + sells − commissions, derived on every read; their cost is at average cost (HoldingHistory.heldCost); a missing price gives null, never a guess.
 * Profit is from the shares only, the same for everyone: unrealized in USD, realized per currency; dividends and interest stay in IB's cash, so they land on the account owner.
 * InvestorWarning added: trades without a price, trades not in USD, negative cash, more shares than IB reports, closed positions with no P&L to count — shown on the card, never blocking.
 * Closed positions are now per investor (PositionTrades splits a position's trades by investor): one investor selling out of a holding the other still holds is a closed position of their own.
 * GET /api/portfolio gains investors (the cards, with deposits and warnings), investorQuantities on each holding, and investorId on every trade and closed position.
 * InvestorWriteController added: POST/PUT /api/investors (a name already taken, ignoring case, is 409) and POST/PUT/DELETE for deposits; a deposit for the account owner is 400, their cash comes from IB.
 * TradeRequest and NewManualPositionRequest gain an optional investorId: a new trade without one is the owner's, a correction without one keeps its investor, an unknown id is 400.
 * InvestorsSummary added (UI): a card per investor under the account summary, rename, "+ Add investor" and a deposits panel; with the owner alone only "+ Add investor" shows.
 * With more than one investor: Qty in Positions reads "39 (15 · 24)" with names on hover, an Investor column in the trades and closed positions tables, and an Investor field in the trade forms.
 * New tests: InvestorSummaryCalculatorTest (with INVESTORS_TODO.md's worked example), PositionTradesTest, InvestorWriteControllerTest, InvestorWriteServiceTest; heldCost in HoldingHistoryTest.
 *
 * VERSION 0.12.0: [Average cost, partial sells and manual positions]
 * HoldingHistory: a period still held after a partial sell is now a closed position of the shares sold; before, only a sell back to zero counted. Tagged "partial · still holding N".
 * ClosedPosition: the shares sold are measured at average cost (each costs the average of the shares held then, with its part of the buy commissions); a closed period adds up exactly.
 * ClosedPositionResponse: quantity is now the shares sold, not bought; gains remainingQuantity and trades (the period's own trades); manualClosedPositionId becomes manualPositionId.
 * V3 migration: manual_position replaces manual_closed_position; its buys and sells are trade rows (of a holding or a manual position, never both); each old row became one buy and one sell.
 * ManualPositionWriteController replaces ClosedPositionWriteController: POST/PUT/DELETE /api/manual-positions and POST /api/manual-positions/{id}/trades; symbol and currency stored in capitals.
 * HoldingWriteService: PUT/DELETE /api/trades/{id} also correct a manual position's trades; its last sell can be corrected but not deleted or turned into a buy (409), so the position never vanishes.
 * Trade quantity must be a whole number: TradeRequest and NewManualPositionRequest reject 2.5 ("must be a whole number"), and the forms take whole numbers only; before, 6 decimal places passed.
 * ClosedPositionsTable: a chevron opens each row's trades, read-only for a holding and editable for a manual position (details, delete, add/edit/delete trades); the row's own edit/delete are gone.
 * NewManualPositionForm replaces ManualClosedPositionForm, with a commission for the buy and for the sell; TradeForm and TradesPanel now serve a holding or a manual position alike.
 * New tests: ManualPositionWriteControllerTest and ManualPositionWriteServiceTest replace the ClosedPositionWrite tests; average cost and partial sells in ClosedPositionTest and HoldingHistoryTest.
 *
 * VERSION 0.11.0: [Commissions and manual closed positions]
 * Trades gain a commission: optional when entered; without one the default for an order is stored, 1 cent a share with a $5 minimum (Utils.calculateOrderCommission, IBBot's rule).
 * V2 migration adds trade.commission and gives the trades entered before it the same default, so the column is never empty.
 * ClosedPosition: the realized P&L and its % now subtract the commissions of every buy and sell in the period; before, commissions were ignored. ClosedPositionResponse gains commissions.
 * Manual closed positions added: table manual_closed_position and POST/PUT/DELETE /api/manual-closed-positions, for a round trip sold before the first sync; JSON only, sell date never before buy date.
 * GET /api/portfolio serves them in closedPositions with source MANUAL, their id and note (holdingId null); one entered without a commission is charged the default for both orders.
 * TradeForm gains a Commission field (empty = the default, hint on hover) and TradesPanel a Commission column.
 * ClosedPositionsSection: an "Add closed position" form, and edit and delete for the rows entered by hand, tagged "manual"; a derived row says it is corrected through its holding's trades.
 * ClosedPositionsTable gains Commission and Note columns and the currency next to each realized P&L ("+40.00 USD"); percentages now show two decimals instead of one, in Positions too.
 * New tests: UtilsTest for the default commission, ClosedPositionWriteControllerTest and ClosedPositionWriteServiceTest; commissions in ClosedPositionTest, HoldingHistoryTest and the HoldingWrite tests.
 *
 * VERSION 0.10.1: [Over-sold closed positions]
 * ClosedPosition: a period that sold more shares than it bought no longer shows a realized P&L; the extra shares' proceeds counted as pure profit (+1,400 instead of +200). It is "—" now.
 * ClosedPosition: the average sell price is taken over the shares sold instead of the shares bought, so it is always a price the sells could have had (118, not 196.67).
 * ClosedPositionResponse gains warning: "Sold 25 shares but bought 15…", shown in an orange row under the position, always visible; the header counts "(n to check)" apart from "(n without prices)".
 * New test: ClosedPositionTest for a period sold beyond what it bought; HoldingHistoryTest, PortfolioControllerTest and PortfolioReadServiceTest cover the quantity sold and the warning.
 *
 * VERSION 0.10.0: [Closed positions]
 * ClosedPosition added (domain): a position bought and sold back to zero; it keeps the raw totals and derives the average prices, realized P&L and its %, null when a trade has no price.
 * HoldingHistory now splits the trades into position periods (formerly "episodes") and lists every closed one in closedPositions, including one that ended inside a holding still open today.
 * HoldingHistory: a sell that takes the quantity below zero now closes the period; before, the quantity stayed negative and the next buy did not start a new one. The quantity warning still points at it.
 * GET /api/portfolio gains closedPositions (ClosedPositionResponse): symbol, sector, dates, days held, quantity, average prices and realized P&L of every closed position, open and closed holdings alike.
 * ClosedPositionsTable added (UI): a "Closed positions" section below Positions, newest sale first, with the realized P&L totaled per currency and "—" plus a hint where prices are missing.
 * New tests: ClosedPositionTest for the math, HoldingHistoryTest for the split into periods, PortfolioReadServiceTest and PortfolioControllerTest for closedPositions in the JSON.
 *
 * VERSION 0.9.0: [Reconciliation warnings]
 * HoldingHistory.warnings added: checks the trades entered against IB and returns the one gap to fix first — no buy entered, a closed holding with no sell, or a quantity other than IB's (0.0001 tolerance).
 * HoldingWarning / HoldingWarningType added (domain): sent as they are in a new warnings list on each holding in GET /api/portfolio; a warning never blocks a write, IB stays the source of truth.
 * HoldingHistory.netQuantity now sums every trade entered, including a sell while flat that the dates ignore; before, it was the episode's running quantity, so such a sell never showed up as a gap.
 * HoldingWarnings added (UI): a triangle next to the symbol with the message on hover, and the same message at the top of the trades panel; "no buy entered yet" in yellow, the other two in orange.
 * App shows "n holdings need attention" under the Positions title, counting closed holdings too and noting "(n closed, hidden)", since a closed holding with no sell is hidden by default.
 * New tests: HoldingHistoryTest for each warning, their order, the decimal tolerance and a sell the dates ignore; PortfolioControllerTest and PortfolioReadServiceTest for warnings in the JSON.
 *
 * VERSION 0.8.0: [Sector and trades in the UI]
 * PositionsTable gains Sector, Bought, Last sold and Held columns; sorting keeps empty values ("—") last in both directions, and rows are keyed by holding id, so two holdings sharing a symbol no longer collide.
 * The UI types IB figures as number | null and shows a missing one as "—"; before, a price IB did not report rendered as 0.00, which looked like a real zero.
 * Held shows the holding period in its two largest units ("1y 1m", a month counted as 30 days), with the exact number of days on hover.
 * SectorCell added: the sector is edited in the table — Enter or leaving the field saves, Esc cancels, an empty field clears it — and the sectors already entered are offered as you type.
 * TradesPanel / TradeForm added: a chevron opens a holding's trades to add, correct or delete them; a holding with no trades starts as a BUY of the whole position at IB's average cost.
 * TradeForm rounds that prefilled average cost to 4 decimal places: IB's figure (188.2990476) has more than the 6 the API accepts, so the first prefilled trade was rejected.
 * apiClient added: the four write calls, named like the backend; a failed write shows the API's own reason, and every write reloads the portfolio (usePortfolio's retry is now reload).
 * App shows only open holdings, with a "Show closed (n)" toggle that adds the closed ones dimmed and tagged; the summary always totals open holdings; the API-unreachable message no longer mentions TWS.
 * HoldingHistory.holdingDays is never negative: a buy dated after the last sync (entered today while an older sync is served) counts as 0 days instead of -3.
 * TradeRequest: too many digits now reads "must have at most 6 decimal places (and 14 digits before the point)" instead of Hibernate Validator's "numeric value out of bounds".
 * New tests: HoldingHistoryTest for a buy dated after the last sync, and HoldingWriteControllerTest for a price with too many decimal places — the case the prefill hit.
 *
 * VERSION 0.7.0: [Write endpoints for the sector and trades]
 * HoldingWriteController added: PUT /api/holdings/{id}/sector, POST /api/holdings/{id}/trades, PUT and DELETE /api/trades/{id}; the API was GET-only, now it also writes hand-entered data, never anything at IB.
 * HoldingWriteService added: one transaction per write; the sector and note are trimmed and blank becomes null; holdings are never created or deleted here, only by the sync.
 * SectorRequest / TradeRequest added (api/request): checked by @Valid before the endpoint runs — date not in the future, positive quantity, limits matching the NUMERIC(20,6) and VARCHAR columns.
 * pom.xml: spring-boot-starter-validation added; without Hibernate Validator @Valid is silently ignored and invalid trades would reach the database.
 * ApiErrorHandler added: API errors are ProblemDetail JSON; a failed validation names the fields ("quantity: must be greater than 0") instead of Spring's generic text; an unknown id is 404.
 * Write endpoints accept JSON only and there is no CORS configuration, so another website can't make the browser write to the local API: text or form posts get 415, JSON needs a preflight nothing grants.
 * TradeRepository added; HoldingEntity.changeSector, the new TradeEntity constructor and TradeEntity.changeDetails are the only code that writes the hand-entered columns.
 * New tests: HoldingWriteControllerTest (status codes, messages, 415, no CORS) and HoldingWriteServiceTest (what is stored, against portfolioboss_test, incl. sector and trades surviving a re-sync).
 *
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
