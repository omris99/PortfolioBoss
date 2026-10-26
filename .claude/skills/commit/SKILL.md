---
name: commit
description: Stage and commit PortfolioBoss changes with a clear, descriptive commit message. No Co-Authored-By footer.
allowed-tools: Bash(git add *), Bash(git commit *), Bash(git status), Bash(git diff *)
---

# PortfolioBoss Commit Skill

Stage and commit the current changes.

## Process

1. Run `git status` to see all modified/untracked files
2. Run `git diff --stat` to understand what changed
3. Stage only relevant source files (never build output or local config — see Rules)
4. Write a clear commit message — see format below
5. Commit with `git commit -m`

## Commit Message Format

```
<type>(<scope>): <short description>

<body — what changed and why, in plain language. Multiple lines OK.>
```

**Types:** `feat`, `fix`, `refactor`, `docs`, `chore`

**Scopes.** The scope is optional — omit it for changes that span the whole project
(`docs: ...`, `chore: ...`). Never invent a scope that isn't on this list; if nothing
fits, leave it out.

*In the code today (Milestone 1: Maven + Spring Boot, PostgreSQL, sync at connection, derived buy/sell dates,
write endpoints for the sector and trades, the UI that shows the positions and enters the sector and trades, the
reconciliation warnings, the closed positions — commissions, average cost, and manual positions with their own
trades — several investors sharing the account, with their deposits, the momentum score from IB's daily closes, and the
stock analysis — Tavily's searches (kept with each analysis), Claude's extraction, the consensus, the analysts' trend
from price-target moves and the colored dot — behind `POST /api/analysis` and in the UI's Signal column, a holding
drawer's "Analysts & news" tab and the Analyze buttons — the UI's light redesign: four pages, a drawer per holding
and per closed position, a Columns menu, forms in modals —, worked out in the UI only, the Overview's weight graphs
by sector and by holding with the cash, and the drawer's target-weight calculator — and the fifth page,
Recommendations: IB's scan for hot stocks once a trading day in `./run.sh` and on demand (Scan again, `POST
/api/scans`), the hot / too hot / correcting lists with their correlations to the holdings behind
`GET /api/recommendations`, and a drawer per scanned stock):*

| Scope | Covers |
|---|---|
| `ib` | `IbGateway`, `PortfolioWrapper`, `RequestBatch`, `TwsReader` (the one class that connects to TWS: the startup's reading and Scan again's), `TwsPortfolioRunner` (the startup flow) — socket, reader loop, EWrapper callbacks (the portfolio's, the scans', the contract details' and the daily closes') — and what IB reports, as records (`Holding`, `PortfolioSnapshot`, `DailyClose`, `Benchmark`, `HotStockScan`, `ScanRow`, `StockDetails`, `ScanCandidate`, `Scan`, `TwsReading`, `ScanReading`) |
| `report` | the console snapshot report and its formatting |
| `api` | the local Spring MVC API, which always reads from the database: `PortfolioController` + `PortfolioReadService` (`GET /api/portfolio`, which the service puts together), `HoldingWriteController` + `HoldingWriteService` (sector and trade writes), `ManualPositionWriteController` + `ManualPositionWriteService` (manual positions and their trades), `InvestorWriteController` + `InvestorWriteService` (investors and their deposits/withdrawals), `AnalysisController` + `AnalysisService` (`POST /api/analysis`: runs the analysis and stores it), `RecommendationsController` + `RecommendationsReadService` (`GET /api/recommendations`), `ScanController` + `ScanService` (`POST /api/scans`: a new scan through `TwsReader`), `ApiErrorHandler`, and the `api/request/` and `api/response/` records |
| `ai` | `portfolioboss.ai` — `AiKeys` (the two API keys from `config/local.env`), `TavilyClient` + `SearchResult` / `SearchResults` / `StockSearches` (the four searches, the symbol filter, and what is kept with an analysis), `StockAnalyzer` + `StockToAnalyze` / `ClaudeReply` (the call to Claude), and the shape of Claude's answer (`StockAnalysisResult`, `SourceConsensus`, `RatingCounts`, `AnalystAction`, `Headline`, `AnalystRating`, `AnalystActionType`, `Sentiment`), which is also its JSON schema |
| `db` | `portfolioboss.db` (entities, repositories, `PortfolioSyncService`, the sync at connection, `JsonColumnMapper` for the `JSONB` column), the Flyway migrations in `src/main/resources/db/migration/`, `scripts/backup-db.sh` |
| `model` | `portfolioboss.model` (since 2026-10-09) — the values the whole app shares, with no computation: the enums stored by name (`TradeSide`, `HoldingStatus`, `CashMovementType`), the warnings (`HoldingWarning`, `HoldingWarningType`, `InvestorWarning`, `InvestorWarningType`), the labels (`MomentumLabel`, `AnalystTrend`, `HoldingSignal`, `HotStockLabel`) and `AnalystConsensus` |
| `calculation` | `portfolioboss.calculation` (until 2026-10-08 the scopes `domain` and `model`; since 2026-10-09 only what computes) — `HoldingHistory`, `ClosedPosition`, `TradeFact`, `PositionTrades`, `InvestorPart`, `CashMovementFact`, `InvestorSummary`, `InvestorSummaryCalculator`, `Momentum`, `ConsensusCalculator`, `AnalystTrendCalculator`, `SignalCalculator`, `HotStockCalculator`, `ReturnsCorrelation`, `ScanSchedule`, `OrderCommission`: derived dates, closed positions and realized P&L, reconciliation warnings, each investor's cash and profit, the momentum score and the year's figures, the analysts' consensus and trend and the dot, the hot-stock rule and the page's order, the correlations, when a scan is due, the default commission — no Spring, no database |
| `exceptions` | `portfolioboss.exceptions` (since 2026-10-26) — the app's own exceptions (`TwsUnavailableException`, `TwsBusyException`) |
| `ui` | the React app in `ui/` (with its Vite/Tailwind/TypeScript config) and `UiLauncher`, which starts it and opens the browser |
| `config` | `run.sh`, `pom.xml`, `application.properties`, build setup, `.claude/`, tooling |

Tests (`src/test/`) take the scope of the code they cover.

*Planned — add as each milestone lands (see [TODO.md](../../../TODO.md)):*

| Scope | Arrives |
|---|---|
| `thesis` | M1 — the written thesis per holding (the actual product) |
| `analytics` | M3 — correlated clusters, TWR, SPY benchmark (the weight of each holding and sector is already in the UI, scope `ui`) |
| `averaging` | M3 — the averaging-down calculator's verdict ladder (its weight part, the drawer's Weight tab, is in the UI, scope `ui`) |
| `alerts` | M4 — rule-based alerts |
| `journal` | M4 — decision journal |

**Examples:**
- `feat(thesis): add sell-trigger field to the thesis entity`
- `fix(ib): unsubscribe from account updates before disconnecting`
- `refactor(report): extract money formatting out of PortfolioWrapper`
- `docs: add CLAUDE.md project guide`

## Rules

- **Always** show the drafted commit message to the user and wait for approval before running `git commit`
- **Never** add `Co-Authored-By:`, `Generated with`, or any trailer lines to the commit message
- **Never** commit `out/`, `target/`, `*.class`, `ui/node_modules/`, `ui/dist/`, `ui/dev-server.log`,
  `config/local.env`, or `.claude/settings.local.json` unless the user explicitly asks
- Keep the subject line under 72 characters
- The body should explain *what* and *why*, not restate the diff line by line
- This project is **read-only toward IB by design** — a commit that adds `placeOrder`, `cancelOrder`, any
  order-related callback, an API endpoint that writes anything other than PortfolioBoss's own data (the hand-entered
  sector, trades, manual positions, investors and their deposits, the stock analyses and the scans), CORS configuration, a
  write endpoint that accepts something other than JSON, binding beyond localhost, a second place that connects to TWS
  besides `ib.TwsReader`, or sending a quantity, a cost, an investor or the account number to Tavily or Claude is a signal
  something is wrong; flag it instead of committing
- If `$ARGUMENTS` is provided, use it as the commit message (skip analysis)

## Arguments

If the user passes arguments after `/commit`, treat them as the full commit message and skip drafting.
