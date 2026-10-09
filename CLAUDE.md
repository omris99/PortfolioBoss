# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). It connects to
TWS, syncs the holdings into a local PostgreSQL database on every connection, and serves that
database to a React UI (`ui/`) that shows the positions table — the API always reads from the
database, never straight from TWS. In the same table the user enters what IB doesn't know (a holding's
sector and its buy/sell trades), and below it the closed positions — derived from those trades, plus the manual
positions sold before PortfolioBoss saw them — and, above it, a card per investor for an account several people's
money shares (each one's cash, value and profit), all of which the UI writes to the same database through a few JSON
endpoints. It is a Maven / Spring Boot application (Java 21, Spring Boot 4.1.1).

[TODO.md](TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.
[HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) is the session-by-session plan for the next stretch
of Milestone 1 (Postgres, trades, holding details); its sessions 0 (Maven + Spring Boot), 1
(PostgreSQL, Flyway, schema, entities), 2 (sync at connection, API reads from the database), 3
(derived buy/sell dates and holding period), 4 (write endpoints for the sector and trades), 5 (UI: the new
columns and sorting), 6 (UI: entering the sector and trades) and 7 (reconciliation warnings) are done; 8–9 are
ideas for later. [CLOSED_POSITIONS_TODO.md](CLOSED_POSITIONS_TODO.md) came next: the shares sold, with their realized
P&L — sessions 1 (the list derived from the trades already entered), 2 (commission, manual closed positions) and 3
(their UI), 4 (average cost, partial sells, manual positions with trades of their own) and 5 (its UI) are done.
[INVESTORS_TODO.md](INVESTORS_TODO.md), several investors sharing one IB account (each one's cash and profit), followed:
its sessions 1 (schema, computation, read API), 2 (write endpoints for investors, deposits and a trade's investor),
3 (UI) and 4 (each investor's part of a shared position's profit) are done; its "out of scope" list holds the ideas for
later. [AI_ANALYSIS_TODO.md](AI_ANALYSIS_TODO.md) is next: for each holding, momentum from IB's daily closes, and an
analysis of analyst ratings and news — the server searches Tavily, Claude (Sonnet 5.5, no tools) only extracts JSON, and
the code picks the consensus source and a colored signal — run only from a button. Its sessions 0 (the experiment that
settled the model and the searches) and 1 (momentum: a 0–5 score from IB's daily closes, in `/api/portfolio`) are done,
and so is the momentum part of session 3's UI, brought forward (the Signal column and the momentum box in a holding's
expanded row); session 2 (the analysis backend) and the rest of session 3 are next. Its API keys go in the git-ignored
`config/local.env`, which Claude never reads or prints.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks and, for the momentum, the daily-bar ones (`historicalData` / `historicalDataEnd` — reads too). Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project.

The local API never touches the IB account either. It writes only to PortfolioBoss's own database — the
sector and trades the user enters, through `HoldingWriteController`, the manual positions with their trades,
through `ManualPositionWriteController`, and the investors with their deposits and withdrawals, through
`InvestorWriteController` — and never creates or deletes a holding (only the sync does). `PortfolioController` stays
GET-only (Spring answers 405 to every other method). The server binds to the loopback interface only (`server.address=127.0.0.1` in
`application.properties`), there is **no CORS configuration**, and every write endpoint with a body accepts
JSON only: a page on another site can make the browser send a request to localhost without asking first
only as text or a form (415 here), while JSON, PUT and DELETE need a CORS preflight that nothing grants.
Don't add CORS configuration, don't add an endpoint that writes anything other than PortfolioBoss's own
hand-entered data, and don't expose the API beyond localhost (the connection to TWS and the API are both
unencrypted, which is fine only while local).

## Build & run

Maven build ([pom.xml](pom.xml)): Java 21, Spring Boot 4.1.1 (`spring-boot-starter-webmvc` — Spring
Boot 4's name, not `-web`), JUnit 5. [run.sh](run.sh) is a thin wrapper around `mvn -q spring-boot:run`
(Maven from `brew install maven`); its arguments reach the app as Spring's non-option arguments:

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

`./run.sh` reads the portfolio from TWS once, prints it, syncs it into PostgreSQL, and then keeps
running: it serves the database at `http://localhost:8080/api/portfolio`, starts the UI's dev server,
and opens `http://localhost:5174` in the browser (stop everything with Ctrl+C). If TWS could not be
read this run, it serves whatever the previous sync stored instead (see "Running requires TWS" below).
The UI is a separate Vite app (Node 20.19+) whose dependencies must be installed once:

```bash
cd ui && npm install     # first time only
npm run dev              # manual alternative to the auto-start; proxies /api to :8080
npm run build            # tsc -b && vite build; the type check is the only UI check for now
```

The UI shows the portfolio as of the last sync. It reloads `/api/portfolio` after every write (sector,
trades, manual positions, investors, deposits), but new figures from IB need a new `run.sh` (a refresh button is a
later step).

Tests: `mvn -q test` (JUnit 5; no TWS, but the database tests need `portfolioboss_test` running — see
above). `PortfolioControllerTest` (`@WebMvcTest` + `MockMvc` + `@MockitoBean` on `PortfolioReadService`)
pins the JSON contract the UI depends on with made-up responses, no database needed; `HoldingTest` covers
the derived math; `PortfolioWrapperTest` feeds `PortfolioWrapper` IB callbacks directly, no socket — the portfolio's and
the daily closes' (an error ending one request, a request still running at the timeout, an unreadable bar date);
`MomentumTest` covers the momentum score on made-up series of closes (decision 5's two-day-drop example among them).
`PortfolioSyncServiceTest`, `PortfolioReadServiceTest`, `HoldingWriteServiceTest`,
`ManualPositionWriteServiceTest` and `InvestorWriteServiceTest` are `@DataJpaTest`s against the real
`portfolioboss_test` database (`src/test/resources/application-test.properties`, `@ActiveProfiles("test")`), each test
rolled back automatically. The write endpoints are tested in the same two halves: `HoldingWriteControllerTest`,
`ManualPositionWriteControllerTest` and `InvestorWriteControllerTest` (`@WebMvcTest`, service mocked) for status
codes, validation messages and the JSON-only rule, the `*WriteServiceTest`s for what is stored. `ClosedPositionTest`
and `HoldingHistoryTest` cover the average-cost math, `InvestorSummaryCalculatorTest` (INVESTORS_TODO.md's worked
example among them) and `PositionTradesTest` the split between investors, `OrderCommissionTest` the default commission,
`UtilsTest` the trimming of typed text. After a
change that tests depend on, prefer `mvn -q clean test`: a plain `mvn test` once skipped recompiling stale tests. There is no whole-app `@SpringBootTest`: it
would run `TwsPortfolioRunner` and connect to TWS. Trust `mvn`'s exit code, not the log: `-q` is silent on success, and
`target/surefire-reports/` keeps the reports of tests that have since been deleted.

Spring's own settings are in `src/main/resources/application.properties` (loopback address, port 8080,
startup banner off so it doesn't mix with the `[ib]` lines, the database connection, `ddl-auto=validate`,
`open-in-view=false`). The database user and password come from `PORTFOLIOBOSS_DB_USER` /
`PORTFOLIOBOSS_DB_PASSWORD`; unset, it is your macOS user with no password, which Homebrew's PostgreSQL
accepts.

**External dependency not in the repo:** the TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (shared
with IBBot). It is not on Maven Central, so `run.sh` registers it once in the local Maven repository
(`~/.m2/repository/com/interactivebrokers/tws-api/local`, this machine only) and the `pom.xml`
depends on `com.interactivebrokers:tws-api:local` (`local` is just a label). After replacing the jar
with a newer TWS API, delete that `local` directory so the next `run.sh` registers it again; plain
`mvn` works only after `run.sh` has registered the jar once. `protobuf-java` is an ordinary Maven
dependency now, so `~/DevTools/google-proto-buf/` is no longer used.

**Running requires TWS/Gateway to be logged in** with *Configure → API → Enable ActiveX and Socket
Clients* on for a fresh sync. Without it (or if TWS is silent), `TwsPortfolioRunner` prints the
connection error, skips the sync, and comes up anyway serving whatever the last successful sync
stored — an empty database (nothing has ever synced) still answers 503, same as before any sync.
The app itself never exits over TWS being unreachable; expect the stale-data case when running
outside the user's trading hours rather than treating it as a code bug.

**Running also requires PostgreSQL 17** (`brew install postgresql@17`, kept running with `brew services
start postgresql@17`) with two databases, `portfolioboss` and `portfolioboss_test` (tests only — never
point tests at the real one). The formula is keg-only, so `psql`, `createdb` and `pg_dump` are in
`/opt/homebrew/opt/postgresql@17/bin`, not on the PATH. Spring connects and runs the Flyway migrations
*before* `TwsPortfolioRunner` starts, so with PostgreSQL down the app exits with code 1 within seconds
(`Connection to localhost:5432 refused`, inside a long Spring stack trace) without touching TWS. Unlike a
TWS that is simply off, a stopped PostgreSQL is something to fix — a sync failure (a `DataAccessException`
or `TransactionException` from `PortfolioSyncService.sync`) is the only thing that still exits the app
(code 1, `[db error] ...`), because the database is required and TWS is not. To check the database side
without a live TWS run `./run.sh 7599`: nothing listens there, so it gets through Flyway and schema
validation and then serves whatever is already in the database (or 503 on a database that has never
synced) instead of exiting — stop it with Ctrl+C.

**Client id 101 is deliberate.** IBBot connects as client id `0`; TWS rejects duplicate ids, so
PortfolioBoss must keep a distinct one for both to be connected at once.

## Architecture

One flow, a few classes. The whole platform rests on a single IB call, `reqAccountUpdates`, which is
what delivers real quantity and **average cost** from the broker.

```
Main (Spring Boot: brings the web server up, then Spring runs the runner)
  └──> TwsPortfolioRunner ──> IbGateway ──owns──> PortfolioWrapper ──> Holding (+conId), PortfolioSnapshot,
        │                     (socket + reader loop)  (EWrapper callbacks)    DailyClose (a year per holding + SPY)
        ├──syncs──> PortfolioSyncService ──writes──> Postgres (holding · trade · account_state · daily_close)
        └──────────> UiLauncher ──starts──> ui/'s `npm run dev`, then opens the browser

PortfolioController ──reads── PortfolioReadService ──reads── Postgres      (independent of the flow above:
        │                     (builds api/response/*Response              driven by HTTP requests, not by TWS)
        │                      with calculation/HoldingHistory, InvestorSummaryCalculator, Momentum)
        └──JSON──> ui/ (React; the dev server proxies /api to :8080)

HoldingWriteController ──writes── HoldingWriteService ──writes── Postgres  (sector and trade rows only;
        ▲  (api/request/*Request, @Valid;                                 never IB, never a holding row
        │   ApiErrorHandler → ProblemDetail JSON)                         created or deleted)
        └──JSON── ui/'s sector cell and trade form (ui/src/lib/apiClient.ts), then a reload of /api/portfolio

ManualPositionWriteController ──writes── ManualPositionWriteService ──writes── Postgres  (manual_position rows and
        ▲                                                                          their trades; never a holding)
        └──JSON── ui/'s closed positions section (new manual position, its details and trades)

InvestorWriteController ──writes── InvestorWriteService ──writes── Postgres  (investor and investor_cash_movement
        ▲                                                                    rows; never the account owner's cash)
        └──JSON── ui/'s investor cards (add, rename) and deposits panel
```

The sync-at-connection lifecycle, which is the part worth knowing:

1. `IbGateway.connect()` opens the socket and starts a **daemon** `ib-reader` thread pumping
   `EReader.processMsgs()` on each signal.
2. IB calls back `managedAccounts` → `PortfolioWrapper` takes the first account and issues
   `reqAccountUpdates(true, account)`.
3. `updatePortfolio` fires once per position, keyed by **`conId`** (IB's stable contract id, not
   `symbol` — two positions can share a symbol); zero-quantity ones are removed as closed-out.
   `updateAccountValue` picks out only `NetLiquidation` and `TotalCashValue`.
4. `accountDownloadEnd` stores a `PortfolioSnapshot` (exposed by `IbGateway.snapshot()`), prints the
   report, **unsubscribes** (`reqAccountUpdates(false, …)` — we want a snapshot, not the ~3-minute
   live updates), and counts down a `CountDownLatch`.
5. `TwsPortfolioRunner` is blocked on that latch with a 15s timeout. With a snapshot, **still connected**, it asks
   for the daily closes (`readDailyCloses`): `IbGateway.requestDailyCloses` sends one `reqHistoricalData` per contract
   — every holding plus SPY (`calculation.Benchmark.SPY_CON_ID` = 756733, IB's fixed id, requested whether or not SPY is
   held), by conId + `SMART`, `"1 Y"` of `"1 day"` `TRADES` bars, regular hours, dates as `yyyyMMdd`, request ids from
   1000 — and waits up to 20s (`awaitDailyCloses`); late or failed contracts are just left out (`[ib] daily closes
   received for N of M contracts`). Then it disconnects and hands the snapshot and the closes to
   `PortfolioSyncService.sync(snapshot, dailyCloses)`, which upserts them into the database (see below). Either
   way — synced or not — it then launches the UI and returns; Tomcat's non-daemon threads keep the
   JVM alive until Ctrl+C. If the sync itself throws (a database problem, not a TWS problem) it prints
   `[db error]` and calls `System.exit(SpringApplication.exit(applicationContext, () -> 1))`: the database
   is required, TWS is not.

`TwsPortfolioRunner` is an `ApplicationRunner`, so Spring runs it once, **after the web server is
already listening** — a request that arrives while TWS is still being read (up to 15s) gets a 503 from
the controller, not an error. It is a `@Component` of its own rather than a `@Bean` method on `Main`
on purpose: Spring's test slices (`@WebMvcTest`) load everything declared on the main class and run
every `ApplicationRunner`, so a runner declared on `Main` connected to the real TWS from every test.
Slices skip plain `@Component`s.

`PortfolioWrapper extends DefaultEWrapper` so only the needed callbacks exist. IBBot's IB layer is
**not** importable here (its `IBTraderBot` *is* one giant `EWrapper`); this project writes its own
small wrapper on purpose.

Error handling note: `INFO_CODES` in `PortfolioWrapper` filters IB's informational status codes
(2104, 2106, …) to stdout; codes 502/504 mean connection failure and release the latch early so the
program reports a clear message instead of waiting out the timeout. An error carrying a daily-close request id (162, no
market data permission) ends that one request without closes (`[ib error] daily closes of contract …`) — the contract
keeps the closes of an earlier sync. The daily closes arrive on the reader thread while the startup thread waits, so
they sit in concurrent collections; `dailyCloses()` serves only requests that have ended (a timeout leaves a half-received
one out), and a bar whose date can't be read is skipped — an exception there would stop the reader loop's whole batch.

`Holding` is a record mirroring `updatePortfolio` exactly — the broker is the source of truth, so no
field is hand-entered — plus `conId`, IB's stable contract id, its second component. Derived math
(`costBasis`, `unrealizedPnlPercent`) lives on the record.

The database layer, `portfolioboss.db`, is now connected to the flow: `PortfolioSyncService.sync` runs
once per connection (called from `TwsPortfolioRunner`, step 5 above), and the API reads only from the
database, never straight from TWS. `HoldingEntity`, `TradeEntity` and `AccountStateEntity` map the tables
`holding`, `trade` and `account_state`, created by `src/main/resources/db/migration/V1__portfolio_schema.sql`.
`sync` upserts by `(account, conId)`: a holding reported by IB is created (`new HoldingEntity(account,
holding, syncedAt)`) or refreshed (`refreshFromIb` — IB's figures only, `status = OPEN`; it never touches
`sector` or `trades`); an open holding IB no longer reports is marked `CLOSED` (`markClosed`), never deleted,
so the sector and trades typed in by hand survive; a `CLOSED` holding that reappears is reopened
automatically. `AccountStateEntity` holds one row, replaced by a new instance (`new AccountStateEntity(snapshot)`)
on every sync. All of this runs in one `@Transactional` method, so a snapshot is stored whole or not at all.
The hand-entered side is written only by `api.HoldingWriteService`, `api.ManualPositionWriteService` and
`api.InvestorWriteService` (below), through `HoldingEntity.changeSector`, the `TradeEntity` constructors,
`changeDetails` and `changeInvestor`, `ManualPositionEntity`'s constructor and `changeDetails`, `InvestorEntity`'s
constructor and `changeName`, and `InvestorCashMovementEntity`'s constructor and `changeDetails`. `V2__closed_positions.sql` added `trade.commission`
(`NOT NULL`: a trade entered without one stores the default, `calculation.OrderCommission.defaultFor` — 1 cent a share,
at least $5 an order, IBBot's rule — worked out at write time; V2's own comment still calls it
`Utils.calculateOrderCommission`, its name then, since a migration that has run is never edited). `V3__manual_positions.sql` added `manual_position`
(`ManualPositionEntity`): a position PortfolioBoss never saw as a holding, sold before the first sync, kept apart from
`holding` because only the sync creates holdings. Its buys and sells are ordinary `trade` rows — a trade belongs to a
holding **or** a manual position (`holding_id` / `manual_position_id`, exactly one set, checked by the table) — so they
are computed and corrected the same way. V3 also turned V2's one-row-per-round-trip `manual_closed_position` into
manual positions (same ids, one buy and one sell) and dropped it. `V4__investors.sql` added `investor`
(`InvestorEntity`: a name, unique, and `is_account_owner` — at most one, the row "Me" the migration created) and
`investor_cash_movement` (`InvestorCashMovementEntity`: date, `CashMovementType` `DEPOSIT` / `WITHDRAWAL`, amount, note
— only for investors other than the owner), and made `trade.investor_id` `NOT NULL`, every trade entered before it
the owner's. `TradeEntity.investorId()` is a plain id, not a link to the entity: the computation needs only the id,
and the UI gets the names from `investors`. `V5__daily_closes.sql` added `daily_close` (`DailyCloseEntity`: `con_id`,
`bar_date`, `close_price` — an IB figure, so `DOUBLE PRECISION`; a generated `id` plus `UNIQUE (con_id, bar_date)`,
since a two-column key needs a separate key class in JPA): a year of daily closes per contract, for the momentum, keyed by
IB's contract id rather than by holding because SPY is read whether or not it is held. `sync(snapshot, dailyCloses)`
replaces in full the rows of every contract IB sent closes for — IB adjusts past prices for splits, so nothing old is
kept — and leaves the others alone; `sync(snapshot)` is the same with no closes. `HoldingRepository`, `TradeRepository`,
`ManualPositionRepository`, `InvestorRepository` (`findAllByOrderById` with the cash movements in the same query, and the
default method `accountOwner()`), `InvestorCashMovementRepository`, `AccountStateRepository` and `DailyCloseRepository`
(`findByConIdInOrderByConIdAscBarDateAsc`, and `deleteForContracts` — a hand-written `@Modifying @Query` bulk `DELETE`,
because a derived `deleteBy…` loads every row and deletes them one at a time) are the repositories.
Things that are easy to break:
- Flyway runs the migrations at startup, and a migration that has run is never edited (Flyway checks its
  checksum): a schema change is a new `V6__….sql`.
- A daily close IB sends twice for the same date is stored once (the later value): a duplicate would break
  `daily_close`'s `UNIQUE` constraint and, with it, the whole sync — which exits the app.
- `ddl-auto=validate` makes Hibernate check the entities against the tables at startup and change nothing;
  never set it to `update` or `create`.
- Figures from IB are `DOUBLE PRECISION` columns under `Double` fields; amounts typed in by hand
  (`trade.quantity`, `price`, `commission`) are `NUMERIC` under `BigDecimal`. A `NUMERIC` column under a `Double` field
  fails validation at startup (tried).
- `HoldingEntity` (a stored row), `calculation.Holding` (one IB reading) and `HoldingResponse` (what the UI gets)
  are three different things on purpose — `HoldingEntity.toIbHolding()` rebuilds a `Holding` from the stored
  row (`NULL` → `NaN`) so the derived math is never duplicated outside `Holding`.
- `scripts/backup-db.sh` dumps the database to `~/PortfolioBossBackups`, outside the repo.
- Database tests (`PortfolioSyncServiceTest`, `PortfolioReadServiceTest`, `HoldingWriteServiceTest`,
  `ManualPositionWriteServiceTest`, `InvestorWriteServiceTest`) are `@DataJpaTest`s against the real
  `portfolioboss_test` (see Build & run) — never PostgreSQL is mocked out.
- A migration that moves real data (V3, V4) is tried first on a scratch database holding a copy of the real one, and
  `scripts/backup-db.sh` runs before the `./run.sh` that applies it (for V4 the backup was also restored into a scratch
  database and compared table by table first). One that only adds a table (V5) still gets the backup first, checked
  for the same row counts as the database. A live check that writes test data (a made-up investor) runs against a
  scratch copy too, pointed at with `SPRING_DATASOURCE_URL`: there is no way to delete an investor.

`portfolioboss.calculation` (until 2026-10-08 two packages, `domain` and `model`, merged and renamed to say what it
holds) holds what IB reports — `Holding`, `PortfolioSnapshot`, `DailyClose`, `Benchmark` —, the enums the entities and
the JSON share with the computation — `TradeSide`, `HoldingStatus`, `CashMovementType` —, and the computation itself:
`HoldingHistory`, `ClosedPosition`, `TradeFact`, `HoldingWarning`, `HoldingWarningType`, for the investors
`PositionTrades`, `InvestorPart`, `CashMovementFact`, `InvestorSummary`, `InvestorSummaryCalculator`, `InvestorWarning` and
`InvestorWarningType`, for the momentum `Momentum` and `MomentumLabel`, and `OrderCommission` (the default commission) —
pure Java, no Spring and no database, so it is unit tested directly. Every other package uses it, and it uses none of
them but `utils` (see Conventions). A holding's
`firstBuyDate`, `lastSellDate`, `holdingDays` and closed positions are derived from its `trade` rows, never stored:
`HoldingHistory.of(List<TradeFact>)` walks them in chronological order (a buy before a sell on the same date) tracking
a running quantity, and splits them into **position periods** (the private record `PositionPeriod`): a buy while flat
opens one, and the sell that brings the quantity back to (near) zero — or below it, an over-sell the quantity check
then points at — closes it. A long-term holding is often sold in full and bought again later, and without this the
first-ever buy date would belong to an unrelated stretch of ownership. The dates come from the last period; every
period that **has a sell** — closed, or still open after a partial sell — becomes a `ClosedPosition` in
`HoldingHistory.closedPositions`: the shares sold so far, at **average cost** (chosen over FIFO in CLOSED_POSITIONS_TODO.md). The private
`AverageCostCalculator` walks the period's trades: each share sold costs the average of the shares held at that
moment and takes the same share of the buy commissions with it, and the sell back to zero takes whatever is left, so
a closed period adds up exactly. `ClosedPosition` keeps raw totals — dates, `boughtQuantity`, `soldQuantity`,
`soldCost`, `sellProceeds`, `commissions` (every sell's, plus the buys' part that left with the shares sold),
`remainingQuantity` (0 once closed) and `tradeIds` — from which it derives the average prices (buy over the shares
sold, sell over the shares sold) and `realizedPnl` with its percent of `soldCost`; `null` as soon as a sell, or a
buy before one, has no price, never a guess. A period that sold more than it bought (an over-sell) has no realized
P&L either — the extra shares' proceeds would count as pure profit — and `ClosedPosition.warning()` says so in
English. A sell entered while already flat is treated as a data-entry mistake and silently ignored, never rejected. `holdingDays` counts to `lastSellDate` when `CLOSED`, or to the last sync's date (`account_state.as_of`,
not the system clock, so the number doesn't drift between page loads) when `OPEN` — and is never negative: a
buy dated after that sync (entered today while serving an older sync, e.g. with TWS off) counts as 0 days. `HoldingRepository
.findByAccountOrderById`'s `@EntityGraph(attributePaths = "trades")` loads a holding's trades in the same
query instead of one extra query per holding (`ManualPositionRepository.findAllByOrderById` does the same);
`TradeEntity.toTradeFact()` reduces a row to what the computation needs (id, investor, date, side, quantity, price,
commission). `HoldingEntity.tradeHistory()` is the history of all of a holding's trades together — its dates and
warnings in `HoldingResponse`. `toPositionTrades()`, on `HoldingEntity` and `ManualPositionEntity` alike, builds a
`PositionTrades` (symbol, currency, the IB figures or `null` for a manual position, every trade) that splits them by
investor: `historyOf(investorId)` runs `HoldingHistory.of` on one investor's trades only, so the **closed positions are
per investor** (one investor can sell out of a holding the other still holds), and `quantitiesByInvestor` gives each
other investor the sum of their trades and the account owner IB's position minus those. `partsByInvestor` turns that
into an `InvestorPart` per investor (`quantity`, `sharesValue`, `sharesCost`, derived `unrealizedPnl` and its percent),
worked out the same way as the cards — another investor's value is their quantity × IB's market price and their cost
`heldCost`, the owner's are IB's value and cost minus the others' — so the parts add up to IB's row for the holding and,
over every holding, to each investor's card: `InvestorSummaryCalculator` sums the same `sharesValueOf` / `sharesCostOf`.

The investors (INVESTORS_TODO.md): the **account owner is the residual** — the owner's cash, total value and shares cost
are IB's minus the other investors', so the cards always add up to exactly what IB reports, missing trades or not.
Another investor's cash = deposits − withdrawals − buys + sells − commissions (`TradeFact.cashFlow()`,
`CashMovementFact`), derived on every read and never stored; their shares value is their quantity × IB's market price,
their cost `HoldingHistory.heldCost` (the average cost of the shares still held, from the same `AverageCostCalculator`).
Profit is from the shares only, the same for everyone: unrealized = value − cost, realized = their closed positions,
**per currency** (`realizedPnlByCurrency`); everything else is USD only. `InvestorSummaryCalculator` (its inputs as
record components: the owner's id, the investor ids, every `PositionTrades`, the cash movements, IB's cash and NAV)
returns an `InvestorSummary` per investor (`depositsMinusWithdrawals` — `null` for the owner —, `cash`, `sharesValue`,
`totalValue`, `sharesCost`, `realizedPnlByCurrency`, `warnings`; derived `unrealizedPnl`, its percent and `totalPnl`). A
missing price makes what depends on it `null`, never a guess. `InvestorWarning(type, message)` — several at once, shown,
never blocking: `TRADES_WITHOUT_PRICE`, `TRADES_NOT_IN_USD` (left out of the cash), `NEGATIVE_CASH`,
`MORE_SHARES_THAN_IB`, `CLOSED_POSITIONS_NOT_COUNTED` (no realized P&L to add). Dividends, interest and IB's own fees
are in IB's cash, so they land on the owner; they count toward no one's profit.

`HoldingHistory.warnings(status, ibPosition)` checks the trades entered against IB, which stays the source of truth
for the quantity — a gap is shown next to the holding, never a reason to reject a trade. It returns **at most one**
`HoldingWarning(type, message)`, the one to fix first: `NO_TRADES_LOGGED` (no buy at all), else `CLOSED_WITHOUT_SELL`
(a `CLOSED` holding whose current position period no sell ends), else `QUANTITY_MISMATCH` (`netQuantity` more than `0.0001`
from IB's `position`). Without that order a holding with no trades would also be a mismatch, the same gap twice.
Things that are easy to break:
- `netQuantity` is the plain sum of **every** trade (`TradeFact.signedQuantity()`), including a sell entered while
  flat that the dates ignore — so the quantity check still points at it.
- A `NaN` IB position skips the quantity check: `BigDecimal.valueOf(NaN)` throws, which would be a 500.
- Prices and average cost are never compared: IB's average cost includes commissions and would never match.

The momentum (AI_ANALYSIS_TODO.md, decision 5) is derived on every read from the stored daily closes, never stored:
`Momentum.of(closes, spyCloses)` (`null` without a single close) keeps the raw figures as record components — `asOf`,
`lastClose`, `sma20` / `sma50` / `sma200` (simple averages of the last 20 / 50 / 200 closes), `high20` (the highest of
the last 20), `oneMonthReturnPercent` and SPY's over the same calendar month (from the last close on or before the same
day a month earlier, so a weekend takes the Friday) — and derives five checks, a point each: `aboveSma20`, `aboveSma50`,
`sma50AboveSma200`, `nearHigh` (at most 10% below `high20`, inclusive) and `beatsSpy`; `score()` counts them and
`label()` maps it (`MomentumLabel`: `STRONG` 4–5, `NEUTRAL` 2–3, `WEAK` 0–1). A figure there are not enough closes for
is `null`, so is every check that needs it, and the score and label exist only when all five checks do — never a
guess. The private record `DailyCloses` sorts one contract's closes once and computes the figures from them, so only
`of` is static. Things that are easy to break:
- `beatsSpy` is a strict "greater than", so SPY itself can never score more than 4/5.
- The 20-day average and the one-month comparison react fast on purpose (an early warning after a drop): the label
  changes far more often than a 200-day rule would.

`PortfolioController` (Spring MVC) serves `GET /api/portfolio` through `PortfolioReadService`
(`@Transactional(readOnly = true)`, since `open-in-view=false` means entities must be read inside the
transaction): 503 until a sync has ever stored an `account_state` row, then 200 with `Cache-Control:
no-store` and a `PortfolioResponse` built from the latest sync — including closed holdings, which the UI
filters. `PortfolioReadService` puts the whole response together: its private methods build each holding
(`holdingResponseOf`), the closed positions and the investors' cards. The response records only hold the result — at
most a constructor that copies fields from one entity or one computed object (`TradeResponse(TradeEntity)`,
`MomentumResponse(Momentum)`, `InvestorResponse(InvestorEntity, InvestorSummary)`, …). They live in
`api/response/`; Jackson
turns them into JSON, so their component names **are** the JSON keys and must stay stable
(`ui/src/types/portfolio.ts` mirrors them) — add fields, don't rename or remove. Beyond the original IB
fields, `HoldingResponse` also carries `id`, `conId`, `sector`, `status`, and — derived via
`calculation.HoldingHistory`, see above — `firstBuyDate`, `lastSellDate`, `holdingDays` and `trades`
(a `List<TradeResponse>`, one entry per `trade` row), and `warnings` (a `List<calculation.HoldingWarning>`, see above);
the UI reads all of them. `HoldingWarning` and its enum go into the JSON as they are, with no `*Response` copy — the
same as `HoldingStatus` and `TradeSide` — so their names are JSON keys and values too.
`PortfolioResponse.closedPositions` (at the top level) is every holding's and then every manual position's closed
positions, each investor's apart, as `ClosedPositionResponse`s (`holdingId`, `symbol`, `currency`, `sector`, the
dates, `holdingDays`, `quantity` — the shares **sold** —, the average prices, `realizedPnl`, `realizedPnlPercent`,
`warning` — `null`, or the over-sell message, which the UI shows as it is in an orange row under the position —,
`commissions`, `source` (`ClosedPositionSource`: `TRADES` for a holding, `MANUAL`), `manualPositionId`, `note` (the
manual position's), `remainingQuantity`, `trades` — the period's own `TradeResponse`s, picked by `tradeIds` — and
`investorId`) — from open holdings as well as closed ones, since a holding still open today may have been sold in full
or in part before. The UI sums the realized P&L per currency — in all, per investor, and per position for its "By
investor" table (no endpoint does it). `PortfolioResponse.investors` (added last) is one
`InvestorResponse` per investor, the account owner first: `id`, `name`, `accountOwner`, the `InvestorSummary` figures
and its derived ones, `realizedPnlByCurrency` (a `{"HKD": 950, "USD": 500}` map), `cashMovements`
(`CashMovementResponse`s) and `warnings` (`calculation.InvestorWarning`, as it is). `HoldingResponse.investorQuantities`
(`InvestorQuantityResponse(investorId, quantity, sharesValue, sharesCost, unrealizedPnl, unrealizedPnlPercent)` — one
`InvestorPart` each, only investors holding some of it; the four figures were appended in INVESTORS_TODO.md session 4)
and `TradeResponse.investorId` were appended for the split. `HoldingResponse.momentum` (appended last) is a
`MomentumResponse` — `Momentum`'s figures, its five checks, `percentBelowHigh`, `score` and `label` (`MomentumLabel` goes
into the JSON as it is) — or `null` while no closes are stored for the holding: `PortfolioReadService` loads the closes of
every holding and of SPY in one query, and hands each holding's, with SPY's, to `Momentum.of`. `asOf` is an ISO-8601 string. `portfolioboss.utils.Utils.finiteOrNull` turns IB's `NaN` / infinity into
`null` for both JSON and the database (`nanIfNull` is the reverse, used by `toIbHolding()`); without it
Jackson writes the *string* `"NaN"`, which breaks the UI's `number | null` types. The port is `server.port`
in `application.properties`; `ui/vite.config.ts` proxies `/api` to it.

`HoldingWriteController` serves the writes through `HoldingWriteService` (`@Transactional`, the write-side
twin of `PortfolioReadService`): `PUT /api/holdings/{holdingId}/sector` (204), `POST
/api/holdings/{holdingId}/trades` (201 + the new trade as a `TradeResponse`), `PUT /api/trades/{tradeId}`
(200 + the trade) and `DELETE /api/trades/{tradeId}` (204) — the last two for any trade, a manual position's too.
`ManualPositionWriteController` / `ManualPositionWriteService` (same pattern) serve `POST /api/manual-positions`
(201 + a `ManualPositionResponse` with the id; the body, `NewManualPositionRequest`, carries the details plus a first
buy and first sell of the same quantity, so the position always has a sell to show — its cross-field rule, sell date
not before buy date, is an `@AssertTrue` private method), `PUT /api/manual-positions/{id}` (204, details only:
`ManualPositionRequest`), `DELETE /api/manual-positions/{id}` (204, its trades deleted with it) and `POST
/api/manual-positions/{id}/trades` (201, a `TradeRequest`). The symbol and currency of a manual position are stored in
capitals. A manual position's **last sell** can be corrected but not deleted or turned into a buy — it would vanish
from the closed positions — so `HoldingWriteService` answers that with 409 Conflict. `InvestorWriteController` /
`InvestorWriteService` serve `POST /api/investors` (201 + an `AddedInvestorResponse`) and `PUT
/api/investors/{investorId}` (204, a rename — the owner too) with an `InvestorRequest(name)`; a name already taken,
ignoring case, is 409, and the account owner is created only by V4, never by the API. `POST
/api/investors/{investorId}/cash-movements` (201), `PUT /api/cash-movements/{movementId}` (200) and `DELETE
/api/cash-movements/{movementId}` (204) take a `CashMovementRequest(movementDate, type, amount, note)`; a deposit for the
account owner is 400 — the owner's cash comes from IB. `TradeRequest` and `NewManualPositionRequest` gained an optional
`investorId`: a new trade without one is the account owner's, a **correction without one keeps the trade's investor**,
and an id that doesn't exist is 400 (`InvestorWriteService.investorOfTrade`, used by both trade services). After a
write the UI reloads all of `/api/portfolio`, so the endpoints stay small. The bodies are the `api/request/` records
(`SectorRequest`, `TradeRequest`, `NewManualPositionRequest`, `ManualPositionRequest`, `InvestorRequest`,
`CashMovementRequest`), checked by `@Valid` before the method runs, with
limits that mirror the columns (`@Digits(integer = 14, fraction = 6)` for a price or commission in `NUMERIC(20,6)`,
with its own readable message; a quantity is a **whole number** of shares, `@Digits(integer = 14, fraction = 0)` —
"must be a whole number (at most 14 digits)", which also rejects a raw `10.0` since it counts the decimals as
written; `@Size` for the `VARCHAR`s, `@PastOrPresent` dates). A commission left empty stores the default
(`OrderCommission.orDefault`); 0 is a commission too. The sector and the note are trimmed, and blank becomes `null`.
An unknown id is a `ResponseStatusException` with 404. `ApiErrorHandler` (`@RestControllerAdvice extends
ResponseEntityExceptionHandler`) makes every error a `ProblemDetail` JSON body (`status`, `title`, `detail`)
and overrides only the validation case, so that `detail` names the fields — `"quantity: must be greater than
0"`, sorted, in English (Hibernate Validator ships no Hebrew messages). The UI shows that `detail` as it is;
its write calls are all in `ui/src/lib/apiClient.ts`, named after the service's methods.
Things that are easy to break:
- `spring-boot-starter-validation` must stay in `pom.xml`: without it `@Valid` is silently ignored — no
  startup error, invalid input just reaches the database.
- IB's average cost spreads the commissions over every share, so it has more decimal places than the 6
  `@Digits` allows (`188.2990476`, found in the live check): the UI's `TradeForm` rounds it to 4 places when it
  prefills a price. Anything else that sends an IB figure as a trade amount has to round it too.
- `consumes = APPLICATION_JSON_VALUE` states the JSON-only rule, but it is not the only guard: a
  `@RequestBody` record can only be read from JSON, so another `Content-Type` gets 415 even without it (tried).

`UiLauncher` copies IBBot's `launchVisualizer()`: `bash -l -c "npm run dev"` in `ui/` (a login shell,
so `npm` is on the PATH), output to `ui/dev-server.log`, then macOS `open` once the port answers.
Things that are easy to break:
- It probes every address `localhost` resolves to, because Vite listens on `[::1]` only on this
  machine — a plain `127.0.0.1` probe never succeeds.
- A shutdown hook stops npm *and its child processes*; killing only npm leaves Vite holding the port.
- An already-running Vite is reused, and is neither restarted nor stopped on exit.
- Failures are logged as `[ui error]` and never stop the API.
- `TwsPortfolioRunner.UI_PORT` must match `ui/vite.config.ts` (`strictPort` is on), and `ui/` is found
  relative to the working directory — which is why `run.sh` `cd`s to the project root before
  starting Maven.

## Conventions

- Console output uses `[ib]` prefixes for connection lifecycle, `[ib error]` for real errors, `[api]`
  for the local API, `[ui]` / `[ui error]` for the UI launcher, and `[db]` / `[db error]` for the sync
  (`[db] synced N holdings (N new, M updated, K closed)` on success, then `[db] stored N daily closes for M contracts`;
  `[db] TWS unreachable; serving the portfolio from the last sync, if any` when TWS could not be read; `[db error]` only
  when the sync itself fails, which is also the only case that still exits the app). The daily closes print
  `[ib] requesting a year of daily closes for N contracts` and `[ib] daily closes received for N of M contracts`.
- Packages are named after their area, and where the area prints to the console its name is the prefix:
  `ib` / `[ib]`, `api` / `[api]`, `ui` / `[ui]`, `db` / `[db]`.
- Package dependencies point one way, toward `calculation`: `ib`, `db` and `api` use it, and it uses nothing of the
  app's but `utils` — no Spring, no database, no IB API. A type that both the computation and an entity or a request
  need (an enum like `TradeSide`) goes in `calculation`, not in `db`; before 2026-10-08 three enums sat in `db` and
  the two packages depended on each other. The records in `api.response` only hold the JSON's shape; putting them
  together is `PortfolioReadService`'s job, as private methods, not static factories on the records.
- Readability over brevity, in Java and TypeScript alike: descriptive names (`holding`,
  `sortState`, `response`), never one-letter variables (the conventional `e` in a `catch` is fine),
  and small functions/components with a single job instead of long inline expressions.
- One entry point: `Main` starts the Spring Boot app, and `TwsPortfolioRunner` owns the TWS
  host/port/client id and the single `IbGateway`. Don't add a second entry point or a second place
  that connects to TWS.
- API response types go in `portfolioboss.api.response`, request bodies in `portfolioboss.api.request`; the
  accessors, derived-math and write methods another package needs (`Holding.costBasis()`,
  `HoldingEntity.id()`/`sector()`/`trades()`/`toIbHolding()`/`changeSector()`, `TradeEntity`'s constructors and
  `changeDetails()`/`changeInvestor()`, `ManualPositionEntity`'s constructor and `changeDetails()`, `InvestorEntity`'s
  constructor and `changeName()`, `toPositionTrades()`, `TradeResponse(TradeEntity)` — built by the write services in
  `api` —, the other response records' copy constructors, which `PortfolioReadService` calls, …) are
  `public`. Everything internal to a class's own package — `HoldingEntity`'s and `AccountStateEntity`'s
  from-a-snapshot constructors, `refreshFromIb`, `markClosed`, the `PortfolioWrapper` methods `IbGateway` calls,
  `HoldingHistory.QUANTITY_TOLERANCE` — is marked `protected` rather than
  left as the unmarked package-private default: an explicit keyword is easier to spot while reading than the
  *absence* of one. (On a `record`, always `final`, or on a package-private top-level class, `protected` is
  exactly as reachable as package-private in practice — nothing outside the package can subclass either — so
  this is a readability choice, not a wider one.) What only Spring calls, by reflection, is `private`: the
  controllers' constructors and endpoint methods, and `TwsPortfolioRunner`'s constructor. JPA entities' empty
  constructors stay `protected`: Hibernate's lazy-loading stand-in subclass calls them (`private` broke tests). `static`
  only with a real reason — `main`, helper classes with a private constructor (`Utils`, `AppMetadata`, `Benchmark`,
  `OrderCommission`), a factory that has to compute before the record exists (`HoldingHistory.of`, `Momentum.of`) — and
  constants stay `static final`; a helper that doesn't touch fields is still an instance method, and a factory that only
  copies fields is a constructor. A helper used by more than one layer —
  `portfolioboss.utils.Utils`, shared by `api`, `db` and `calculation` — is the one exception to "narrow package,"
  and lives in its own package rather than being duplicated per layer.
- Changelog: a notable change bumps `AppMetadata.VERSION` and adds an entry at the top of the
  changelog comment below the class in `AppMetadata.java` (copied from IBBot). Format:
  `VERSION x.y.z: [short title]`, then one ` * ` line per cohesive change — lead with the
  class/feature name, say what changed and why, no dates, no sub-bullets — and a blank ` *` line
  before the previous version. `Main` prints `AppMetadata.getSignature()` on start. Write entries
  with the `/changelog` skill, which also reads new untracked files; it is user-invoked only and
  deliberately not part of the end-of-session updates.
- Reuse from IBBot happens by **copying self-contained pieces** (the Finnhub `NewsProvider`
  abstraction, its `ui/` React stack), not by shared modules — see the architecture decisions in
  TODO.md.
- Milestone 1 direction: Maven, Spring Boot REST, the PostgreSQL schema and entities, the sync at connection,
  deriving buy/sell dates and holding period from `trade` rows, the write endpoints for the sector and trades,
  the UI to show and enter them, the reconciliation warnings, the closed positions (commissions, average cost,
  manual positions) and the investors sharing the account are in, and so is the momentum score of AI_ANALYSIS_TODO.md;
  the API reads only from the database.
  HOLDING_DETAILS_TODO.md's sessions 8–9 (detected-change trade drafts, a stale-data banner) are optional
  ideas. The
  `thesis` table comes later. The **written thesis per holding** is the actual product, not the IB reader.
