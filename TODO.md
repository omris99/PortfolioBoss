# PortfolioBoss — Product & Build Plan

> Working plan for what we're building and in what order. Pairs with [README.md](README.md).

## 🎨 Visual target — the "Horizon" (אופק) demo

Interactive mock (fictional data) that illustrates the whole concept — the UX target for Milestone 2+:

**→ https://claude.ai/code/artifact/911ec711-5547-4a15-adb2-97a272c4137c**

Everything below is the plan to turn that mock into a real tool backed by your live IB account.

---

## Vision

The calm counterpart to IBBot. Where the trading bot optimizes for **reaction speed** on intraday
momentum, PortfolioBoss optimizes for **discipline** on long-term holdings. The platform's job is not
to show the most data — it's to protect the investor from the known psychological mistakes
(sunk-cost averaging-down, anchoring on entry price, mistaking a daily wiggle for a signal, hidden
concentration).

**Read-only, always.** It reads the account from IB and never places, modifies, or cancels an order.

---

## The six principles (what makes it valuable)

Every feature traces back to one of these. If a proposed feature doesn't, it probably belongs in
IBBot, not here.

1. **Written thesis per position** — the backbone. Each holding records *why I bought*, *what I
   expect*, and *what would make me sell*. This turns every future decision from an emotional
   question ("it dropped 20%, buy more?") into a factual one: **did the thesis break, or just the
   price?**
2. **Averaging-down guard** — the single most dangerous decision, given its own tool. Before you
   act it shows the new average, the new break-even, the capital required, **how much the position
   will balloon as a % of the portfolio**, and forces the honest question: *"if I held none of this,
   would I buy it today at this price?"* Averaging down is only legitimate when the thesis is intact.
3. **Portfolio picture, not stock picture** — concentration per position and per sector, correlated
   clusters (8 tech names that move together is not diversification), and a "market drops 20%"
   scenario.
4. **Event-cadence data, not ticks** — earnings dates, dividends, guidance changes, material news,
   basic fundamentals (revenue growth, margins, debt). Daily refresh is enough — a flashing screen
   pushes impulsive decisions. No real-time streaming.
5. **Rule-based alerts** — the platform calls you when there's a reason: a price level you set, an
   earnings date approaching, a position over its weight limit, a thesis not reviewed in 6 months.
   You don't sit and watch.
6. **Honest self-measurement** — portfolio return vs. SPY done correctly (time-weighted, neutralizes
   deposits, includes dividends), plus a **decision journal**: every buy/sell/average-down logged
   with its reason, so after six months you can ask "did my averaging-down decisions actually work?"

**Explicitly out of scope (for now):** price forecasts, heavy technical indicators, anything that
tries to *time the market* — those are short-term tools and already live in IBBot. Tax optimization
is a nice-to-have for later.

---

## Architecture decisions (settled 18.07.2026)

- **Separate project from IBBot**, not a module inside it. Opposite risk profile (read-only vs.
  real-money trading core); IBBot's IB layer isn't an importable library (`IBTraderBot` *is* the
  giant `EWrapper`); and this is the clean greenfield for the Maven→JUnit→Spring→Postgres learning
  path without touching the live bot.
- **Java backend + React/TypeScript frontend** — the same two-tier split as IBBot, because the hard
  already-solved parts are Java (IB connectivity) and the whole UI stack is React/TS. PortfolioBoss
  writes its own small `EWrapper` (only the callbacks it needs), rather than sharing IBBot's.
- **Reuse by copying self-contained pieces**, not shared modules: the Finnhub `NewsProvider`
  abstraction + config pattern, and a fork of IBBot's `ui/` React stack. Extract a shared library
  only if/when drift actually hurts.
- **Own IB client id (`101`)** so it can connect to TWS alongside the trading bot (which uses `0`).
- Reach for **Python** only if heavier quant shows up later (factor models, Monte Carlo). The core
  portfolio math (weights, weighted averages, correlation over daily returns) fits fine in Java.

---

## Data sources

