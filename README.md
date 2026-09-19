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

## Status — Milestone 0 (walking skeleton)

Proves the one IB call the whole platform is built on: reading your actual holdings and average cost
from the broker via `reqAccountUpdates`. It connects, prints the portfolio, and then serves that
snapshot to a small React UI that shows the positions table.

```
PortfolioBoss v0.2.0 (RunID: 190920261035) · read-only portfolio reader
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
```

## Prerequisites

- **JDK 21+** (matches IBBot).
- **Node 20.19+** to run the UI in `ui/`.
- **IB TWS or Gateway running and logged in**, with *Configure → API → Enable ActiveX and Socket
  Clients* turned on.
- The TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (the same one IBBot uses).

## Run

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

> **Client id:** the trading bot connects as client id `0`. PortfolioBoss defaults to `101` so both
> can be connected to TWS at the same time. If you change it, keep it distinct from the bot's.

## UI

`./run.sh` keeps running after it prints the portfolio. It serves the snapshot at
`http://localhost:8080/api/portfolio` (loopback only, GET only), starts the UI's dev server, and
opens `http://localhost:5174` in the browser — the way IBBot launches its visualizer. Ctrl+C stops
all of it. The UI's dependencies need installing once:

```bash
cd ui && npm install
```

The dev server's output goes to `ui/dev-server.log`. If `npm run dev` is already running, `run.sh`
reuses it instead of starting another. The browser step uses macOS `open`. If TWS can't be reached
the program exits before any of this happens.

The page shows the snapshot taken when `run.sh` started — to refresh it, stop `run.sh` (Ctrl+C) and
run it again. The UI is styled after IBBot's (React, Vite, Tailwind, dark slate theme).

---

## Roadmap

| Milestone | Goal |
|---|---|
| **0** ✅ | Read-only connect to IB, print real holdings + average cost. |
| 1 | Persist holdings + a **written thesis and status** per holding (Postgres/JPA); expose over REST. |
| 2 | React/TypeScript UI on real data (the "Horizon" demo is the visual target). *Started: the positions table.* |
| 3 | Analytics — concentration, sector exposure, benchmark vs. SPY, averaging-down calculator on real cost basis. |
| 4 | Fundamentals / earnings / dividends (Finnhub), rule-based alerts, decision journal. |

## Layout

```
src/main/java/portfolioboss/
├── Main.java              # entry point: read the portfolio, print it, serve it to the UI
├── AppMetadata.java       # version, startup signature, and the changelog (newest entry first)
├── api/
│   ├── ApiServer.java     # GET /api/portfolio on localhost (JDK HttpServer)
│   └── PortfolioJson.java # hand-written JSON for the snapshot
├── ib/
│   ├── IbGateway.java     # IB socket connection + reader loop (read-only)
│   └── PortfolioWrapper.java  # EWrapper callbacks: reads holdings, prints, unsubscribes
├── model/
│   ├── Holding.java       # one holding (symbol, qty, avg cost, market value, P&L)
│   └── PortfolioSnapshot.java  # account, timestamp, net liquidation, cash, holdings
└── ui/
    └── UiLauncher.java    # starts the UI dev server and opens it in the browser

ui/                        # React 19 + Vite + Tailwind; the positions table and account summary
```

The directory layout is Maven-standard so Milestone 1 can add a build tool without moving files.
Milestone 0 builds with plain `javac` via `run.sh` — no build tool required yet.
