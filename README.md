# PortfolioBoss

A decision-support tool for a **long-term** investment portfolio held at Interactive Brokers — the calm counterpart to
the intraday IBBot trading bot. Where IBBot optimizes for reaction speed, PortfolioBoss optimizes for **discipline**: for
every holding it gathers the data that helps decide whether to stay in it — its momentum, what the analysts say and do,
the news — and it shows the portfolio as a whole: how the money splits by sector and by holding, what was sold and for
how much, and each investor's part of an account several people's money shares. A fifth page looks further: the US
stocks in a long upward trend, and a recommendation picked by rules in code.

It is **read-only by design.** PortfolioBoss reads your account from IB and never places, modifies, or cancels an order.
It runs as its own application with its own API client id, so it can connect to TWS alongside the trading bot. What you
enter by hand — sectors, trades, investors — is stored in its own PostgreSQL database and never sent to IB.

---

## The five pages

- **Overview** — the account's figures, what needs a look (a trade missing, a holding IB no longer reports), how the
  signals stand, and the weights: a ring by sector and bars by holding next to the cash, on a tab for the whole
  portfolio and one per investor.
- **Positions** — the holdings, with the columns you pick. Each holding has a **signal** — green, yellow or red — from
  three things: its momentum, the analysts' trend and the news. A row opens the holding's drawer: Summary, Momentum,
  Analysts & news (and its Analyze button), Trades, Weight (the whole shares to sell or buy for a target weight) and,
  for a shared holding, By investor. The sector and the buy/sell trades are entered here.
- **Closed** — the shares sold, with their realized P&L at average cost, worked out from the trades entered; and the
  positions sold before PortfolioBoss first saw them, entered by hand.
- **Investors** — a card per investor: their cash, value and profit, and their deposits and withdrawals. The account
  owner's figures are what IB reports less everyone else's, so the cards always add up to the account.
