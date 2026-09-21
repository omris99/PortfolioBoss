# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). Currently at
**Milestone 0** plus the first UI slice: it connects to TWS, prints holdings, then serves that
snapshot to a React UI (`ui/`) that shows the positions table. The PostgreSQL schema and JPA
entities are in place (`db/`), but nothing reads or writes them yet. It is a Maven / Spring Boot
application (Java 21, Spring Boot 4.1.1).

[TODO.md](TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.
[HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) is the session-by-session plan for the next stretch
of Milestone 1 (Postgres, trades, holding details); its sessions 0 (Maven + Spring Boot) and 1
(PostgreSQL, Flyway, schema, entities) are done.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks. Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project.

The local API is covered by the same rule: `PortfolioController` has one GET endpoint (Spring answers
405 to every other method) and the server binds to the loopback interface only
(`server.address=127.0.0.1` in `application.properties`). Don't add an endpoint that changes
anything, and don't expose it beyond localhost (the connection to TWS and the API are both
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

`./run.sh` reads the portfolio from TWS once, prints it, and then keeps running: it serves that
snapshot at `http://localhost:8080/api/portfolio`, starts the UI's dev server, and opens
`http://localhost:5174` in the browser (stop everything with Ctrl+C). The UI is a separate Vite app
(Node 20.19+) whose dependencies must be installed once:

```bash
cd ui && npm install     # first time only
npm run dev              # manual alternative to the auto-start; proxies /api to :8080
npm run build            # tsc -b && vite build; the type check is the only UI check for now
```

The UI shows the snapshot taken when `run.sh` started; refreshing means re-running `run.sh` (a
refresh button is a later step).

Tests: `mvn -q test` (JUnit 5; no TWS and, so far, no PostgreSQL — the `@WebMvcTest` slice does not
load the database). `PortfolioControllerTest` (`@WebMvcTest` + `MockMvc`)
pins the JSON contract the UI depends on with made-up holdings; `HoldingTest` covers the derived math.
Trust `mvn`'s exit code, not the log: `-q` is silent on success, and `target/surefire-reports/` keeps
the reports of tests that have since been deleted.

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
Clients* on. Without it the program prints a connection error and exits — expect that when running
outside the user's trading hours rather than treating it as a code bug.

**Running also requires PostgreSQL 17** (`brew install postgresql@17`, kept running with `brew services
start postgresql@17`) with two databases, `portfolioboss` and `portfolioboss_test` (tests only — never
point tests at the real one). The formula is keg-only, so `psql`, `createdb` and `pg_dump` are in
`/opt/homebrew/opt/postgresql@17/bin`, not on the PATH. Spring connects and runs the Flyway migrations
*before* `TwsPortfolioRunner` starts, so with PostgreSQL down the app exits with code 1 within seconds
(`Connection to localhost:5432 refused`, inside a long Spring stack trace) without touching TWS. Unlike a
TWS that is simply off, a stopped PostgreSQL is something to fix. To check the database side without a live
TWS — and without real holdings in the output — run `./run.sh 7599`: nothing listens there, so it gets
through Flyway and schema validation and then exits 0 with the usual TWS error.

**Client id 101 is deliberate.** IBBot connects as client id `0`; TWS rejects duplicate ids, so
PortfolioBoss must keep a distinct one for both to be connected at once.

## Architecture

One flow, a few classes. The whole platform rests on a single IB call, `reqAccountUpdates`, which is
what delivers real quantity and **average cost** from the broker.

```
Main (Spring Boot: brings the web server up, then Spring runs the runner)
  └──> TwsPortfolioRunner ──> IbGateway ──owns──> PortfolioWrapper ──> Holding, PortfolioSnapshot
        │                     (socket + reader loop)  (EWrapper callbacks)
        ├──stores──> SnapshotStore <──reads── PortfolioController ──JSON──> ui/
        │                                     (api/response/*Response)     (React; the dev server proxies /api to :8080)
        └──────────> UiLauncher ──starts──> ui/'s `npm run dev`, then opens the browser
```

The snapshot lifecycle, which is the part worth knowing:

1. `IbGateway.connect()` opens the socket and starts a **daemon** `ib-reader` thread pumping
   `EReader.processMsgs()` on each signal.
2. IB calls back `managedAccounts` → `PortfolioWrapper` takes the first account and issues
   `reqAccountUpdates(true, account)`.
3. `updatePortfolio` fires once per position (zero-quantity ones are removed as closed-out);
   `updateAccountValue` picks out only `NetLiquidation` and `TotalCashValue`.
4. `accountDownloadEnd` stores a `PortfolioSnapshot` (exposed by `IbGateway.snapshot()`), prints the
   report, **unsubscribes** (`reqAccountUpdates(false, …)` — we want a snapshot, not the ~3-minute
   live updates), and counts down a `CountDownLatch`.
5. `TwsPortfolioRunner` is blocked on that latch with a 15s timeout, then disconnects. With a snapshot
   it puts it in `SnapshotStore`, launches the UI and returns — Tomcat's non-daemon threads keep the
   JVM alive until Ctrl+C. Without one (TWS unreachable or silent) it calls
   `System.exit(SpringApplication.exit(context))`: closing the context stops the web server, and since
   the reader loop is a daemon thread the JVM exits right after.

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
field is hand-entered. Derived math (`costBasis`, `unrealizedPnlPercent`) lives on the record.

The database layer, `portfolioboss.db`, is built but **not connected to the flow yet** (the sync, and an
API that reads from it, are the next session). `HoldingEntity`, `TradeEntity` and `AccountStateEntity` map
the tables `holding`, `trade` and `account_state`, created by
`src/main/resources/db/migration/V1__portfolio_schema.sql`; `HoldingRepository` is the only repository so
far. Things that are easy to break:
- Flyway runs the migrations at startup, and a migration that has run is never edited (Flyway checks its
  checksum): a schema change is a new `V2__….sql`.
- `ddl-auto=validate` makes Hibernate check the entities against the tables at startup and change nothing;
  never set it to `update` or `create`.
- Figures from IB are `DOUBLE PRECISION` columns under `Double` fields; amounts typed in by hand
  (`trade.quantity`, `price`) are `NUMERIC` under `BigDecimal`. A `NUMERIC` column under a `Double` field
  fails validation at startup (tried).
- `HoldingEntity` (a stored row), `model.Holding` (one IB reading) and `HoldingResponse` (what the UI gets)
  are three different things on purpose.
- A holding that leaves TWS is meant to be marked `CLOSED`, never deleted, so the sector and trades typed
  in by hand survive (the sync will do it). `scripts/backup-db.sh` dumps the database to
  `~/PortfolioBossBackups`, outside the repo.

`PortfolioController` (Spring MVC) serves `GET /api/portfolio`: 503 until `SnapshotStore` holds the
snapshot, then 200 with `Cache-Control: no-store` and a `PortfolioResponse`. The response records
(`PortfolioResponse`, `HoldingResponse`) live in `api/response/`; Jackson turns them into JSON, so
their component names **are** the JSON keys and must stay stable (`ui/src/types/portfolio.ts` mirrors
them) — add fields, don't rename or remove. `asOf` is an ISO-8601 string. `JsonNumbers.finiteOrNull`
turns IB's `NaN` / infinity into `null`: without it Jackson writes the *string* `"NaN"`, which breaks
the UI's `number | null` types. `SnapshotStore` is temporary — the database replaces it. The port is
`server.port` in `application.properties`; `ui/vite.config.ts` proxies `/api` to it.

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

- Console output uses `[ib]` prefixes for connection lifecycle, `[ib error]` for real errors, and
  `[api]` for the local API, `[ui]` / `[ui error]` for the UI launcher.
- Packages are named after their area, and where the area prints to the console its name is the prefix:
  `ib` / `[ib]`, `api` / `[api]`, `ui` / `[ui]`, `db` / `[db]` (the `[db]` lines arrive with the sync).
- Readability over brevity, in Java and TypeScript alike: descriptive names (`holding`,
  `sortState`, `response`), never one-letter variables (the conventional `e` in a `catch` is fine),
  and small functions/components with a single job instead of long inline expressions.
- One entry point: `Main` starts the Spring Boot app, and `TwsPortfolioRunner` owns the TWS
  host/port/client id and the single `IbGateway`. Don't add a second entry point or a second place
  that connects to TWS.
- API response types go in `portfolioboss.api.response`; visibility stays as narrow as it can (a
  helper like `JsonNumbers` is package-private, and only the factory the controller calls is `public`).
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
- Milestone 1 direction: Maven, Spring Boot REST and the PostgreSQL schema and entities (`holding`,
  `trade`, `account_state`) are in; next is the sync at connection and an API that reads from the
  database, then trades and holding details — see HOLDING_DETAILS_TODO.md. The `thesis` table comes
  later. The **written thesis per holding** is the actual product, not the IB reader.
