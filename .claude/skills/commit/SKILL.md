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
stock analysis — Tavily's searches, Claude's extraction, the consensus and the colored dot — behind `POST /api/analysis`):*

| Scope | Covers |
|---|---|
| `ib` | `IbGateway`, `PortfolioWrapper`, `TwsPortfolioRunner` — socket, reader loop, EWrapper callbacks (the portfolio's and the daily closes'), and the startup read from TWS — and what IB reports, as records (`Holding`, `PortfolioSnapshot`, `DailyClose`, `Benchmark`) |
| `report` | the console snapshot report and its formatting |
| `api` | the local Spring MVC API, which always reads from the database: `PortfolioController` + `PortfolioReadService` (`GET /api/portfolio`, which the service puts together), `HoldingWriteController` + `HoldingWriteService` (sector and trade writes), `ManualPositionWriteController` + `ManualPositionWriteService` (manual positions and their trades), `InvestorWriteController` + `InvestorWriteService` (investors and their deposits/withdrawals), `AnalysisController` + `AnalysisService` (`POST /api/analysis`: runs the analysis and stores it), `ApiErrorHandler`, and the `api/request/` and `api/response/` records |
| `ai` | `portfolioboss.ai` — `AiKeys` (the two API keys from `config/local.env`), `TavilyClient` + `SearchResult` / `SearchResults` (the two searches), `StockAnalyzer` + `StockToAnalyze` / `ClaudeReply` (the call to Claude), and the shape of Claude's answer (`StockAnalysisResult`, `SourceConsensus`, `RatingCounts`, `AnalystAction`, `Headline`, `AnalystRating`, `AnalystTrend`, `AnalystActionType`, `Sentiment`), which is also its JSON schema |
| `db` | `portfolioboss.db` (entities, repositories, `PortfolioSyncService`, the sync at connection, `JsonColumnMapper` for the `JSONB` column), the Flyway migrations in `src/main/resources/db/migration/`, `scripts/backup-db.sh` |
| `model` | `portfolioboss.model` (since 2026-10-09) — the values the whole app shares, with no computation: the enums stored by name (`TradeSide`, `HoldingStatus`, `CashMovementType`), the warnings (`HoldingWarning`, `HoldingWarningType`, `InvestorWarning`, `InvestorWarningType`), the labels (`MomentumLabel`, `HoldingSignal`) and `AnalystConsensus` |
| `calculation` | `portfolioboss.calculation` (until 2026-10-08 the scopes `domain` and `model`; since 2026-10-09 only what computes) — `HoldingHistory`, `ClosedPosition`, `TradeFact`, `PositionTrades`, `InvestorPart`, `CashMovementFact`, `InvestorSummary`, `InvestorSummaryCalculator`, `Momentum`, `ConsensusCalculator`, `SignalCalculator`, `OrderCommission`: derived dates, closed positions and realized P&L, reconciliation warnings, each investor's cash and profit, the momentum score, the analysts' consensus and the dot, the default commission — no Spring, no database |
| `ui` | the React app in `ui/` (with its Vite/Tailwind/TypeScript config) and `UiLauncher`, which starts it and opens the browser |
| `config` | `run.sh`, `pom.xml`, `application.properties`, build setup, `.claude/`, tooling |

Tests (`src/test/`) take the scope of the code they cover.

*Planned — add as each milestone lands (see [TODO.md](../../../TODO.md)):*

| Scope | Arrives |
|---|---|
| `thesis` | M1 — the written thesis per holding (the actual product) |
| `analytics` | M3 — concentration, correlated clusters, TWR, SPY benchmark |
| `averaging` | M3 — the averaging-down calculator and its verdict ladder |
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
  sector, trades, manual positions, investors and their deposits, and the stock analyses), CORS configuration, a write
  endpoint that accepts something other than JSON, binding beyond localhost, or sending a quantity, a cost, an investor
  or the account number to Tavily or Claude is a signal something is wrong; flag it instead of committing
- If `$ARGUMENTS` is provided, use it as the commit message (skip analysis)

## Arguments

If the user passes arguments after `/commit`, treat them as the full commit message and skip drafting.