- **Recommendations** — the US stocks IB's scan finds in a long upward trend, in three lists: Hot, Too hot (stretched
  far above the 50-day average) and Correcting. Every stock shows the analysts' target from Dow Jones's headlines.
  Analyze & recommend analyzes the first 10 hot stocks whose target is 15% or more above the price, and the code picks
  up to three — by their risk signs (Finnhub's company figures) and the news; Claude only extracts the facts.

## Where the data comes from

| What | From | When |
|---|---|---|
| Holdings, average cost, cash | IB (`reqAccountUpdates`) | every `./run.sh` |
| A year of daily closes → the momentum score (0–5) | IB | every `./run.sh` |
| The hot-stock scan, and the analysts' price targets in Dow Jones's headlines | IB (scanner, `reqHistoricalNews`) | once a US trading day at startup, or **Scan again** |
| The analysts' consensus and moves, the news | Tavily searches; Claude turns them into JSON, the code decides | only when you press **Analyze** (paid) |
| Earnings date, company figures (margin, debt, P/E, growth) | Finnhub | only on the Recommendations page's **Analyze** |
| Sector, trades, manual positions, investors and their deposits | you, in the UI | — |

What leaves the machine: Tavily gets a stock's symbol; Claude the symbol, the currency, the price, the date and the
search results; Finnhub only the symbol. Never a quantity, a cost, an investor or the account number.

## Prerequisites

- **JDK 21+** (matches IBBot).
- **Maven 3.9+** (`brew install maven`) — the build; `run.sh` calls it.
- **Node 20.19+** to run the UI in `ui/`.
- **IB TWS or Gateway running and logged in**, with *Configure → API → Enable ActiveX and Socket Clients* turned on.
- **PostgreSQL 17**, running: `brew install postgresql@17`, `brew services start postgresql@17`, then create two
  databases, `portfolioboss` and `portfolioboss_test` (tests only). Homebrew leaves the tools (`createdb`, `psql`) off
  the PATH; they are in `/opt/homebrew/opt/postgresql@17/bin`. Its default setup needs no password, and Flyway creates
  the tables when the app first starts.
- The TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (the same one IBBot uses). It is not on Maven Central, so `run.sh`
  registers it in your local Maven repository (`~/.m2`) the first time.
- **API keys, optional**, as `KEY=VALUE` lines in `config/local.env` (git-ignored; an environment variable of the same
  name wins). A missing key turns off only its part, and the app says so at startup:
  - `TAVILY_API_KEY` and `ANTHROPIC_API_KEY` — the analysis (Analyze). Tavily's free plan has 1,000 credits a month; a
    run over 7 holdings is about 35 credits and $0.30 of Claude.
  - `FINNHUB_API_KEY` — the earnings dates and company figures on the Recommendations page. Its free plan allows 60
    calls a minute; a stock is 2.

## Run

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

> **Client id:** the trading bot connects as client id `0`. PortfolioBoss defaults to `101` so both can be connected to
> TWS at the same time. If you change it, keep it distinct from the bot's.

`./run.sh` reads the portfolio from TWS once, scans for hot stocks when a scan is due, stores it all in PostgreSQL, and
then keeps running: it serves the database at `http://localhost:8080/api/portfolio` (loopback only), starts the UI's dev
server and opens `http://localhost:5174` in the browser. Ctrl+C stops all of it. With made-up numbers:

```
PortfolioBoss v0.27.0 (RunID: 021120262305) · read-only portfolio reader
[ib] connecting to 127.0.0.1:7496 (clientId=101, read-only)
[ib] connection ready
[ib] account: U1234567

════════════════════════════════════════════════════════════════════════════════════
 PORTFOLIO  ·  account U1234567
════════════════════════════════════════════════════════════════════════════════════
SYMBOL            QTY     AVG COST         LAST      MKT VALUE     UNREAL P&L        %
────────────────────────────────────────────────────────────────────────────────────
MSFT               40       412.30       468.15      18,726.00      +2,234.00    13.5%
COST               15       880.00       935.20      14,028.00        +828.00     6.3%
AMZN               60       182.75       214.40      12,864.00      +1,899.00    17.3%
ASML               12       905.10       742.60       8,911.20      -1,950.00   -18.0%
V                  25       268.40       301.80       7,545.00        +835.00    12.4%
GOOGL              30       165.20       196.10       5,883.00        +927.00    18.7%
PLTR              100        24.80        21.35       2,135.00        -345.00   -13.9%
────────────────────────────────────────────────────────────────────────────────────
TOTAL                                                70,092.20      +4,428.00

 Net liquidation : 82,500.00
 Cash            : 12,407.80
════════════════════════════════════════════════════════════════════════════════════

[ib] scanning for hot stocks: SCAN_histPerfRatio3m_DESC, SCAN_histPerfRatio6m_DESC
[ib] requesting the contract details of 74 stocks
[ib] scans found 74 stocks, 71 of them on NYSE or NASDAQ
[ib] left out, not on NYSE or NASDAQ: KRVW (ARCA), TAMX (BATS), ORLQ (ARCA)
[ib] requesting half a year of Dow Jones headlines for 71 stocks
[ib] analysts' price targets: 1980 in the Dow Jones headlines of 68 of 71 stocks
[ib] requesting a year of daily closes for 79 contracts
[ib] daily closes received for 79 of 79 contracts
[ib] disconnected
[db] synced 7 holdings (0 new, 7 updated, 0 closed)
[db] stored 19750 daily closes for 79 contracts
[db] stored a scan of 71 stocks
[db] stored 1980 analysts' price targets for 68 stocks
[api] serving the portfolio at http://localhost:8080/api/portfolio
[ui] dev server started (pid 23221), log: ui/dev-server.log
[ui] opened http://localhost:5174
[ai] analysis ready
```

(Spring's own startup lines and IB's market-data farm lines are left out.) The scan runs once a US trading day: when the
market hasn't closed since the last one, the scan's lines are replaced by `[ib] scan skipped: …`, and the
Recommendations page's **Scan again** reads a new one on demand.

If TWS can't be reached, `run.sh` prints the connection error, skips the sync and serves what the last sync stored (a
database that has never synced answers 503). If PostgreSQL isn't running it exits within seconds, without trying TWS.

Tests: `mvn -q test`. No test connects to TWS or calls Tavily, Claude or Finnhub, but the database tests run against
the `portfolioboss_test` database, so PostgreSQL must be running.

## UI

A React app in `ui/` (React 19, Vite, Tailwind, a light theme designed in Claude Design from
[DESIGN_BRIEF.md](DESIGN_BRIEF.md)). Its dependencies need installing once:

```bash
cd ui && npm install
npm run build        # the type check, and the build
```

`run.sh` starts its dev server (`npm run dev`, output in `ui/dev-server.log`), or reuses one already running. The page
shows the portfolio as of the last sync and reloads it after every change you make and every analysis; new figures from
IB need `run.sh` stopped (Ctrl+C) and run again. Every form and every delete opens in a dialog; nothing you enter
reaches IB, and the sync never overwrites it.

## The local API