| Need | Source | Status |
|---|---|---|
| Holdings + average cost + unrealized P&L | IB `reqAccountUpdates` → `updatePortfolio` | ✅ wired (Milestone 0) |
| Cash / buying power / net liquidation | IB `updateAccountValue` | ✅ wired (Milestone 0) |
| Prices (daily / snapshot — no streaming needed) | IB market data | later |
| Benchmark (SPY) history | IB `reqHistoricalData` (pattern already in IBBot's `MarketAnalyzerService`) | later |
| Fundamentals (revenue growth, margins, debt, P/E) | Finnhub `stock/metric` (extend IBBot's `NewsProvider` pattern) | later |
| Earnings dates / dividends | Finnhub calendar + dividends | later |
| News / catalysts | Finnhub (already built in IBBot) | later |
| **Time-weighted return** (needs historical NAV + cash flows) | IB **Flex Query** reports, or snapshot NAV daily and store | later — see note |

> **Note on TWR:** the honest performance metric needs a *history* of portfolio value and your
> deposits/withdrawals, which the live socket doesn't give. The proper source is IB Flex Query
> reports (downloadable NAV + cash-flow XML/CSV). Simple fallback: snapshot NAV once a day and store
> it ourselves.

---

## Milestones

### ✅ Milestone 0 — Walking skeleton (DONE 18.07.2026)
Read-only connect to IB, print real holdings + average cost. Proves `reqAccountUpdates`.
Compiles clean against the real IB jar; **not yet run against live TWS** — needs your TWS running.
- [x] `IbGateway` (socket + reader loop, read-only)
- [x] `PortfolioWrapper extends DefaultEWrapper` (holdings, account values, print)
- [x] `Holding` model, `Main`, `run.sh`
- [ ] **Run it once against your live TWS and confirm holdings + avg cost read correctly** ← next action

### Milestone 1 — Persist holdings + thesis (the new core)
This is where the actual product begins: the **written thesis**, which exists nowhere today.
- [ ] `brew install maven`; convert the build to Maven (layout is already Maven-standard)
- [ ] Postgres + JPA: `holding` (snapshot from IB) and `thesis` (why / expectation / **sell trigger** / status: valid·review·weakened·broken / weight_limit / last_reviewed_at)
- [ ] Spring Boot REST: `GET /portfolio` (live holdings joined with stored thesis), `PUT /thesis/{symbol}`
- [ ] A daily NAV snapshot row (feeds TWR later)
- [ ] First JUnit tests (thesis validation, weighted-average math)

### Milestone 2 — UI on real data
- [ ] Fork IBBot's `ui/` React/Vite/Tailwind/lightweight-charts stack
- [ ] Rebuild the Horizon screens against the REST API — holdings grid **led by thesis + status**
- [ ] Thesis editor (write/update thesis, set status, set weight limit)

### Milestone 3 — Analytics & the averaging-down tool
- [ ] Concentration by position (with weight-limit line) and by sector
- [ ] Correlated-cluster / beta exposure flag; "market −20%" scenario
- [ ] Benchmark vs. SPY chart (reuse the `reqHistoricalData` SPY pattern); time-weighted return
- [ ] **Averaging-down calculator on real cost basis** — new avg, break-even, weight inflation vs.
      limit, thesis-gated verdict, the honest question. Verdict ladder (worked out in the Horizon
      demo — top rule wins):
  - Answered "no" to *"would I buy this today from scratch?"* → **red**: not averaging down, refusing
    to lose (sunk-cost).
  - Thesis **broken** → **red**: don't add.
  - Thesis **weakened / review** → **amber**: wait for re-confirmation before adding.
  - New weight > limit (default 15%) → **amber/red**: concentration. Also flag when current price >
    avg cost — that's averaging *up* (increasing exposure), not lowering cost.
  - Thesis **valid** + weight OK + price < avg → **green**: justified; show the new break-even.

### Milestone 4 — Enrichment
- [ ] Fundamentals / earnings / dividends via Finnhub (extend the `NewsProvider` abstraction)
- [ ] Rule-based alerts (price level, earnings soon, weight over limit, thesis stale ≥ 6 months)
- [ ] Decision journal with outcomes + honest self-assessment ("averaging-downs: N, profitable: M")
      — reuse IBBot's `ResearchService` async JSONL/CSV pattern (also fits the daily NAV snapshot)

---

## Open questions / decisions pending

- Which IB account(s) — single personal account, or does the login manage several? (affects
  `reqAccountUpdates` account handling)
- TWR: Flex Query integration vs. daily NAV self-snapshot — decide at Milestone 1.
- Auth for the REST API (local-only single user vs. real auth) — likely local-only to start.
