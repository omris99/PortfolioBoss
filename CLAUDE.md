# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). Currently at
**Milestone 0** plus the first UI slice: it connects to TWS, prints holdings, then serves that
snapshot to a React UI (`ui/`) that shows the positions table.

[TODO.md](TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks. Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project.

The local API is covered by the same rule: `ApiServer` has one GET endpoint and binds to the
loopback interface only. Don't add an endpoint that changes anything, and don't expose it beyond
localhost (the connection to TWS and the API are both unencrypted, which is fine only while local).

## Build & run

No build tool yet (Maven arrives in Milestone 1 — the directory layout is already Maven-standard so
files won't need to move). [run.sh](run.sh) compiles with plain `javac` into `out/` and runs:

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

There are no tests yet; JUnit lands with Maven in Milestone 1.

**External dependency not in the repo:** the TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (shared
with IBBot). Compilation fails without it. `run.sh` also adds `~/DevTools/google-proto-buf/*.jar` to
the classpath if present.

**Running requires TWS/Gateway to be logged in** with *Configure → API → Enable ActiveX and Socket
Clients* on. Without it the program prints a connection error and exits — expect that when running
outside the user's trading hours rather than treating it as a code bug.

**Client id 101 is deliberate.** IBBot connects as client id `0`; TWS rejects duplicate ids, so
PortfolioBoss must keep a distinct one for both to be connected at once.

## Architecture

One flow, a few classes. The whole platform rests on a single IB call, `reqAccountUpdates`, which is
what delivers real quantity and **average cost** from the broker.

```
Main ──> IbGateway ──owns──> PortfolioWrapper ──> Holding, PortfolioSnapshot
  │      (socket + reader loop)  (EWrapper callbacks)
  ├────> ApiServer ──JSON──> ui/   (React; the dev server proxies /api to :8080)
  └────> UiLauncher ──starts──> ui/'s `npm run dev`, then opens the browser
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
5. `Main` is blocked on that latch with a 15s timeout, then disconnects. With a snapshot it starts
   `ApiServer` and returns — the HTTP server's non-daemon thread keeps the JVM alive until Ctrl+C.
   Without one (TWS unreachable or silent) it calls `System.exit(0)`, since the reader loop is a
   daemon thread and nothing else keeps the JVM alive.

`PortfolioWrapper extends DefaultEWrapper` so only the needed callbacks exist. IBBot's IB layer is
**not** importable here (its `IBTraderBot` *is* one giant `EWrapper`); this project writes its own
small wrapper on purpose.

Error handling note: `INFO_CODES` in `PortfolioWrapper` filters IB's informational status codes
(2104, 2106, …) to stdout; codes 502/504 mean connection failure and release the latch early so the
program reports a clear message instead of waiting out the timeout.

`Holding` is a record mirroring `updatePortfolio` exactly — the broker is the source of truth, so no
field is hand-entered. Derived math (`costBasis`, `unrealizedPnlPercent`) lives on the record.

`ApiServer` uses the JDK's built-in `HttpServer` (as IBBot's `ManualCommandServer` does) and serves
one pre-rendered snapshot. `PortfolioJson` writes the JSON by hand — the payload is flat, and
`NaN` figures become `null` — until Spring Boot/Jackson arrives in Milestone 1. Keep the endpoint's
shape (`GET /api/portfolio`) stable across that move so the UI doesn't change.

`UiLauncher` copies IBBot's `launchVisualizer()`: `bash -l -c "npm run dev"` in `ui/` (a login shell,
so `npm` is on the PATH), output to `ui/dev-server.log`, then macOS `open` once the port answers.
Things that are easy to break:
- It probes every address `localhost` resolves to, because Vite listens on `[::1]` only on this
  machine — a plain `127.0.0.1` probe never succeeds.
- A shutdown hook stops npm *and its child processes*; killing only npm leaves Vite holding the port.
- An already-running Vite is reused, and is neither restarted nor stopped on exit.
- Failures are logged as `[ui error]` and never stop the API.
- `Main.UI_PORT` must match `ui/vite.config.ts` (`strictPort` is on), and `ui/` is found relative to
  the working directory — which is why `run.sh` `cd`s to the project root before starting Java.

## Conventions

- Console output uses `[ib]` prefixes for connection lifecycle, `[ib error]` for real errors, and
  `[api]` for the local API, `[ui]` / `[ui error]` for the UI launcher.
- Readability over brevity, in Java and TypeScript alike: descriptive names (`holding`,
  `sortState`, `response`), never one-letter variables (the conventional `e` in a `catch` is fine),
  and small functions/components with a single job instead of long inline expressions.
- One entry point: `Main` owns the TWS host/port/client id and the single `IbGateway`. Don't add a
  second entry point or a second place that connects to TWS.
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
- Milestone 1 direction: Maven, Postgres/JPA (`holding` + `thesis` tables), Spring Boot REST. The
  **written thesis per holding** is the actual product, not the IB reader.
