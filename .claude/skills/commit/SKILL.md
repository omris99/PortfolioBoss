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

*In the code today (Milestone 0 + the first UI slice + the database schema, built with Maven and Spring Boot):*

| Scope | Covers |
|---|---|
| `ib` | `IbGateway`, `PortfolioWrapper`, `TwsPortfolioRunner` — socket, reader loop, EWrapper callbacks, and the startup read from TWS |
| `model` | `Holding`, `PortfolioSnapshot` and the derived portfolio math |
| `report` | the console snapshot report and its formatting |
| `api` | `PortfolioController`, `SnapshotStore` and the `api/response/` records — the local Spring MVC API (`GET /api/portfolio`) |
| `db` | `portfolioboss.db` (entities, repositories, and later the sync and the daily NAV snapshot), the Flyway migrations in `src/main/resources/db/migration/`, `scripts/backup-db.sh` |
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
- This project is **read-only by design** — a commit that adds `placeOrder`, `cancelOrder`, any
  order-related callback, or an API endpoint that changes something (or binds beyond localhost) is a
  signal something is wrong; flag it instead of committing
- If `$ARGUMENTS` is provided, use it as the commit message (skip analysis)

## Arguments

If the user passes arguments after `/commit`, treat them as the full commit message and skip drafting.