The UI uses a small local API on `localhost:8080`, which can also be called directly. Bodies are JSON only (a page on
another site can't send JSON to it: there is no CORS configuration). The ids are those in `GET /api/portfolio`.

| Request | What it does |
|---|---|
| `GET /api/portfolio` | the holdings, closed positions, investors and account, as of the last sync |
| `GET /api/recommendations` | the latest scan's three lists and the latest recommendation |
| `PUT /api/holdings/{holdingId}/sector` | `{"sector": "Technology"}` — empty clears it |
| `POST /api/holdings/{holdingId}/trades` | `{"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10, "price": 150.25}` — `commission`, `note` and `investorId` are optional |
| `PUT /api/trades/{tradeId}`, `DELETE /api/trades/{tradeId}` | correct or delete a trade |
| `POST /api/manual-positions`, `PUT` / `DELETE /api/manual-positions/{id}`, `POST /api/manual-positions/{id}/trades` | positions sold before the first sync, and their trades |
| `POST /api/investors`, `PUT /api/investors/{investorId}` | add or rename an investor |
| `POST /api/investors/{investorId}/cash-movements`, `PUT` / `DELETE /api/cash-movements/{movementId}` | an investor's deposits and withdrawals |
| `POST /api/analysis` | **paid**: analyze every open holding (`{}`) or some (`{"holdingIds": [3]}`) |
| `POST /api/recommendations/analysis` | **paid**: analyze the short list and pick (`{}`), or one stock (`{"candidateId": 12}`) |
| `POST /api/scans` | `{}` — a new scan from TWS (read-only, free) |

```bash
curl -X POST -H 'Content-Type: application/json' \
     -d '{"tradeDate":"2025-01-15","side":"BUY","quantity":10,"price":150.25}' \
     localhost:8080/api/holdings/1/trades
```

An invalid request answers 400 with the reason (`"quantity: must be greater than 0"`), an unknown id 404. Holdings
themselves are never created or deleted through the API — only the sync does that.

---

## Plans

[TODO.md](TODO.md) is the initial plan (2026-07-18): the six principles every feature traces back to. Its first
principle, a written thesis per holding, was replaced on 2026-10-06 by data per holding. The work since is planned one
file at a time, session by session:

| Plan | What | State |
|---|---|---|
| [HOLDING_DETAILS_TODO.md](HOLDING_DETAILS_TODO.md) | Maven and Spring Boot, PostgreSQL, the sync, trades, warnings | done (two ideas left for later) |
| [CLOSED_POSITIONS_TODO.md](CLOSED_POSITIONS_TODO.md) | the shares sold and their realized P&L | done |
| [INVESTORS_TODO.md](INVESTORS_TODO.md) | several investors sharing one account | done |
| [AI_ANALYSIS_TODO.md](AI_ANALYSIS_TODO.md) | momentum, and the analysis of analysts and news | done |
| [RECOMMENDATIONS_TODO.md](RECOMMENDATIONS_TODO.md) | the Recommendations page | done but the measurement of the picks, which needs a month of prices (from about 2026-11-27) |
| [MY_HOLDINGS_TODO.md](MY_HOLDINGS_TODO.md) | what the holdings still lack: averaging down, the earnings date and the analysts' target, the holdings that move together, alerts, the return against SPY | current |

## Layout

```
pom.xml                    # Maven build: Java 21, Spring Boot 4.1.1 (MVC, validation, JPA, Flyway), PostgreSQL driver, JUnit 5
src/main/java/portfolioboss/
├── Main.java              # Spring Boot entry point
├── TwsPortfolioRunner.java  # startup flow: read TWS (and scan when due), store it, open the UI
├── AppMetadata.java       # version, startup signature, and the changelog (newest entry first)
├── api/                   # the controllers and their services: reads, writes, the analyses, the scan
│   ├── request/           # what the UI sends (with their validation rules)
│   └── response/          # what the UI receives: the shape of the JSON only
├── ai/                    # the analysis: Tavily's searches, the one call to Claude, the steps of a stock's analysis
├── stocks/                # Finnhub's client: earnings dates and company figures
├── calculation/           # only what computes — pure Java, no Spring, no database
├── model/                 # values the whole app shares: enums stored by name, warnings, labels
├── db/                    # the JPA entities and repositories, and the sync
├── ib/                    # the TWS connection (read-only), and the records of what IB reports
├── exceptions/            # TWS unreachable, TWS busy
├── ui/                    # UiLauncher: starts the UI's dev server and opens the browser
└── utils/                 # IB's NaN / infinity ↔ null, trimming typed text
src/main/resources/application.properties  # loopback address, port 8080, database connection, config/local.env
src/main/resources/db/migration/           # Flyway migrations (V1 … V12)
src/test/java/portfolioboss/               # JUnit 5; the database tests use portfolioboss_test
scripts/backup-db.sh                       # dumps the database to ~/PortfolioBossBackups

ui/src/
├── pages/                 # the five pages
├── components/            # tables, drawers, dialogs; graphs/ (the weights), weightCalculator/, recommendations/
├── hooks/                 # loading the data, running a scan or an analysis
├── lib/                   # the API client, formatting, shared styles
└── types/                 # the API's JSON, mirrored
```

Built with Maven (`pom.xml`); `./run.sh` wraps `mvn spring-boot:run`. [CLAUDE.md](CLAUDE.md) has the architecture in
detail.
