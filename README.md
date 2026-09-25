# PortfolioBoss

A decision-support platform for a **long-term** investment portfolio held at Interactive Brokers —
the calm counterpart to the intraday IBBot trading bot. Where IBBot optimizes for reaction speed,
PortfolioBoss optimizes for **discipline**: every holding carries a written thesis and a health
status, and the tool actively protects you from the classic mistakes (averaging down into a broken
thesis, letting one position balloon, mistaking a daily wiggle for a signal).

It is **read-only by design.** PortfolioBoss reads your account from IB and never places, modifies,
or cancels an order. It runs as its own application with its own API client id, so it can connect to
TWS alongside the trading bot.

---

## Status — Milestone 1 in progress

Milestone 0 proved the one IB call the whole platform is built on: reading your actual holdings and
average cost from the broker via `reqAccountUpdates`. On top of it, PortfolioBoss now:

- syncs the holdings into a local PostgreSQL database on every connection — a position that
  disappears from TWS is marked closed, never deleted;
- serves that database to a small React UI that shows the positions table (the API never reads TWS
  directly, so the last sync is still there when TWS is off);
- lets you enter each holding's sector and buy/sell trades right in the table, stored through a
  local JSON API;
- derives each holding's buy date, last sell date and holding period from those trades.

```
PortfolioBoss v0.8.0 (RunID: 250920260933) · read-only portfolio reader
[ib] connecting to 127.0.0.1:7496 (clientId=101, read-only)
[ib] connection ready
[ib] account: U1234567

════════════════════════════════════════════════════════════════════════════
 PORTFOLIO  ·  account U1234567
════════════════════════════════════════════════════════════════════════════
SYMBOL            QTY     AVG COST         LAST      MKT VALUE     UNREAL P&L        %
────────────────────────────────────────────────────────────────────────────
NVDA              131       118.20       172.40      22,584.40      +7,100.20    +45.9%
...
────────────────────────────────────────────────────────────────────────────
TOTAL                                              104,159.00      +5,731.00
[db] synced 7 holdings (0 new, 7 updated, 0 closed)
[api] serving the portfolio at http://localhost:8080/api/portfolio
[ui] dev server started (pid 23221), log: ui/dev-server.log
[ui] opened http://localhost:5174
```

(Spring's own startup log lines are left out.)

## Prerequisites

- **JDK 21+** (matches IBBot).
- **Maven 3.9+** (`brew install maven`) — the build; `run.sh` calls it.
- **Node 20.19+** to run the UI in `ui/`.
- **IB TWS or Gateway running and logged in**, with *Configure → API → Enable ActiveX and Socket
  Clients* turned on.
- **PostgreSQL 17**, running: `brew install postgresql@17`, `brew services start postgresql@17`, then
  create two databases, `portfolioboss` and `portfolioboss_test` (tests only). Homebrew leaves the tools
  (`createdb`, `psql`) off the PATH; they are in `/opt/homebrew/opt/postgresql@17/bin`. Its default setup
  needs no password, and Flyway creates the tables when the app first starts.
- The TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (the same one IBBot uses). It is not on Maven
  Central, so `run.sh` registers it in your local Maven repository (`~/.m2`) the first time.

## Run

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

> **Client id:** the trading bot connects as client id `0`. PortfolioBoss defaults to `101` so both
> can be connected to TWS at the same time. If you change it, keep it distinct from the bot's.

Tests: `mvn test`. They need no TWS, but the database tests run against the `portfolioboss_test`
database, so PostgreSQL must be running.

## UI

`./run.sh` keeps running after it prints the portfolio. It syncs the portfolio into PostgreSQL, serves
it at `http://localhost:8080/api/portfolio` (loopback only), starts the UI's dev server, and opens
`http://localhost:5174` in the browser — the way IBBot launches its visualizer. Ctrl+C stops all of
it. The UI's dependencies need installing once:

```bash
cd ui && npm install
```

The dev server's output goes to `ui/dev-server.log`. If `npm run dev` is already running, `run.sh`
reuses it instead of starting another. The browser step uses macOS `open`.

If TWS can't be reached, `run.sh` prints the connection error, skips the sync and serves what the last
sync stored (a database that has never synced answers 503). If PostgreSQL isn't running it exits within
seconds, without trying TWS.

The page shows the portfolio as of the last sync and reloads it after every change you make; new
figures from IB need `run.sh` stopped (Ctrl+C) and run again. The UI is styled after IBBot's (React,
Vite, Tailwind, dark slate theme).

## Sector and trades

A holding's sector and its buy/sell trades are entered by hand and stored in PortfolioBoss's own
database — never sent to IB, and never overwritten by the sync. Bought, Last sold and Held are derived
from those trades.

- **Sector:** click the cell ("+ add" when empty), type, and press Enter. Esc cancels; an empty field
  clears it. The sectors you already entered are offered as you type.
