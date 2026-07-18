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
from the broker via `reqAccountUpdates`. It connects, prints the portfolio, and exits.

```
PortfolioBoss · read-only portfolio reader (Milestone 0)
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

---

## Roadmap

| Milestone | Goal |
|---|---|
| **0** ✅ | Read-only connect to IB, print real holdings + average cost. |
| 1 | Persist holdings + a **written thesis and status** per holding (Postgres/JPA); expose over REST. |
| 2 | React/TypeScript UI on real data (the "Horizon" demo is the visual target). |
| 3 | Analytics — concentration, sector exposure, benchmark vs. SPY, averaging-down calculator on real cost basis. |
| 4 | Fundamentals / earnings / dividends (Finnhub), rule-based alerts, decision journal. |

## Layout

```
src/main/java/portfolioboss/
├── Main.java              # entry point
├── ib/
│   ├── IbGateway.java     # IB socket connection + reader loop (read-only)
│   └── PortfolioWrapper.java  # EWrapper callbacks: reads holdings, prints, unsubscribes
└── model/
    └── Holding.java       # one holding (symbol, qty, avg cost, market value, P&L)
```

The directory layout is Maven-standard so Milestone 1 can add a build tool without moving files.
Milestone 0 builds with plain `javac` via `run.sh` — no build tool required yet.
