# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). It connects to
TWS, syncs the holdings into a local PostgreSQL database on every connection, and serves that
database to a React UI (`ui/`) that shows the positions table — the API always reads from the
database, never straight from TWS. In the same table the user enters what IB doesn't know (a holding's
sector and its buy/sell trades), which the UI writes to the same database through a few JSON endpoints. It
is a Maven / Spring Boot application (Java 21, Spring Boot 4.1.1).

[TODO.md](TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.
[HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) is the session-by-session plan for the next stretch
of Milestone 1 (Postgres, trades, holding details); its sessions 0 (Maven + Spring Boot), 1
(PostgreSQL, Flyway, schema, entities), 2 (sync at connection, API reads from the database), 3
(derived buy/sell dates and holding period), 4 (write endpoints for the sector and trades), 5 (UI: the new
columns and sorting) and 6 (UI: entering the sector and trades) are done; 7–9 are ideas for later.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks. Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project.

The local API never touches the IB account either. It writes only to PortfolioBoss's own database — the
sector and trades the user enters, through `HoldingWriteController` — and never creates or deletes a
holding (only the sync does). `PortfolioController` stays GET-only (Spring answers 405 to every other
method). The server binds to the loopback interface only (`server.address=127.0.0.1` in
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
trades), but new figures from IB need a new `run.sh` (a refresh button is a later step).

Tests: `mvn -q test` (JUnit 5; no TWS, but the database tests need `portfolioboss_test` running — see
above). `PortfolioControllerTest` (`@WebMvcTest` + `MockMvc` + `@MockitoBean` on `PortfolioReadService`)
pins the JSON contract the UI depends on with made-up responses, no database needed; `HoldingTest` covers
the derived math; `PortfolioWrapperTest` feeds `PortfolioWrapper` IB callbacks directly, no socket.
`PortfolioSyncServiceTest`, `PortfolioReadServiceTest` and `HoldingWriteServiceTest` are `@DataJpaTest`s
against the real `portfolioboss_test` database (`src/test/resources/application-test.properties`,
`@ActiveProfiles("test")`), each test rolled back automatically. The write endpoints are tested in the same two
halves: `HoldingWriteControllerTest` (`@WebMvcTest`, service mocked) for status codes, validation messages and
the JSON-only rule, `HoldingWriteServiceTest` for what is stored. There is no whole-app `@SpringBootTest`: it
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
  └──> TwsPortfolioRunner ──> IbGateway ──owns──> PortfolioWrapper ──> Holding (+conId), PortfolioSnapshot
        │                     (socket + reader loop)  (EWrapper callbacks)
        ├──syncs──> PortfolioSyncService ──writes──> Postgres (holding · trade · account_state)
        └──────────> UiLauncher ──starts──> ui/'s `npm run dev`, then opens the browser

PortfolioController ──reads── PortfolioReadService ──reads── Postgres      (independent of the flow above:
        │                     (api/response/*Response,                    driven by HTTP requests, not by TWS)
        │                      +domain/HoldingHistory)
        └──JSON──> ui/ (React; the dev server proxies /api to :8080)

HoldingWriteController ──writes── HoldingWriteService ──writes── Postgres  (sector and trade rows only;
        ▲  (api/request/*Request, @Valid;                                 never IB, never a holding row
        │   ApiErrorHandler → ProblemDetail JSON)                         created or deleted)
        └──JSON── ui/'s sector cell and trade form (ui/src/lib/apiClient.ts), then a reload of /api/portfolio
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
5. `TwsPortfolioRunner` is blocked on that latch with a 15s timeout, then disconnects. With a snapshot
   it hands it to `PortfolioSyncService.sync`, which upserts it into the database (see below). Either
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
program reports a clear message instead of waiting out the timeout.

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
The hand-entered side is written only by `api.HoldingWriteService` (below), through `HoldingEntity.changeSector`,
the `TradeEntity` constructor and `TradeEntity.changeDetails`. `HoldingRepository`, `TradeRepository` and
`AccountStateRepository` are the repositories.
Things that are easy to break:
- Flyway runs the migrations at startup, and a migration that has run is never edited (Flyway checks its
  checksum): a schema change is a new `V2__….sql`.
- `ddl-auto=validate` makes Hibernate check the entities against the tables at startup and change nothing;
  never set it to `update` or `create`.
- Figures from IB are `DOUBLE PRECISION` columns under `Double` fields; amounts typed in by hand
  (`trade.quantity`, `price`) are `NUMERIC` under `BigDecimal`. A `NUMERIC` column under a `Double` field
  fails validation at startup (tried).
- `HoldingEntity` (a stored row), `model.Holding` (one IB reading) and `HoldingResponse` (what the UI gets)
  are three different things on purpose — `HoldingEntity.toIbHolding()` rebuilds a `Holding` from the stored
  row (`NULL` → `NaN`) so the derived math is never duplicated outside `Holding`.
- `scripts/backup-db.sh` dumps the database to `~/PortfolioBossBackups`, outside the repo.
- Database tests (`PortfolioSyncServiceTest`, `PortfolioReadServiceTest`, `HoldingWriteServiceTest`) are
  `@DataJpaTest`s against the real `portfolioboss_test` (see Build & run) — never PostgreSQL is mocked out.

`portfolioboss.domain` holds `HoldingHistory` and `TradeFact` — pure computation, no Spring and no
database, so it is unit tested directly. A holding's `firstBuyDate`, `lastSellDate` and `holdingDays` are
derived from its `trade` rows, never stored: `HoldingHistory.of(List<TradeFact>)` walks them in
chronological order (a buy before a sell on the same date) tracking a running quantity, and starts a fresh
**episode** every time a sell brings that quantity back to (near) zero — a long-term holding is often sold in
full and bought again later, and without this the first-ever buy date would belong to an unrelated stretch of
ownership. A sell entered while already flat is treated as a data-entry mistake and silently ignored, never
rejected. `holdingDays` counts to `lastSellDate` when `CLOSED`, or to the last sync's date (`account_state.as_of`,
not the system clock, so the number doesn't drift between page loads) when `OPEN` — and is never negative: a
buy dated after that sync (entered today while serving an older sync, e.g. with TWS off) counts as 0 days. `HoldingRepository
.findByAccountOrderById`'s `@EntityGraph(attributePaths = "trades")` loads a holding's trades in the same
query instead of one extra query per holding; `TradeEntity.toTradeFact()` reduces a row to what the
computation needs.

`PortfolioController` (Spring MVC) serves `GET /api/portfolio` through `PortfolioReadService`
(`@Transactional(readOnly = true)`, since `open-in-view=false` means entities must be read inside the
transaction): 503 until a sync has ever stored an `account_state` row, then 200 with `Cache-Control:
no-store` and a `PortfolioResponse` built from the latest sync — including closed holdings, which the UI
filters. The response records (`PortfolioResponse`, `HoldingResponse`) live in `api/response/`; Jackson
turns them into JSON, so their component names **are** the JSON keys and must stay stable
(`ui/src/types/portfolio.ts` mirrors them) — add fields, don't rename or remove. Beyond the original IB
fields, `HoldingResponse` also carries `id`, `conId`, `sector`, `status`, and — derived via
`domain.HoldingHistory`, see above — `firstBuyDate`, `lastSellDate`, `holdingDays` and `trades`
(a `List<TradeResponse>`, one entry per `trade` row); the UI reads all of them.
`asOf` is an ISO-8601 string. `portfolioboss.utils.Utils.finiteOrNull` turns IB's `NaN` / infinity into
`null` for both JSON and the database (`nanIfNull` is the reverse, used by `toIbHolding()`); without it
Jackson writes the *string* `"NaN"`, which breaks the UI's `number | null` types. The port is `server.port`
in `application.properties`; `ui/vite.config.ts` proxies `/api` to it.

`HoldingWriteController` serves the writes through `HoldingWriteService` (`@Transactional`, the write-side
twin of `PortfolioReadService`): `PUT /api/holdings/{holdingId}/sector` (204), `POST
/api/holdings/{holdingId}/trades` (201 + the new trade as a `TradeResponse`), `PUT /api/trades/{tradeId}`
(200 + the trade) and `DELETE /api/trades/{tradeId}` (204). After a write the UI reloads all of
`/api/portfolio`, so the endpoints stay small. The bodies are the `api/request/` records (`SectorRequest`,
`TradeRequest`), checked by `@Valid` before the method runs, with limits that mirror the columns
(`@Digits(integer = 14, fraction = 6)` for `NUMERIC(20,6)`, with its own readable message, `@Size` for the
`VARCHAR`s, `@PastOrPresent` trade date). The sector and the note are trimmed, and blank becomes `null`. An unknown id is a
`ResponseStatusException` with 404. `ApiErrorHandler` (`@RestControllerAdvice extends
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
  (`[db] synced N holdings (N new, M updated, K closed)` on success; `[db] TWS unreachable; serving the
  portfolio from the last sync, if any` when TWS could not be read; `[db error]` only when the sync
  itself fails, which is also the only case that still exits the app).
- Packages are named after their area, and where the area prints to the console its name is the prefix:
  `ib` / `[ib]`, `api` / `[api]`, `ui` / `[ui]`, `db` / `[db]`.
- Readability over brevity, in Java and TypeScript alike: descriptive names (`holding`,
  `sortState`, `response`), never one-letter variables (the conventional `e` in a `catch` is fine),
  and small functions/components with a single job instead of long inline expressions.
- One entry point: `Main` starts the Spring Boot app, and `TwsPortfolioRunner` owns the TWS
  host/port/client id and the single `IbGateway`. Don't add a second entry point or a second place
  that connects to TWS.
- API response types go in `portfolioboss.api.response`, request bodies in `portfolioboss.api.request`; the
  accessors, derived-math and write methods another package needs (`Holding.costBasis()`,
  `HoldingEntity.id()`/`sector()`/`trades()`/`toIbHolding()`/`changeSector()`, `TradeEntity`'s constructor and
  `changeDetails()`, `TradeResponse(TradeEntity)` — built by `HoldingWriteService` in `api`, …) are
  `public`. Everything internal to a class's own package — `HoldingEntity`'s and `AccountStateEntity`'s
  from-a-snapshot constructors, `refreshFromIb`, `markClosed`, `HoldingResponse.from`'s entity-to-response
  conversion, `TwsPortfolioRunner`'s own constructor — is marked `protected` rather than
  left as the unmarked package-private default: an explicit keyword is easier to spot while reading than the
  *absence* of one. (On a `record`, always `final`, or on a package-private top-level class, `protected` is
  exactly as reachable as package-private in practice — nothing outside the package can subclass either — so
  this is a readability choice, not a wider one.) A helper used by more than one layer —
  `portfolioboss.utils.Utils`, shared by `api.response` and `db` — is the one exception to "narrow package,"
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
  and the UI to show and enter them are in; the API reads only from the database. HOLDING_DETAILS_TODO.md's
  sessions 7–9 (reconciliation warnings, detected-change trade drafts, a stale-data banner) are optional
  ideas. The
  `thesis` table comes later. The **written thesis per holding** is the actual product, not the IB reader.