- **Trades:** the ▸ at the start of a row opens the holding's trades, with a form to add one; the pencil
  corrects a trade, the bin deletes it. For a holding with no trades yet, the form starts as a buy of
  the whole position at IB's average cost (which includes commissions), so only the date is left.
- **Closed holdings** (sold in full at IB) are hidden; "Show closed" brings them back, dimmed, so their
  history can still be completed.

The UI uses a small local API, which can also be called directly. Bodies are JSON only; `{holdingId}`
and `{tradeId}` are the `id`s in `GET /api/portfolio`.

| Request | Body | Answer |
|---|---|---|
| `PUT /api/holdings/{holdingId}/sector` | `{"sector": "Technology"}` — empty clears it | 204 |
| `POST /api/holdings/{holdingId}/trades` | `{"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10, "price": 150.25, "note": "why"}` — `price` and `note` are optional | 201 + the trade |
| `PUT /api/trades/{tradeId}` | same as the POST | 200 + the trade |
| `DELETE /api/trades/{tradeId}` | — | 204 |

```bash
curl -X POST -H 'Content-Type: application/json' \
     -d '{"tradeDate":"2025-01-15","side":"BUY","quantity":10}' \
     localhost:8080/api/holdings/1/trades
```

An invalid request answers 400 with the reason (`"quantity: must be greater than 0"`), an unknown id
404. Holdings themselves are never created or deleted through the API — only the sync does that.

---

## Roadmap

| Milestone | Goal |
|---|---|
| **0** ✅ | Read-only connect to IB, print real holdings + average cost. |
| 1 | Persist holdings + a **written thesis and status** per holding (Postgres/JPA); expose over REST. *In progress: Maven + Spring Boot, the sync into PostgreSQL, derived buy/sell dates and holding period, and entering the sector and trades are done; the thesis is next.* |
| 2 | React/TypeScript UI on real data (the "Horizon" demo is the visual target). *Started: the positions table, with the sector and trades editable in it.* |
| 3 | Analytics — concentration, sector exposure, benchmark vs. SPY, averaging-down calculator on real cost basis. |
| 4 | Fundamentals / earnings / dividends (Finnhub), rule-based alerts, decision journal. |

## Layout

```
pom.xml                    # Maven build: Java 21, Spring Boot 4.1.1 (MVC, validation, JPA, Flyway), PostgreSQL driver, JUnit 5
src/main/java/portfolioboss/
├── Main.java              # Spring Boot entry point
├── TwsPortfolioRunner.java  # startup flow: read the portfolio from TWS, sync it into the database, open the UI
├── AppMetadata.java       # version, startup signature, and the changelog (newest entry first)
├── api/
│   ├── PortfolioController.java     # GET /api/portfolio on localhost (Spring MVC)
│   ├── PortfolioReadService.java    # builds that response from the database
│   ├── HoldingWriteController.java  # PUT / POST / DELETE for the sector and trades (JSON only)
│   ├── HoldingWriteService.java     # stores them in the database
│   ├── ApiErrorHandler.java         # errors as JSON, with the reason spelled out
│   ├── request/                     # what the UI sends: SectorRequest, TradeRequest (with their validation rules)
│   └── response/                    # what the UI receives: PortfolioResponse, HoldingResponse, TradeResponse
├── db/
│   ├── HoldingEntity.java, TradeEntity.java, AccountStateEntity.java  # JPA mappings of the tables
│   ├── HoldingStatus.java, TradeSide.java  # enums, stored by name
│   ├── HoldingRepository.java, TradeRepository.java, AccountStateRepository.java
│   └── PortfolioSyncService.java, SyncResult.java  # the sync at connection: create, refresh or close holdings
├── domain/
│   └── HoldingHistory.java, TradeFact.java  # buy/sell dates and holding period from the trades (pure, no Spring)
├── ib/
│   ├── IbGateway.java     # IB socket connection + reader loop (read-only)
│   └── PortfolioWrapper.java  # EWrapper callbacks: reads holdings, prints, unsubscribes
├── model/
│   ├── Holding.java       # one holding (symbol, contract id, qty, avg cost, market value, P&L)
│   └── PortfolioSnapshot.java  # account, timestamp, net liquidation, cash, holdings
├── ui/
│   └── UiLauncher.java    # starts the UI dev server and opens it in the browser
└── utils/
    └── Utils.java         # IB's NaN / infinity ↔ null, for the JSON and the database
src/main/resources/application.properties  # loopback address, port 8080, database connection
src/main/resources/db/migration/           # Flyway migrations (V1__portfolio_schema.sql)
src/test/java/portfolioboss/               # JUnit 5; the database tests use portfolioboss_test
scripts/backup-db.sh                       # dumps the database to ~/PortfolioBossBackups

ui/                        # React 19 + Vite + Tailwind; the positions table (sector and trades editable) and account summary
```

Built with Maven (`pom.xml`); `./run.sh` wraps `mvn spring-boot:run`.
