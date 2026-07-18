# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). Currently at
**Milestone 0**: a walking skeleton that connects to TWS, prints holdings, and exits.

[TODO.md](TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks. Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project.

## Build & run

No build tool yet (Maven arrives in Milestone 1 — the directory layout is already Maven-standard so
files won't need to move). [run.sh](run.sh) compiles with plain `javac` into `out/` and runs:

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

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

Four classes, one flow. The whole platform rests on a single IB call, `reqAccountUpdates`, which is
what delivers real quantity and **average cost** from the broker.

```
Main ──> IbGateway ──owns──> PortfolioWrapper ──> Holding
         (socket + reader loop)  (EWrapper callbacks)
```

The snapshot lifecycle, which is the part worth knowing:

1. `IbGateway.connect()` opens the socket and starts a **daemon** `ib-reader` thread pumping
   `EReader.processMsgs()` on each signal.
2. IB calls back `managedAccounts` → `PortfolioWrapper` takes the first account and issues
   `reqAccountUpdates(true, account)`.
3. `updatePortfolio` fires once per position (zero-quantity ones are removed as closed-out);
   `updateAccountValue` picks out only `NetLiquidation` and `TotalCashValue`.
4. `accountDownloadEnd` prints the report, **unsubscribes** (`reqAccountUpdates(false, …)` — we want
   a snapshot, not the ~3-minute live updates), and counts down a `CountDownLatch`.
5. `Main` is blocked on that latch with a 15s timeout, then disconnects and calls `System.exit(0)`
   (nothing non-daemon keeps the JVM alive).

`PortfolioWrapper extends DefaultEWrapper` so only the needed callbacks exist. IBBot's IB layer is
**not** importable here (its `IBTraderBot` *is* one giant `EWrapper`); this project writes its own
small wrapper on purpose.

Error handling note: `INFO_CODES` in `PortfolioWrapper` filters IB's informational status codes
(2104, 2106, …) to stdout; codes 502/504 mean connection failure and release the latch early so the
program reports a clear message instead of waiting out the timeout.

`Holding` is a record mirroring `updatePortfolio` exactly — the broker is the source of truth, so no
field is hand-entered. Derived math (`costBasis`, `unrealizedPnlPercent`) lives on the record.

## Conventions

- Console output uses `[ib]` prefixes for connection lifecycle, `[ib error]` for real errors.
- Reuse from IBBot happens by **copying self-contained pieces** (the Finnhub `NewsProvider`
  abstraction, its `ui/` React stack), not by shared modules — see the architecture decisions in
  TODO.md.
- Milestone 1 direction: Maven, Postgres/JPA (`holding` + `thesis` tables), Spring Boot REST. The
  **written thesis per holding** is the actual product, not the IB reader.
