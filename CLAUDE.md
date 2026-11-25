# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A read-only decision-support tool for a **long-term** Interactive Brokers portfolio — the calm
counterpart to the sibling IBBot intraday trading project (`~/DevProjects/IBBot`). It connects to
TWS, syncs the holdings into a local PostgreSQL database on every connection, and serves that
database to a React UI (`ui/`) of seven pages — the API always reads from the database, never straight from TWS: an
Overview (the account's figures, what needs a look, how the signals stand, how the money splits by sector and by
holding), the positions table, in which the user
enters what IB doesn't know (a holding's buy/sell trades; its sector and industry come from IB since 2026-11-05), the
Averaging calculator (buying more of several holdings at once, those below their average cost first, against the cash —
worked out in the UI only, nothing saved), the closed positions — derived from those
trades, plus the manual positions sold before PortfolioBoss saw them —, a card per investor for an account several
people's money shares (each one's cash, value and profit), all of which the UI writes to the same database through a
few JSON endpoints, Recommendations: the US stocks IB's scan finds in a long upward trend, in three lists (hot, too
hot, correcting), and Taxes: the capital gains tax of every sale in the account, as the broker withholds it, to check off
against its monthly statements. Each holding also gets a momentum score from IB's daily closes and, on the press of a button, an analysis of
its analyst ratings and news (Tavily searches, Claude extracts, the code decides), stored in the same database. It is a
Maven / Spring Boot application (Java 21, Spring Boot 4.1.1).

[TODO.md](implementation-plans/TODO.md) is the plan of record — the six product principles, the settled architecture
decisions, and the milestone breakdown. Read it before proposing features or structural changes;
a feature that doesn't trace back to one of the six principles probably belongs in IBBot, not here.
[HOLDING_DETAILS_TODO.md](implementation-plans/HOLDING_DETAILS_TODO.md) is the session-by-session plan for the next stretch
of Milestone 1 (Postgres, trades, holding details); its sessions 0 (Maven + Spring Boot), 1
(PostgreSQL, Flyway, schema, entities), 2 (sync at connection, API reads from the database), 3
(derived buy/sell dates and holding period), 4 (write endpoints for the sector and trades), 5 (UI: the new
columns and sorting), 6 (UI: entering the sector and trades) and 7 (reconciliation warnings) are done; 8–9 are
ideas for later. [CLOSED_POSITIONS_TODO.md](implementation-plans/CLOSED_POSITIONS_TODO.md) came next: the shares sold, with their realized
P&L — sessions 1 (the list derived from the trades already entered), 2 (commission, manual closed positions) and 3
(their UI), 4 (average cost, partial sells, manual positions with trades of their own) and 5 (its UI) are done.
[INVESTORS_TODO.md](implementation-plans/INVESTORS_TODO.md), several investors sharing one IB account (each one's cash and profit), followed:
its sessions 1 (schema, computation, read API), 2 (write endpoints for investors, deposits and a trade's investor),
3 (UI) and 4 (each investor's part of a shared position's profit) are done; its "out of scope" list holds the ideas for
later. [AI_ANALYSIS_TODO.md](implementation-plans/AI_ANALYSIS_TODO.md) is next: for each holding, momentum from IB's daily closes, and an
analysis of analyst ratings and news — the server searches Tavily, Claude (Sonnet 5.5, no tools) only extracts JSON, and
the code picks the consensus source, the analysts' trend and a colored signal — run only from a button. Its sessions 0
(the experiment that settled the model and the searches), 1 (momentum: a 0–5 score from IB's daily closes, in
`/api/portfolio`) and 2 (the analysis backend: `POST /api/analysis`, the `stock_analysis` table, the analysis and the dot
in `/api/portfolio`) are done; session 3 (the UI: the Signal column, the analysis box — since the redesign a holding
drawer's "Analysts & news" tab —, the Analyze buttons) is built, and its runs from the browser brought decisions 18–22: the analysts' trend worked out by the
code from price-target moves, four searches (news articles for the analysts' actions, since forecast pages lag weeks in
the search engine's copy), each source's copy date in the UI, and the search results Claude read kept with every
analysis — what is left of its check is in the plan. Its API keys go in the git-ignored `config/local.env`, which
Claude never reads or prints. The UI's redesign (begun 2026-10-11) is designed screen by screen in Claude Design from
[DESIGN_BRIEF.md](implementation-plans/DESIGN_BRIEF.md) — every screen's content, the color meanings to keep, made-up sample data (never the
real portfolio) — and built from that design system's tokens and component specs, not its preview code. All of it is in
and browser-checked (2026-10-11): the light theme, the four pages with their addresses, the Overview, a drawer for a
holding's details (`/positions/:holdingId`) and for a closed position, a Columns menu, the Signal cell in words, a
confirm before a paid Analyze run quoting the real average cost, every form and confirm-delete in a modal, and the two
warning triangles. The Radix primitives (`@radix-ui/react-dialog`, `-popover`, `-dropdown-menu`, and since 2026-11-16
`-tooltip`, since 2026-11-25 every hint and "?" of the app) bring the focus and keyboard handling; the date fields use the browser's own picker behind a calendar button, and the first load is still a
"Loading…" box rather than the design's skeletons. Since 2026-10-13 the Overview also shows the money by sector (a ring)
and by holding with the cash (bars), with the stocks against the cash under the account's figures, and a holding's
drawer has a Weight tab that works out the shares to sell or buy for a target weight — built in the UI from figures
`/api/portfolio` already sends (see the architecture below); both have a tab per investor (Portfolio · Omri · Shay …)
that weighs that investor's own shares and cash. They were built first without Claude Design, then designed in its
second round (DESIGN_BRIEF.md §7, 2026-10-25) and rebuilt from it the same day, browser-checked: one "Weights"
card with the tabs in its header, the explanations in a small bubble (`Tip`; the app's `Hint` since 2026-11-25), the
Weight tab's result in rows. Every trade has a price since 2026-10-13 too: the API, the form and the table (V8) all
refuse one without.

[RECOMMENDATIONS_TODO.md](implementation-plans/RECOMMENDATIONS_TODO.md) came next: a fifth page of hot US stocks — "hot" is a long
upward trend, then sorted by how far the price has stretched above its 50-day average —, a recommendation the code
picks — by weighted risk signs from Finnhub's company figures and the closes, and by a news score from the events Claude
classifies in the news and the analysts' moves (decision 13) —, and a measurement of how the picks did. Its sessions 0
(the experiment), 1 (the scan in `./run.sh`, V9, the hot-stock rule, the correlations, `GET /api/recommendations`) and 1b
(the page, designed first in Claude Design's third round — DESIGN_BRIEF.md §8, every piece marked [now] or [later] — and
the Scan again button, `POST /api/scans`) are done and were checked against TWS and in the browser on 2026-10-26: 992
stocks passed the scan's filters, the two scans found 73, all 73 on NYSE or NASDAQ by their contract details'
`primaryExch`. Session 2 (2026-10-27) added Finnhub's client, `stocks.FinnhubClient`. Session 3's backend (2026-10-27) is
built: `POST /api/recommendations/analysis` (the short list's analysis, Finnhub beside it, the pick, V10 and V11),
`moveReason` and `newsEvents` in every analysis, and the GET side; V10 is on the real database and one paid full run went
through (10 stocks, 38 s, $0.47) before decision 13. Session 4 (2026-10-27) built the UI — the [later] pieces of the
design, reworked where decision 13 had overtaken it: the recommendation card (none yet / the picks / none qualified, "why
not the others"), Analyze & recommend and an Analyze per stock (in its row after a confirm, in its drawer at once), a
detail line under each analyzed Hot row, and the drawer's Analysts & news, Company and If I buy tabs —, with a small API
addition (each stock's news-score parts, `riskWeight`, `score`, `canBePicked`, so the UI never holds a weight). V11 is on
the real database since the first full run from the page (2026-10-27). After that run the user kept the rule as it is (a
stock with no risk sign and no news scores 0 and can be picked), and asked for the analyst actions without a price target
to be hidden and the detail line to be one labeled line. **Claude doesn't explain the picks** (dropped: it would justify a
pick it didn't make) **nor choose them** (session 0: the same input gave two picks, then none). Then decision 14
(2026-10-29, session 4ב): two of the three picks of 27.10 were below the analysts' target, and the user wants **at least
15% upside by the analysts** — so every scan now reads each stock's **Dow Jones headlines** through IB's API (free on this
account; IB's scanner filter for it was refused, Finnhub's targets are paid), the code reads each firm's price target out
of them, and the short list is the first 10 HOT stocks whose **median** target (each firm's latest of half a year, 3
firms at least) is 15%+ above the last close. Session 4ב is built (437 tests, the UI build) and was checked live on
2026-10-29: `./run.sh` applied V12 (after a backup) and its scan read 2,022 targets for 69 of the 71 stocks found, a full
run from the page picked three, and the page was checked in the browser. Session 5 (the measurement) comes after, once a
month of prices has passed since the first picks (from about 2026-11-27).

[MY_HOLDINGS_TODO.md](implementation-plans/MY_HOLDINGS_TODO.md) (2026-11-02) is the current plan: what the holdings themselves still lack
against TODO.md's six principles — in the weeks before, a scanned stock got more data than a holding. In order: how much a
stock must rise to reach its new average after a buy (the drawer's Weight tab, UI only), the earnings date, Finnhub's
figures and the Dow Jones target for the holdings too, the holdings that move together and a market drop of 20%, alerts
from a daily run, and the portfolio's return against SPY. Its session 0 (the docs) is done, and so is session 1
(2026-11-04, browser-checked): every purchase in the drawer's Weight tab shows the new average cost and how far the price
must rise to reach it (and against the average of today), and a second field buys for an amount. Session 2 (2026-11-04/05,
443 tests, checked live against TWS) gives every holding what a scanned stock has, in `/api/portfolio` — the Dow Jones
target, Finnhub's next report and four figures, the risk signs —, read on every `./run.sh` connection: the holdings'
contract details and headlines in the same rounds as the scan's, then Finnhub for each holding in USD (decision 10, the
symbol only). And (decision 11, the user's choice) a holding's **sector comes only from IB** — IB's broad word, with its
finer one as the holding's `industry` (V14) —, never typed by hand: the sector endpoint and the editable cell are gone.
Session 3 (2026-11-05/07, 444 tests) shows them: Finnhub's four fields moved into one object a holding and a scanned
stock share, `companyInfo` (with when Finnhub answered); the shared pieces left `recommendations/` for
`components/companyInfo/` and `components/AnalystTarget.tsx`; the Positions table has Analysts' target and Earnings (under
Columns), and an open holding's drawer the target first in Analysts & news and a Company tab. After the first browser
check, at his asks: the next report 0–14 days away marked with a yellow highlighter wherever the outlined timing note
was ("in 6 days" under the date in the table), "—" for no risk signs, and the drawer's row of risk signs turned into
Warning signs — the dot's three and the risk signs together; all of it browser-checked by the user. Session 3ב
(2026-11-07, put before session 4 at his ask — decision 13) is a sixth page, the **Averaging calculator** (`/averaging`):
the drawer's Weight tab plans buying more of one holding, the page plans it for several at once, every purchase added up
against the tab's cash, with a line of what to know under each holding (TODO.md's principle 2: "would you buy it today at
this price?", answered with data). Designed first in Claude Design's fourth round (DESIGN_BRIEF.md §9; his picks:
arrangement A, and "1 purchase planned" on the folded section), built the same day and browser-checked by him. The
Weight tab stays — selling to a target and an investor's first purchase exist only there, and the Overview's bars lead to
it — with a link to the page under its calculator, always shown (his ask). Session 5 (2026-11-11) came before session 4,
at his ask, and changed with decision 14: **no daily run** (the app doesn't stay open) — instead **Sync now** (5א), a
button in the top bar that reads TWS as the startup does (`POST /api/syncs`), browser-checked against TWS the same day —,
and **the alerts** (5ב) are checked on every sync, against the alerts still open: nine kinds (decisions 6 and 16), a
**bell** at the top bar's left with the number not yet seen (decision 15, the accent blue), and a settings drawer behind
its ⚙ (decision 17: each kind on or off, four numbers), all in a package of its own, `alerts` (his ask). 5ב is built (505
tests, V15 on the real database after a backup, 14 of 14 tables equal) and browser-checked by him (2026-11-13); his
first look moved the bell to the left of the name. The alerts' channel (session 6, Telegram) is a plan for later. Session 4
(holdings that move together, a market drop of 20%) waits.

[CAPITAL_GAINS_TAX_TODO.md](implementation-plans/CAPITAL_GAINS_TAX_TODO.md) (2026-11-13/14) is the current plan: the capital gains tax of every
sale in the account, in shekels and exactly as Phoenix — the Israeli broker the account is opened through, which withholds
the tax — computes it (the settlement day's Bank of Israel rate, FIFO lot by lot, a yearly balance settled at each month's
end), each investor's part, and a ✓ against what Phoenix actually withheld, on a seventh page, Taxes, designed in Claude
Design first. Its source for the account is **IB's Flex Web Service** (decision 13): the account's every trade, the day
trades the database leaves out on purpose included, with IB's real commissions and settlement days. Session 1 (2026-11-15,
551 tests) is built: a new package, `tax`, that downloads IB's Flex reports on every sync and stores them in V16's
`flex_trade`, `flex_cash_transaction` and `flex_report` — the history from the year of the earliest trade typed in, then
only the missing days, each period starting and ending on a weekday: the first live runs found that **IB refuses a
period that starts or ends on a Saturday or a Sunday** (1003 "Statement is not available"; the user confirmed it by hand
on IB's site). V16 is on the real database since that day's `./run.sh` (after `portfolioboss-2026-11-15.sql.gz`, 16 of
16 tables equal), and the whole history is stored, 2021-01-01 to 2026-11-13, without a gap or a double. Session 2
(2026-11-15, 591 tests) is built: a package of its own, `exchangerates` (the user's ask), downloads the Bank of Israel's
dollar rates into V17's `exchange_rate` on every sync — only the days really missing, checked before any call —, and
`tax.SettlementDayRatesService` gives it the settlement days the tax needs (the Flex trades' and cash transactions', and
the typed trades' estimated). Checked against the Bank of Israel before building: it has **no Hong Kong dollar rate** in
any series (a sale in HKD stays "enter by hand", decision 10), and it publishes no rate on Israeli and international
holidays — about 15 weekdays a year, 7 days at most in a row —, which such a day's "last rate before it" (2.2) covers.
V17 is on the real database since that day's `./run.sh` (after `portfolioboss-2026-11-15.sql.gz`, 19 of 19 tables
equal; the morning's backup renamed `…-before-V16`): one request stored the dollar's rates from two weeks before the
account's earliest settlement day to 2026-11-13, every one equal to the Bank of Israel's series, and every settlement day
in dollars up to that day inside them; the next sync asked nothing. Session 3 (2026-11-15, 648 tests) is built and
checked live: the tax itself, worked out on every call from what is stored and printed after every sync — FIFO over each
stock's trades, the taxable gain per buy a sale took from (decision 11), an entry per settlement day, each year's balance
with its monthly settlements —, for the account from Flex and for each other investor from their typed trades, **in
shekels** (decision 6, changed at the user's ask: an investor's tax isn't converted to dollars and doesn't touch their
cash). Its planning found that **Flex's Trades section shows no corporate action**: a reverse split had moved a stock to
a new contract, its buys under the old one and its sale under the new. The user added the query's Corporate Actions
section and IB's own `fifoPnlRealized`, stored by V18 (decision 15), and the trades before a split are restated in the
shares of after it, in memory. On the real data (V18 since that day, after `portfolioboss-2026-11-15.sql.gz`, 20 of 20
tables equal; the history downloaded again): our FIFO equals IB's to the cent on every sale but one in HKD (IB lowered
its cost by a return of capital, which Phoenix taxed as income — no change); the 867 of 2024 has the same taxable gain to
the shekel, its tax a few tens of shekels lower (Phoenix's own fees, the user's OK); 2025 differs by the HKD sale, which
isn't computed. Session 4 (2026-11-16, 692 tests) is built and checked live: the **confirmations** — what Phoenix recorded
for a settlement day of the account, as the user confirms it on the Taxes page (V19's `tax_confirmation`) — and the
page's endpoints, `GET /api/taxes` and `POST` / `PUT` / `DELETE /api/taxes/confirmations` (`tax.TaxController`). The
user's rules: **a confirmed amount is final** — it counts in the year's tax and the monthly settlements in place of the
calculated one, even if the calculation changes later —, a day is only waiting or confirmed (**no "matches" / "differs"**:
"a number I typed is the right one"), and only a day the calculation has an entry for can be confirmed (Phoenix's own fee,
about 20 ₪ a month, isn't worth a day of its own). V19 is on the real database since that day (after
`portfolioboss-2026-11-16.sql.gz`, 21 of 21 tables equal; applied by `./run.sh 7599`), and `GET /api/taxes` agreed with
itself on every year of the real data. Session 5 (2026-11-16/17, 707 tests, browser-checked by the user) is the **Taxes
page** (`/taxes`, the seventh), built from Claude Design's fifth round after a revision round of its own (DESIGN_BRIEF.md
§10's note: no "matches" / "differs", "calculated" in every text, "Bought" for a lot): a year to choose, tabs for
Portfolio and each investor besides the account owner, the year's card, and one table of the year's settlement days by
month — a day opens into its sales, a sale into the buys it took from, both ways —, with Confirm, the pencil for the
broker's amount and "Confirm the whole month" on Portfolio only. The user's pick (option ב): it came **before** the session
he planned next — when Flex is read, offer the trades not typed in (day trades too) and split a trade between investors,
so that each one, the account owner too, pays by their own trades —, so the owner's tab and the "day trade" tag wait for
it. The API only gained keys for it: each day's status, worked out in Java, a sale's FIFO cost against the average cost,
and whether IB's report is connected and stored up to which day.

[STOCK_SPLITS_TODO.md](implementation-plans/STOCK_SPLITS_TODO.md) (2026-11-22) is the current plan: a holding that split
showed the warning triangle — IB's quantity after the split, the trades typed in before it. The design is the user's: a
**split is stored once**, by IB's `actionID`, in V20's `stock_split`, when the sync finds it in the Flex reports' Corporate
Actions and IB's words ("SPLIT 4 FOR 1") and the shares agree; the **trades typed in stay as typed**, and on every read
each investor's open position at the split is **translated** (his word) into the shares of after it — a position sold in
full before the split stays as it happened (IB reports no split of a stock no longer held). Rejected: the sync rewriting
the stored trades (a back-dated trade typed later, whole shares in a reverse split, the sync never writes typed trades).
Session 1 (the backend, 765 tests) is built and checked live on 2026-11-22: V20 on the real database after
`portfolioboss-2026-11-22.sql.gz` (22 of 22 tables equal), the run stored both splits already in the reports. It also
fixed the tax: it knew only a reverse split into a new contract, and a split in the same contract — one row in IB's
report — had left every sale of the stock not computed. Session 2 (2026-11-23, UI only, browser-checked by the user) shows
them: the split's grey line in the drawer's trades at its date, a translated trade's row in the shares of after the split
with what was typed under it ("= 10 at 400.00 before the split" — the user's pick over the plan's "= 40 at $100 after the
split", so that the table agrees with IB's quantity), the trade form's note for a date on or before a split, and the
backfill's prefill turned into the date's shares.

[UI_POLISH_TODO.md](implementation-plans/UI_POLISH_TODO.md) (2026-11-24) is the current plan: the UI's small details
polished for a calmer, clearer experience — **not a redesign**, no new feature —, from Claude Design's sixth round
(DESIGN_BRIEF.md §11, his rules in §11.6), checked on a **demo database** of made-up positions (`portfolioboss_demo`,
`scripts/demo/` — git-ignored since 2026-11-25, on this machine only —, run with
`SPRING_DATASOURCE_URL=…/portfolioboss_demo ./run.sh 7599`; never real data on screen). Stages
א (the top bar in `components/topBar/`, `Hint`, dates dd/mm/yyyy), ב (words instead of tags, `MomentumMark`, Positions'
columns, the drawer), ג (motion — the "Motion" block of `index.css`, `hooks/animation/useCloseAfterExitAnimation.ts` —
and the first load's skeletons, `components/firstLoad/`), ד (the alerts panel in two groups and the settings drawer;
the alerts' words and group in Java; the top bar sticky) and ה (one way to explain on hover: `Hint` in place of every
`title`, one "?" — see the hover explanations below) are built and browser-checked (2026-11-24/25); ו (the rest of the
findings) comes next. Its file is the record of each stage's pieces and decisions.

## Hard invariant: read-only

PortfolioBoss reads the account and **never places, modifies, or cancels an order**. `IbGateway`
deliberately exposes no order-placement method, and `PortfolioWrapper` overrides only the
account-reading callbacks, for the momentum the daily-bar ones (`historicalData` / `historicalDataEnd`), and for the
Recommendations page the scanner's (`scannerData` / `scannerDataEnd`), the contract details' (`contractDetails` /
`contractDetailsEnd`) and Dow Jones's headlines' (`historicalNews` / `historicalNewsEnd`) — reads too: `IbGateway` sends
`reqScannerSubscription` and, after the scan's first answer, `cancelScannerSubscription`, `reqContractDetails` and
`reqHistoricalNews`. Do not add `placeOrder`, `cancelOrder`, or order-related `EWrapper`
callbacks — if a task seems to need them, it is the wrong project. The drawer's Weight tab and the Averaging calculator
say how many shares to sell or buy, and that is all they do: calculations on screen that send nothing anywhere and save
nothing. Likewise
"recommended" means picked by rules in code, not a price forecast, and nothing is bought.

The local API never touches the IB account either. It writes only to PortfolioBoss's own database — the
trades the user enters, through `HoldingWriteController`, the manual positions with their trades,
through `ManualPositionWriteController`, the investors with their deposits and withdrawals, through
`InvestorWriteController`, the stock analyses (`stock_analysis` rows), through `AnalysisController` and
`RecommendationAnalysisController` — the second also the recommendations (`investment_recommendation` rows) —, and a new
scan (`scan` / `scan_candidate` rows and its stocks' daily closes), through `ScanController`, a sync (what the startup
writes, the splits it finds in the Flex reports among it — `stock_split`), through `SyncController`, the alerts' "seen" and their settings (`alert`, `alert_setting`), through
`alerts.AlertController`, and the confirmations of the capital gains tax (`tax_confirmation`), through
`tax.TaxController` — and never creates or deletes a holding (only the sync does). `AnalysisController` and `RecommendationAnalysisController` are the two
endpoints that reach outside the machine: they send each stock's symbol to Tavily, and the symbol, currency, the price
(IB's, or a scanned stock's last close), today's date and the search results to Claude — **never** a quantity, a cost, an
investor or the account number (AI_ANALYSIS_TODO.md, decision 8), and nothing about the portfolio
(RECOMMENDATIONS_TODO.md, decision 6) — and they run only when the user presses the button. `stocks.FinnhubClient`
sends Finnhub only a symbol (RECOMMENDATIONS_TODO.md, decision 8), from two callers: the second endpoint, on its button,
and `api.SyncService`, for each open holding in USD (MY_HOLDINGS_TODO.md, decision 10: on a foreign exchange IB's symbol
is not Finnhub's and may be another company's) — **without a button press** on every `./run.sh` that reached TWS, and
on Sync now. `tax.FlexClient` (CAPITAL_GAINS_TAX_TODO.md, session 1) downloads reports from IB's Flex Web Service, over
HTTPS — read only: the service gives reports and nothing else, no way to trade or move money — and sends it only the
token, the query's number and the two dates of a period, nothing about the portfolio; **without a button press**, from
`api.SyncService` after every sync that reached TWS, and only for the days not stored yet (none after a day's first sync).
IB takes the token only in the address (`t=`), so no error of the client may print the address (`FlexClient` turns each
failure into a message of its own; `FlexClientTest` checks the token is in none). The account number in IB's reports is
never stored. `exchangerates.BankOfIsraelClient` (CAPITAL_GAINS_TAX_TODO.md, session 2) downloads the Bank of Israel's
representative rates from its public SDMX service (`edge.boi.gov.il`), over HTTPS, without a key, and sends it only the
series' name — the currency's code, `RER_USD_ILS` — and the two dates of a period, nothing about the portfolio;
**without a button press**, from `api.SyncService` (through `tax.SettlementDayRatesService`) after every sync that reached
TWS, and only when a settlement day's rate is really missing (none on most syncs). Two endpoints read TWS, both read-only and free, through the same `ib.TwsReader` as `./run.sh`:
`ScanController` (`POST /api/scans`, the Scan again button: a scan) and `SyncController` (`POST /api/syncs`, Sync now:
the startup's whole reading). The alerts send nothing out of the machine (their channel is a plan for later).
`PortfolioController` and `RecommendationsController` stay GET-only, and `GET /api/alerts` is `AlertController`'s one
read, as `GET /api/taxes` is `TaxController`'s (Spring answers 405 to every other method). The server binds to the loopback interface only (`server.address=127.0.0.1` in
`application.properties`), there is **no CORS configuration**, and every write endpoint with a body accepts
JSON only: a page on another site can make the browser send a request to localhost without asking first
only as text or a form (415 here), while JSON, PUT and DELETE need a CORS preflight that nothing grants —
`POST /api/analysis` and `POST /api/recommendations/analysis` take a JSON body even to analyze everything (`{}`), so no
other site can start a paid run, and `POST /api/scans`, `POST /api/syncs` and `POST /api/alerts/seen` take `{}`
(`ScanRequest`, `SyncRequest`, `AlertsSeenRequest`, records with nothing in them) for the same reason. Don't add CORS
configuration, don't add an endpoint that writes anything other than PortfolioBoss's own data (what the user enters, the
analyses, the recommendations, the scans, a sync — the Flex reports and the exchange rates it downloads, and the splits
it stores, too —, and the alerts with their settings), and don't expose the API beyond localhost (the connection to TWS and the API are both
unencrypted, which is fine only while local).

## Build & run

Maven build ([pom.xml](pom.xml)): Java 21, Spring Boot 4.1.1 (`spring-boot-starter-webmvc` — Spring
Boot 4's name, not `-web`), JUnit 5. [run.sh](run.sh) is a thin wrapper around `mvn -q spring-boot:run`
(Maven from `brew install maven`); its arguments reach the app as Spring's non-option arguments:

```bash
./run.sh              # 127.0.0.1:7496 (live TWS), clientId 101
./run.sh 7497         # paper-trading port
./run.sh 7496 102     # custom clientId
```

`./run.sh` reads the portfolio from TWS once, prints it, scans for hot stocks when a scan is due (once a US trading
day: only when the market has closed since the last scan, `calculation.ScanSchedule` — otherwise `[ib] scan skipped:
…`), reads the holdings' contract details (their sector and industry) and Dow Jones targets, asks Finnhub about the
holdings in USD once TWS is disconnected, syncs it all into PostgreSQL, checks the alerts (`[alerts] checked: 2 new
(ABCD, WXYZ), 1 no longer true`), downloads the days of IB's Flex reports not stored yet (`[tax] Flex: 41 trades, 6 cash
transactions, 0 corporate actions (2026-11-03 – 2026-11-13)`, or `[tax] Flex skipped: already read up to 13.11`), stores
the splits they brought (`[db] stored a stock split: ABCD 4 for 1 on 2026-03-10`; nothing when none is new — this step
runs at a startup without TWS too), then the Bank of Israel's dollar rates of the settlement days that have none yet (`[exchangerates] Bank of Israel: stored 3 USD
rates (2026-11-12 – 2026-11-16)`, or `[exchangerates] Bank of Israel skipped: every settlement day up to 16.11 has its USD
rate`), prints the capital gains tax of every year and our FIFO against IB's (`[tax] capital gains tax 2026: 1,234.56 ILS
on a taxable gain of 4,938.24 ILS (12 sales)`, `[tax] our FIFO against IB's: 75 of 75 sales match`), and then keeps
running: it serves the database at `http://localhost:8080/api/portfolio`, starts the UI's dev server,
and opens `http://localhost:5174` in the browser (stop everything with Ctrl+C). If TWS could not be
read this run, it serves whatever the previous sync stored instead (see "Running requires TWS" below).
The UI is a separate Vite app (Node 20.19+ — which is why react-router stays on 7.x: 8.x needs Node 22) whose
dependencies must be installed once:

```bash
cd ui && npm install     # first time only
npm run dev              # manual alternative to the auto-start; proxies /api to :8080
npm run build            # tsc -b && vite build; the type check is the only UI check for now
```

A change to `ui/tailwind.config.js` (a new color role, say) reaches a running dev server only after a restart — stop
the app and run it again: the CSS variables in `index.css` reload, but the classes built from the config don't (the
sector ring came up black on 2026-10-13, its fill classes missing; on 2026-11-05 the new highlighter showed no yellow
until the restart).

The UI shows the portfolio as of the last sync. It reloads `/api/portfolio` after every write (trades,
manual positions, investors, deposits) and after every analysis run; new figures from IB come with a new `run.sh` or
the top bar's **Sync now** (since 2026-11-11: the startup's whole reading on demand, `POST /api/syncs`, then a reload —
the Recommendations page, the alerts bell and the Taxes page load again when the portfolio's `asOf` changes).
Recommendations' Scan again reads only a new scan on demand and reloads `/api/recommendations`. Both need TWS open. The
Taxes page reloads `/api/taxes` after every confirmation, correction or cancel.

Tests: `mvn -q test` (JUnit 5; no TWS, but the database tests need `portfolioboss_test` running — see
above). `PortfolioControllerTest` (`@WebMvcTest` + `MockMvc` + `@MockitoBean` on `PortfolioReadService`)
pins the JSON contract the UI depends on with made-up responses, no database needed; `HoldingTest` covers
the derived math; `PortfolioWrapperTest` feeds `PortfolioWrapper` IB callbacks directly, no socket — the portfolio's,
the scans' (only a scan's first answer kept, an error ending it, code 165 not ending it, the cancel's confirmation after
the end ignored), the contract details' (a second send on the same connection — the holdings' after the scan's —
numbered on, the first one's answers kept) and the daily closes' (an error ending one request, a request still running at
the timeout, an unreadable bar date), and Dow Jones's headlines (each contract's with their time, a late headline of an
earlier round dropped, an unreadable time skipped); `ScanTest` how two scans' rows and the contract details become
candidates (one per stock, NYSE / NASDAQ only, the left-out ones named); `PriceTargetHeadlineReaderTest` the four forms of
a target headline (copied from the real feed), a thousands separator and an `&amp;`, the company matched by the start of
each word of IB's name (HPE's "Ent", "The Trade Desk"), another company's target left out (Asset Entities in ASST's feed,
Apple against APPLIED MATERIALS) and what isn't a target; `NewsHeadlinePagingTest` the pages back (a full page asks for
the one before it; a short, empty, repeated or half-year-old one ends the stock);
`MomentumTest` covers the momentum score on made-up series of closes (decision 5's two-day-drop example among them) and
the year's figures (IB's year starting a day late still a full year, a young stock's months).
`HotStockCalculatorTest` (the 15% and 5–15% limits, no long trend, the page's order), `ReturnsCorrelationTest` (1 and
−1, 99 shared days, a date missing from one), `ScanScheduleTest` (a morning scan, the weekend, New York's clock
change), `RiskSignsCalculatorTest` (each risk sign at its limit, a missing figure raising none, no Finnhub figures at
all, a holding without closes, the 14 / 15 days of the timing note — for a scanned stock and a holding alike),
`RecommendationCandidateTest` (the signs and the note reaching it from Finnhub and the closes, the news score from the
analysis), `NewsScoreCalculatorTest` (every weight of
the table, a partnership without a size, `NOT_IN_THE_LIST` 0, an analyst's move without a target or 31 days old, the
parts the page shows — every event, only the moves that count), `AnalystTargetCalculatorTest` (each firm's latest target,
the half year's first day counted, the median of an odd and an even count, a pre-split target not moving it, under three
firms no target, exactly 15% enough) and
`RecommendationCalculatorTest` (a pick weighs 2 at most, the order, good news before the list's order but never past the
risk limit or the target rule, bad news after, the target exactly 15% / 14.9% / below the price / of two firms, the
reasons in the order of the checks) cover the Recommendations rules.
`PortfolioSyncServiceTest` (since MY_HOLDINGS_TODO.md's session 2 also: the sector and industry from the contract
details — empty words none, no details the old ones kept —, the holdings' targets replaced per stock, a holding the scan
found too stored once, Finnhub's data replaced per contract), `PortfolioReadServiceTest` (and each holding's analysts'
target and company info — with when Finnhub answered, `null` without an answer, `riskSigns` `null` for an answer with no
figure), `HoldingWriteServiceTest`,
`ManualPositionWriteServiceTest` and `InvestorWriteServiceTest` are `@DataJpaTest`s against the real
`portfolioboss_test` database (`src/test/resources/application-test.properties`, `@ActiveProfiles("test")`), each test
rolled back automatically. The test profile caps each context's pool at 3 connections: Spring keeps every test context
it builds, and ten database test classes at the default 10, with a running `./run.sh`'s 10, passed PostgreSQL's 100
("too many clients already", 2026-10-29). The write endpoints are tested in the same two halves: `HoldingWriteControllerTest`,
`ManualPositionWriteControllerTest` and `InvestorWriteControllerTest` (`@WebMvcTest`, service mocked) for status
codes, validation messages and the JSON-only rule, the `*WriteServiceTest`s for what is stored. `ClosedPositionTest`
and `HoldingHistoryTest` cover the average-cost math, `InvestorSummaryCalculatorTest` (INVESTORS_TODO.md's worked
example among them) and `PositionTradesTest` the split between investors, `OrderCommissionTest` the default commission,
`UtilsTest` the trimming of typed text. The analysis: **no test calls Tavily, Claude or Finnhub** (it costs money or
credits and needs the network). `ConsensusCalculatorTest` (AAPL and TTWO of the live check, MarketBeat first, the 1–5 scale's limits),
`AnalystTrendCalculatorTest` (price-target moves only, on NVDA, INTC and TTWO of the first run from the UI) and
`SignalCalculatorTest` cover the decisions; `SearchResultsTest` the symbol filter and the addresses it leaves out,
`StockSearchesTest` the four searches together (credits, filter); `TavilyClientTest` the four searches
against a fake Tavily (`MockRestServiceServer`); `StockAnalyzerTest` the request to Claude built without sending it — the
whole user message (nothing about the holding but symbol, currency and price), the model and effort, and the schema
derived from `StockAnalysisResult` (`null` allowed exactly where it says `@Nullable`, at most 16 such fields), plus that
the SDK's client builds with the Jackson in use, and the instructions' rules (the move reason from the news only, the news
events each once, magnitude only for a partnership or a contract, `NOT_IN_THE_LIST` never the closest type);
`StockAnalysisRepositoryTest` (`@DataJpaTest`) the JSONB round trip of the result
and the search results, analyses stored in earlier shapes still read (Claude's old `analystTrend`, no copy dates, no
search results, no dropped addresses, no move reason or news events), the latest analysis per holding leaving out the
scanned stocks', a scanned stock's analysis with Finnhub's answer or without, and the table refusing a row of both or
neither; `AnalysisServiceTest` (`@DataJpaTest`, Tavily
and Claude `@MockitoBean`s) a run, a failed stock, the search results stored exactly as Claude read them, 404 / 400 / 503; `AnalysisControllerTest` the status codes and the JSON-only rule.
The Recommendations side: `RecommendationsReadServiceTest` (`@DataJpaTest`, six made-up stocks with a year of closes
shaped for their lists) the lists, the stocks in none, a correlation of 1, a stock already held, a stock keeping its
latest analysis from an earlier scan with its risk signs and timing note, what a news score is made of with the weight and
score the pick would give, `riskSigns` / `riskWeight` / `score` `null` and `canBePicked` false without Finnhub's figures,
the latest recommendation with its own scan's date, the analysts' target from the stored targets (only HOTA has any),
the reasons of a stock never analyzed, the short list (`shortListOfLatestScan`, and `inShortList`) and none before a scan;
`RecommendationAnalysisServiceTest` (`@DataJpaTest`, Tavily, Claude and Finnhub `@MockitoBean`s, thirteen made-up stocks
— HOT01 … HOT11 a little more stretched each —, three firms' targets 30% above each) the first ten HOT analyzed and three
picked, two stocks with too little upside left off the short list (HOT11 then on it), none with enough upside — nothing
searched or asked, an empty recommendation stored —, a pick's upside, the price Claude is told, Finnhub's answer stored, a
pick over the risk limit, the lighter risk and good news first, Finnhub failing, Claude failing, one candidate without a
pick, the picks stored in order (an empty list too), 503 without a key — the full run without Finnhub's —, 404;
`RecommendationAnalysisControllerTest` its JSON (`picks` `null` for one stock), 415, 503, 404 and 405;
`RecommendationsControllerTest` its JSON, 503 and 405; `ScanServiceTest` (`@DataJpaTest`, `TwsReader` a `@MockitoBean`:
**no test connects to TWS**) a scan stored with its closes, nothing stored while busy or when TWS can't be read (the
exceptions pass through; their 409 / 503 are `ApiErrorHandler`'s); `ScanControllerTest` the status codes — the app's two
TWS exceptions turned into 409 and 503 with the user's message — and the JSON-only rule; `SyncServiceTest` (`@DataJpaTest`,
`TwsReader` and `FinnhubClient` `@MockitoBean`s, `@RecordApplicationEvents`) Sync now storing the holdings with Finnhub's
answer, only the holdings in USD asked about, no key no Finnhub, the scan asked for only when due, busy and unreachable
storing nothing, the startup's sync and TWS off at startup, and `PortfolioSyncedEvent` published once per stored sync and
never for a failed one; `SyncControllerTest` 200, 415, 503, 409, 405. The alerts (package `alerts`):
`HoldingAlertsCalculatorTest` each rule at its limit (a report in 7 days counts, 8 doesn't, today counts, past doesn't, the
settings' number of days; a cut 7 days back counts, 8 doesn't, a raise and a target without an earlier one don't, two
firms two conditions; exactly −15% counts, −14.9% doesn't; exactly the 15% weight limit doesn't, 15.1% does, a limit of
20 from the settings; a warning with its type as the subject; a closed holding has only its warning checked; a type
turned off), `AlertChangesCalculatorTest` (true twice in a row one alert, no longer true ends, back again a new alert, two
firms two alerts, a key twice in one check one alert), `PortfolioWeightsCalculatorTest` (the dollar holdings and the
cash; another currency, no market value, 0 or less left out; no cash, nothing), `AlertServiceTest` (`@DataJpaTest`,
the short list a `@MockitoBean`: nothing before the first sync, the first check, a second check adding nothing, ended
and back, a holding's warning, a short-list stock without a holding, a type off neither checked nor shown and its open
alert left open, saving the settings checks again, the newest first with each holding's id, "seen" and a later unseen
alert), `AlertGroupCalculatorTest` (the bell's two groups: a short-list stock not held, one held, every other kind held or
not), `AlertsReadServiceTest` (`@DataJpaTest`: the newest first with each holding's id, a stock not held without one, the
groups — a held stock's short-list alert in Your holdings, a sold one's in The short list, a sold holding's warning still a
holding's —, a type off not shown, each type's setting) and `AlertControllerTest` (the JSON — `group` too — and
`no-store`, 204, a number out of range or for a type without one, days not
whole, a type missing — 400 naming the line —, 415, 405); `FinnhubClientTest` Finnhub's two calls against a fake Finnhub (`MockRestServiceServer`,
shortened copies of its real answers): the earliest report of a calendar out of order, an hour not known yet, a missing
figure, an unknown symbol, a refused call, a key of spaces. The Flex reports (package `tax`; **no test calls IB**):
`FlexReportReaderTest` (a report of the real query's shape, made-up values, no account: a sale and a buy with IB's signs, a
conversion to HKD with its commission in dollars, a dividend and its tax, a fee with no stock and its cancellation, the
period IB wrote, empty sections, the three request statuses, a missing field and an unreadable one named, a `DOCTYPE`
refused, an answer that isn't XML; since session 3 IB's `fifoPnlRealized` and a trade without it, and a reverse split's
two detail rows read and a summary row skipped), `FlexClientTest` (a fake service, `MockRestServiceServer`: fetched once prepared, 1018
tried again, a refused request or report not tried again, 12 tries and still not ready, no answer and an HTTP error —
neither message holding the token —, the keys), `FlexDownloadScheduleTest` (the first download from the earliest typed
trade's year in periods of 365 days — 2024, a leap year, ends on 30 December —, a first of January on a Saturday moved
to the Monday, every period starting and ending on a weekday, the last 365 days without a trade, an update a week back,
an update on a Sunday ending on the Friday — the period IB refused in the live run —, nothing once the last weekday is
stored, a gap of more than a year, the first of January), `FlexReportStorageServiceTest` (`@DataJpaTest`: stored and
logged, the same days again add no row, a changed row replaced, a row sent twice in one report stored once, the last
stored date, IB's FIFO gain stored, a corporate action's rows stored and counted once) and `FlexDownloadServiceTest` (`@DataJpaTest`, `FlexClient` a `@MockitoBean`: no keys nothing asked, the
history oldest first, a failed period stopping the rest without an exception, an update, nothing once up to date);
`SyncServiceTest` has `FlexDownloadService` as a `@MockitoBean` (a stored
sync downloads up to its day, one that read nothing doesn't). The exchange rates (package `exchangerates`; **no test
calls the Bank of Israel**): `BankOfIsraelRatesReaderTest` (the real answer's shape — public rates around Yom Kippur 2025
—, the columns found by their names in another order, a header alone, a line with more fields than the header, a rate or
a day that can't be read and the column missing — each naming the line —, an empty answer), `BankOfIsraelClientTest` (a
fake server: the address in full, a weekend's 404 no rate, 15 days of 404 an error naming the series, an HTTP 500 without
the page, no answer), `ExchangeRatesTest` (the day's own rate, Yom Kippur and a Sunday the last before, after the last
stored and before the first none, nothing stored), `ExchangeRateDownloadScheduleTest` (nothing stored: two weeks before the
earliest day to today; every day known nothing; a holiday inside the stored stretch never asked again; a day after it from
the day after the last stored rate, one before it up to the day before the first, both two requests; a settlement day
after today not asked) and `ExchangeRateDownloadServiceTest` (`@DataJpaTest`, `BankOfIsraelClient` a `@MockitoBean`: the
first download stored, only the days after the stored ones asked, nothing asked when every day is known, a rate not
published yet asked again on the next sync, a failure printed and nothing stored); in `tax`, `UsSettlementDayEstimateTest`
(two weekdays before 28.05.2024, one from that day, a Friday's on Monday) and `SettlementDayRatesServiceTest`
(`@DataJpaTest`, `ExchangeRateDownloadService` a `@MockitoBean`: the Flex trades' and cash transactions' own settlement days
in dollars, the typed trades' estimated ones — a holding's and a manual position's —, HKD left out); `SyncServiceTest` has
`SettlementDayRatesService` as a `@MockitoBean` too (after the Flex reports, up to the sync's day; not when TWS wasn't
read). The tax itself (package `tax`, session 3; made-up trades, most at a rate of 1 through the helper `TaxTestData`):
`TaxableGainOfSharesFromOneBuyTest` (the four rows of decision 11's table), `FifoSalesCalculatorTest` (decision 3's
example — 150, not average cost's 100 —, a sale from two buys, a buy shared by two sales, the commissions spread by
quantity, a sale of more than was bought; since session 5 the average cost beside it — decision 3's example differs, the
average carried from one sale to the next, a sale of every share and a single buy the same as FIFO), `SaleTaxableGainTest` (per buy, not over the whole sale; a buy settled on a day
without a rate takes the one before; HKD, a rate not published yet and an over-sale not computed; a reason of the whole
stock first), `SettlementDayEntryTest` (six sales of a day one entry, a fee at its rate and its cancellation, a fee
without its rate left out, a sale not computed left out, agorot rounded half up; since session 5 the day's status — a tax
or a shield calculated, a 0, a sale or a fee waiting for its rate, a currency without rates, "can't be calculated" before
"waiting", an over-sale first), `TaxYearBalanceTest` (the plan's
example 2 month by month, the year's tax never below 0, a shield beyond what was paid, two days of a month settled
together, the month of the last sync not settled), `TaxYearCalculatorTest` (the balance from each entry's tax amount, the
year's taxable gain, its sales in order),
`CapitalGainsTaxCalculatorTest` (the plan's example 1: the account 2,000, the investor 2,500; the account's FIFO over every
investor's trades; a partial sale in 2025 and the rest in 2026 two years; a sale of 31 December settled in January the
new year's; a fee on a day without a sale), `FlexTradesAfterTheSplitsTest` (`SplitAdjustedFlexTradesTest` until
2026-11-22: a reverse split into a new contract translating the earlier trades, a split in the same contract translating
the open position, a position sold in full before the split untouched, a part of the open position sold before it
translated with it, IB's signs turned into the calculation's, a trade after it untouched, two splits in a row, an action
not stored as a split not followed, the shares held just before a moment, conversions left out, executions of one second
in the order of IB's ids as numbers, a commission in another currency), `SplitsInFlexCorporateActionsTest` (one row
adding the shares, two rows into a new contract, the words and the shares disagreeing, a trade missing, a stock not held,
words without "SPLIT n FOR m", money in the action, rows of another kind, a spin-off neither understood nor reported, a
later split checked against the shares an earlier one left), `OurFifoAgainstIbsFifoTest` (within a cent, more
than a cent, no gain of IB's, only sales), `CapitalGainsTaxConsoleLinesTest` (the lines of the account's years, an
investor's and the comparison, a sale that differs) and `CapitalGainsTaxServiceTest` (`@DataJpaTest`: nothing before the
first sync, the account's tax with a fee, another investor's at their Flex twins' settlement days, a typed trade without
a twin estimated, a stored reverse split followed — stored through `StockSplitDetectionService` —, an investor's trades
held through a stored split translated, a split not stored not followed); `SyncServiceTest` has `CapitalGainsTaxService`
as a `@MockitoBean` (printed after the rates, a failure not failing the sync, none when TWS wasn't read). The splits
(STOCK_SPLITS_TODO.md, session 1; made-up splits of 4 for 1 and 1 for 10): `StockSplitTest` (the arithmetic, a sale's
sign, 1 for 3 kept to 16 digits, a trade of the split's day before it and of the next day not, IB's time, the ratio in
IB's words), `StoredStockSplitsTest` (in time order; a holding's only those that kept its contract; `isStored`),
`TradesAfterTheSplitsTest` (before, on and after the split's day, a position sold in full before it untouched even when
bought again, a part sold before it translated, two investors, two splits, the commission as it was, no split nothing),
`HoldingHistoryTest` (`openPositionTrades`), `StockSplitDetectionServiceTest` (`@DataJpaTest`: stored, not stored again,
one not understood not stored, nothing without an action), `SyncServiceTest` (`StockSplitDetectionService` a
`@MockitoBean`: after the Flex reports and before the tax, at a startup without TWS too, a failure not failing the sync),
`PortfolioReadServiceTest` (no triangle after a split, each investor's quantity, the trades as typed with their
translation beside them, a position sold in full before it as it happened), `AlertServiceTest` (no `TRADES_DONT_MATCH_IB`)
and `PortfolioControllerTest` (`stockSplits`, `translatedTrades`). The confirmations (session 4):
`SettlementDayWithItsConfirmationTest` (waiting counts its calculated amount, confirmed its confirmed one — another
amount, a shield; the status the entry's while waiting, `CONFIRMED` once confirmed — an HKD day too), `TaxYearWithItsConfirmationsTest` (each day with its confirmation, example 2 with April confirmed as
−300 — the recorded balance moves, the calculated doesn't —, a confirmation whose day is no longer calculated kept as a
day of 0), `AccountTaxWithItsConfirmationsTest` (each confirmation in its settlement day's year, a year with only a
confirmation), `TaxConfirmationServiceTest` (`@DataJpaTest`: a day confirmed with its note trimmed, several days in one
request, a day confirmed already 409 with nothing of the request stored, a day without a calculated entry 400, none before
the first sync, a correction, a cancel, 404), `TaxesReadServiceTest` (`@DataJpaTest`: nothing before the first sync, the
days waiting with their calculated amounts, the months and a fee's rate, a sale's buys with both ways of its gain, a
confirmed amount counting in the year, a confirmed day keeping its amount when IB's report changes the calculation, an
investor's years never confirmed; since session 5 each day's status, and IB's report connected and stored up to its last
day — `FlexClient` a `@MockitoBean` — or not connected) and `TaxControllerTest` (`@WebMvcTest`: the JSON — `month` as
`"2026-06"`, a sale not computed without its shekels, since session 5 `flexConnected`, `flexReportsStoredUpTo`, the
statuses and the FIFO-against-average flag — and `no-store`, 503, 201, each 400 message, the service's 409 passed on, 204,
404, 415, 405). The three tax tests on the database share `TaxTestDataInTheDatabase` (an `@Import`ed helper: the synced holding,
IB's Flex rows, the rates and an investor's typed trades — one made-up year whose figures its comment works out). After a
change that tests depend on, prefer `mvn -q clean test`: a plain `mvn test` once skipped recompiling stale tests. There is no whole-app `@SpringBootTest`: it
would run `TwsPortfolioRunner` and connect to TWS. Trust `mvn`'s exit code, not the log: `-q` is silent on success, and
`target/surefire-reports/` keeps the reports of tests that have since been deleted.

Spring's own settings are in `src/main/resources/application.properties` (loopback address, port 8080,
startup banner off so it doesn't mix with the `[ib]` lines, the database connection, `ddl-auto=validate`,
`open-in-view=false`, Hibernate's JSON column mapper — see the analysis below — and
`spring.config.import=optional:file:config/local.env[.properties]`). The database user and password come from
`PORTFOLIOBOSS_DB_USER` / `PORTFOLIOBOSS_DB_PASSWORD`; unset, it is your macOS user with no password, which Homebrew's
PostgreSQL accepts.

**The analysis needs two API keys**, `TAVILY_API_KEY` and `ANTHROPIC_API_KEY`, as `KEY=VALUE` lines in the git-ignored
`config/local.env` — Spring reads it like `application.properties` (`optional:`, so the app starts without it), and an
environment variable of the same name wins. A missing key turns only the analysis off: the app starts as usual, prints
`[ai] analysis off: TAVILY_API_KEY is not set (config/local.env)` instead of `[ai] analysis ready`, and `POST
/api/analysis` answers 503 with that name. Claude never reads or prints the file — only checks a key's name and length.
Tavily's free plan has 1,000 credits a month; a full run of 7 stocks is 35 credits and about $0.30 of Claude, so a run
every day would pass the free plan and a few a week would not. **Finnhub's key**, `FINNHUB_API_KEY`, goes in the same
file and is optional too: without it the app prints `[stocks] Finnhub off: FINNHUB_API_KEY is not set (config/local.env)`
and only Finnhub's part is off — a scanned stock's on Analyze, and a holding's on every `./run.sh` (their stored answers
stay). Its free plan allows 60 calls a minute; a stock is 2, so a portfolio of 10 holdings is 20 calls a run.
**IB's Flex Web Service** (CAPITAL_GAINS_TAX_TODO.md, session 1) needs `FLEX_TOKEN` (made in IB's Client Portal under
Flex Web Service, valid up to a year, can be locked to the home IP address) and `FLEX_QUERY_ID` (the number of the
Activity Flex Query — XML, Trades as executions with `fifoPnlRealized`, Cash Transactions and Corporate Actions, its
fields in sessions 1.1 and 3.0 of the plan) in the same
file; without either the app prints `[tax] Flex off: FLEX_TOKEN is not set (config/local.env)` and downloads no report.
The Bank of Israel's rates (session 2) need no key.

**External dependency not in the repo:** the TWS API jar at `~/DevTools/twsapi/TwsApi.jar` (shared
with IBBot). It is not on Maven Central, so `run.sh` registers it once in the local Maven repository
(`~/.m2/repository/com/interactivebrokers/tws-api/local`, this machine only) and the `pom.xml`
depends on `com.interactivebrokers:tws-api:local` (`local` is just a label). After replacing the jar
with a newer TWS API, delete that `local` directory so the next `run.sh` registers it again; plain
`mvn` works only after `run.sh` has registered the jar once. `protobuf-java` is an ordinary Maven
dependency now, so `~/DevTools/google-proto-buf/` is no longer used.

**Running requires TWS/Gateway to be logged in** with *Configure → API → Enable ActiveX and Socket
Clients* on for a fresh sync. Without it (or if TWS is silent), `TwsReader` prints the
connection error, `TwsPortfolioRunner` skips the sync, and the app comes up anyway serving whatever the last successful sync
stored — an empty database (nothing has ever synced) still answers 503, same as before any sync.
The app itself never exits over TWS being unreachable; expect the stale-data case when running
outside the user's trading hours rather than treating it as a code bug.

**Running also requires PostgreSQL 17** (`brew install postgresql@17`, kept running with `brew services
start postgresql@17`) with two databases, `portfolioboss` and `portfolioboss_test` (tests only — never
point tests at the real one). The formula is keg-only, so `psql`, `createdb` and `pg_dump` are in
`/opt/homebrew/opt/postgresql@17/bin`, not on the PATH. Spring connects and runs the Flyway migrations
*before* `TwsPortfolioRunner` starts, so with PostgreSQL down the app exits with code 1 within seconds
(`Connection to localhost:5432 refused`, inside a long Spring stack trace) without touching TWS. Unlike a
TWS that is simply off, a stopped PostgreSQL is something to fix — a sync failure (a `DataAccessException`
or `TransactionException` from `PortfolioSyncService.sync`) is the only thing that still exits the app
(code 1, `[db error] ...`), because the database is required and TWS is not. To check the database side
without a live TWS run `./run.sh 7599`: nothing listens there, so it gets through Flyway and schema
validation and then serves whatever is already in the database (or 503 on a database that has never
synced) instead of exiting — stop it with Ctrl+C.

**Client id 101 is deliberate.** IBBot connects as client id `0`; TWS rejects duplicate ids, so
PortfolioBoss must keep a distinct one for both to be connected at once.

## Architecture

One flow, a few classes. The whole platform rests on a single IB call, `reqAccountUpdates`, which is
what delivers real quantity and **average cost** from the broker.

```
Main (Spring Boot: brings the web server up, then Spring runs the runner)
  └──> TwsPortfolioRunner ──> api.SyncService.syncAtStartup() (the same sync as Sync now's syncNow())
        │   SyncService ──asks ScanSchedule whether a scan is due, then──> TwsReader (the one place that connects;
        │                                                                     one reading at a time)
        │        TwsReader ──> IbGateway ──owns──> PortfolioWrapper ──> Holding (+conId), PortfolioSnapshot, Scan
        │                      (socket + reader loop)  (EWrapper callbacks,  (rows + contract details + the analysts'
        │                                               RequestBatches)       price targets in Dow Jones's headlines),
        │                                                                     DailyClose (a year per holding, SPY and
        │                                                                     scanned stock), and the holdings' contract
        │                                                                     details and analysts' price targets
        ├──after TWS, asks──> FinnhubClient ──HTTPS──> Finnhub (each holding in USD, the symbol only)
        ├──syncs──> PortfolioSyncService ──writes──> Postgres (holding · trade · account_state · daily_close · scan ·
        │                                                      analyst_price_target · stock)
        ├──publishes PortfolioSyncedEvent──> alerts.AlertsAfterSync ──> AlertService.checkAlerts() (alert rows)
        ├──then──> tax.FlexDownloadService ──> FlexClient ──HTTPS──> IB's Flex Web Service (the days not stored yet)
        │                                  └──> FlexReportStorageService ──writes──> Postgres (flex_trade ·
        │                                                 flex_cash_transaction · flex_corporate_action · flex_report)
        ├──then──> sync.StockSplitDetectionService ──reads── flex_corporate_action · flex_trade
        │            (calculation/tax: SplitsInFlexCorporateActions) ──writes──> Postgres (stock_split: each new split once)
        ├──then──> tax.SettlementDayRatesService (the settlement days in USD: Flex's, and the typed trades' estimated)
        │            └──> exchangerates.ExchangeRateDownloadService ──asks ExchangeRateDownloadSchedule which days are
        │                   missing, then──> BankOfIsraelClient ──HTTPS──> the Bank of Israel (only those days)
        │                   └──writes──> Postgres (exchange_rate)
        ├──then──> tax.CapitalGainsTaxService ──reads── Postgres (flex_* · exchange_rate · holding · trade · investor ·
        │            stock_split) (FlexTradesAfterTheSplits, CapitalGainsTaxCalculator, OurFifoAgainstIbsFifo)
        │            ──prints── [tax] lines
        └──────────> UiLauncher ──starts──> ui/'s `npm run dev`, then opens the browser (the runner, after the sync)

SyncController ──> SyncService.syncNow() ──> TwsReader.readPortfolioUnlessBusy() — the same sync as above, then the
        ▲                                    event; 409 while another reading runs, 503 when TWS can't be read
        └──JSON── POST /api/syncs — only from the top bar's Sync now, then a reload of /api/portfolio

AlertController ──> AlertService ──reads── Postgres (the holdings, closes, stock, analyst_price_target, the short list
        ▲           (alerts/: HoldingAlertsCalculator,    through RecommendationsReadService) ──writes── alert,
        │            AlertChangesCalculator)               alert_setting
        └──JSON── GET /api/alerts, POST /api/alerts/seen, PUT /api/alerts/settings — ui/'s bell and its settings drawer

tax.TaxController ──> TaxesReadService ──> CapitalGainsTaxService (the tax, as above) + tax_confirmation
        ▲             (tax/: AccountTaxWithItsConfirmations, TaxYearWithItsConfirmations, TaxYearBalance;
        │              FlexClient.hasKeys and FlexReportStorageService.lastStoredDate for the page's setup card)
        │         └─> TaxConfirmationService ──writes── tax_confirmation (only a day the calculation has an entry for)
        └──JSON── GET /api/taxes, POST /api/taxes/confirmations, PUT / DELETE /api/taxes/confirmations/{id} — ui/'s
                  Taxes page (session 5): Confirm, the pencil and Confirm the whole month, then a reload

PortfolioController ──reads── PortfolioReadService ──reads── Postgres      (independent of the flow above:
        │                     (builds api/response/*Response              driven by HTTP requests, not by TWS)
        │                      with calculation/HoldingHistory, InvestorSummaryCalculator, Momentum,
        │                      ConsensusCalculator, AnalystTrendCalculator, SignalCalculator,
        │                      AnalystTargetCalculator, RiskSignsCalculator)
        └──JSON──> ui/ (React; the dev server proxies /api to :8080)

RecommendationsController ──reads── RecommendationsReadService ──reads── Postgres (the latest scan, its stocks' closes
        │                           (with calculation/Momentum,             and analysts' price targets, each stock's
        │                            HotStockCalculator, ReturnsCorrelation, latest analysis by conId, the latest
        │                            AnalystTargetCalculator,               investment_recommendation)
        │                            RecommendationCandidate, NewsScoreCalculator)
        └──JSON──> ui/'s Recommendations page

ScanController ──> ScanService ──> TwsReader.readScan() ──> TWS (a scan, contract details, Dow Jones's headlines,
        ▲                  │                                     closes; read-only, free)
        │                  └──writes──> Postgres (PortfolioSyncService.syncScan: a scan row, its targets and closes)
        └──JSON── POST /api/scans — only from the Recommendations page's Scan again, then a reload

AnalysisController ──> AnalysisService ──> StockAnalysisPipeline ──per stock, all at once──> TavilyClient ──> Tavily
        ▲              (keys: ai/AiKeys)    (ai: the searches, then Claude)                    StockAnalyzer ──> Claude
        │                      └──writes──> Postgres (stock_analysis rows only, with the search results Claude read;
        │                                             a failed stock writes nothing)
        └──JSON── POST /api/analysis — only from ui/'s Analyze buttons (every open holding, or one), then a reload

RecommendationAnalysisController ──> RecommendationAnalysisService ──> StockAnalysisPipeline (the same, per stock)
        ▲                            (the short list: the first 10 HOT ──> FinnhubClient ──HTTPS──> Finnhub (beside it)
        │                             with the analysts' target 15%+,   then RecommendationCalculator: the pick (code)
        │                             RecommendationsReadService
        │                             .shortListOfLatestScan)
        │                                    └──writes──> Postgres (stock_analysis rows of scanned stocks, with
        │                                                 Finnhub's answer; an investment_recommendation row per full run)
        └──JSON── POST /api/recommendations/analysis — {} the short list and the pick (Analyze & recommend), or one
                  candidate (its Analyze, in its row or drawer); only from ui/'s Recommendations page, then a reload

HoldingWriteController ──writes── HoldingWriteService ──writes── Postgres  (trade rows only; never IB, never
        ▲  (api/request/*Request, @Valid;                                 a holding row created or deleted,
        │   ApiErrorHandler → ProblemDetail JSON)                         never the sector: IB's)
        └──JSON── ui/'s trade modal (ui/src/lib/apiClient.ts), then a reload of /api/portfolio

ManualPositionWriteController ──writes── ManualPositionWriteService ──writes── Postgres  (manual_position rows and
        ▲                                                                          their trades; never a holding)
        └──JSON── ui/'s Closed page (the new-position modal; a manual position's drawer: details and trades)

InvestorWriteController ──writes── InvestorWriteService ──writes── Postgres  (investor and investor_cash_movement
        ▲                                                                    rows; never the account owner's cash)
        └──JSON── ui/'s Investors page: the cards, their modals (add, rename) and each one's deposits
```

The sync-at-connection lifecycle, which is the part worth knowing:

1. `IbGateway.connect()` opens the socket and starts a **daemon** `ib-reader` thread pumping
   `EReader.processMsgs()` on each signal.
2. IB calls back `managedAccounts` → `PortfolioWrapper` takes the first account and issues
   `reqAccountUpdates(true, account)`.
3. `updatePortfolio` fires once per position, keyed by **`conId`** (IB's stable contract id, not
   `symbol` — two positions can share a symbol); zero-quantity ones are removed as closed-out.
   `updateAccountValue` picks out only `NetLiquidation` and `TotalCashValue`.
4. `accountDownloadEnd` stores a `PortfolioSnapshot` (exposed by `IbGateway.snapshot()`), prints the
   report, **unsubscribes** (`reqAccountUpdates(false, …)` — we want a snapshot, not the ~3-minute
   live updates), and counts down a `CountDownLatch`.
5. `TwsReader.readPortfolio(withScan)` — or Sync now's `readPortfolioUnlessBusy(withScan)`, the same reading
   (`readEverythingOnOneConnection`) — is blocked on that latch with a 15s timeout. With a snapshot, **still
   connected**: when `withScan` (`SyncService` asked `ScanSchedule` first), the scans for hot stocks
   (`IbGateway.requestHotStockScans`: `SCAN_histPerfRatio3m_DESC` and `…6m_DESC`, `STK.US.MAJOR`, `CORP`, the filters
   `priceAbove` 15, `marketCapAbove1e6` 2,000, `avgVolumeAbove` 1,000,000 — `ib.HotStockScan` —, up to 50 rows each,
   10s), then `reqContractDetails` for every stock found (15s) — a scan's row carries no exchange or sector —; then,
   scan or not, `reqContractDetails` for every holding the scan didn't already ask about (15s; `readContractDetailsOfHoldings`
   — a holding's sector and industry, MY_HOLDINGS_TODO.md decision 11, and the name its headlines are read with); then half a
   year of Dow Jones's headlines of every stock kept **and every holding, in the same rounds**, each stock once
   (`IbGateway.requestNewsHeadlines`: `reqHistoricalNews`, provider `DJ-N`, 300 a page, every stock at once, round after
   round back in time — `ib.NewsHeadlinePaging`, at most 8 rounds of 20s; a stock without a company name isn't read) and
   the analysts' price targets in them (`ib.PriceTargetHeadlineReader`, decision 14), split back into the scan's
   (`Scan.withAnalystPriceTargets`) and the holdings' (`TwsReading.holdingAnalystPriceTargetsByConId`); then the
   daily closes: `IbGateway.requestDailyCloses` sends one `reqHistoricalData` per contract
   — every holding, SPY (`ib.Benchmark.SPY_CON_ID` = 756733, IB's fixed id, requested whether or not SPY is
   held) and every stock the scan kept, by conId + `SMART`, `"1 Y"` of `"1 day"` `TRADES` bars, regular hours, dates as
   `yyyyMMdd` — and waits up to 20s (`awaitDailyCloses`); late or failed contracts are just left out (`[ib] daily closes
   received for N of M contracts`). Then it disconnects and returns a `TwsReading` (the snapshot, the closes, the scan —
   `null` when none was due or none answered —, the holdings' contract details and targets). `api.SyncService` asks
   Finnhub about every holding in USD, all at once on virtual threads (`readFinnhubDataOfHoldings`, through
   `FinnhubClient.stockOrNullOnFailure`: a failed call prints `[stocks error]` and leaves that holding out; nothing
   without the key), hands both to `PortfolioSyncService.sync(reading, finnhubDataByConId)`, which upserts them into
   the database (see below), and publishes `api.PortfolioSyncedEvent` — the alerts are checked on it, in the same
   thread, before the sync returns (a failed check prints `[alerts error]` and keeps the sync). `TwsPortfolioRunner`
   (`syncAtStartupOrExit`), synced or not, then launches the UI and returns; Tomcat's non-daemon threads keep the
   JVM alive until Ctrl+C. If the sync itself throws (a database problem, not a TWS problem) it prints
   `[db error]` and calls `System.exit(SpringApplication.exit(applicationContext, () -> 1))`: the database
   is required, TWS is not. On Sync now the same failure is only the request's error; the app goes on.

`ib.TwsReader` (a `@Service`, since 2026-10-26) is the one place that connects to TWS: it owns the host, the port and
the client id (`ApplicationArguments`: `./run.sh 7497 102`), opens a new `IbGateway` per reading and disconnects at its
end, and a `ReentrantLock` lets one reading through at a time — TWS refuses a second connection with the same client
id. `readPortfolio` (the startup) waits for the lock and answers `null` when TWS can't be read; `readPortfolioUnlessBusy`
(Sync now, 2026-11-11 — the same reading, `readEverythingOnOneConnection`, which opens and closes its own connection) and
`readScan` (the Scan again button: a scan, its contract details and the closes of its stocks and SPY, the portfolio that
comes with every connection not kept) take it only if free, else `exceptions.TwsBusyException` with
`ANOTHER_READING_RUNS` ("TWS is already being read — by a sync, a scan or the startup: try again in a moment."), and turn
any failure — no socket, refused, no portfolio in time, no scan answered — into `exceptions.TwsUnavailableException`,
whose message is the user's ("TWS is not reachable: open TWS and log in, then try again.") and whose `reason()` is the
console's. `api.ApiErrorHandler` answers the two with 409 and 503 for every endpoint that reads TWS (since 2026-11-11;
`ScanService` turned them into `ResponseStatusException`s itself before), so the services let them pass.

`TwsPortfolioRunner` is an `ApplicationRunner`, so Spring runs it once, **after the web server is
already listening** — a request that arrives while TWS is still being read (up to 15s) gets a 503 from
the controller, not an error. Since 2026-11-11 it holds no flow of its own: the sync is `api.SyncService.syncAtStartup()`
(whether a scan is due, the reading, Finnhub, the store, the event — what Sync now's `syncNow()` runs too); the runner
exits on a database failure and launches the UI. It is a `@Component` of its own rather than a `@Bean` method on `Main`
on purpose: Spring's test slices (`@WebMvcTest`) load everything declared on the main class and run
every `ApplicationRunner`, so a runner declared on `Main` connected to the real TWS from every test.
Slices skip plain `@Component`s.

`PortfolioWrapper extends DefaultEWrapper` so only the needed callbacks exist. IBBot's IB layer is
**not** importable here (its `IBTraderBot` *is* one giant `EWrapper`); this project writes its own
small wrapper on purpose.

Error handling note: `INFO_CODES` in `PortfolioWrapper` filters IB's informational status codes
(165 — a scan's "50 out of 992 items retrieved", which ended a scan before its rows in session 0 —, 2104, 2106, …) to
stdout; codes 502/504 mean connection failure and release the latch early so the
program reports a clear message instead of waiting out the timeout. The scans, the contract details, Dow Jones's
headlines and the daily closes are `ib.RequestBatch<Key, Answer>`s (one per kind: requests numbered from 100, 5000,
10,000 and 1000, each answered and then ended by its end callback or an error; the headlines a new batch per round of
pages, `PortfolioWrapper.startNewsRound`, numbered from its own thousand, so that a late answer of an earlier round is
dropped, its id unknown to the current one; a second `expect` on the same batch — the holdings' contract details after
the scan's — numbers on from the last id and waits for its own requests only, keeping the first ones' answers: numbered
from the start again, its ids would already count as ended and it would wait out its timeout): an error carrying one of a batch's request ids (162, no market data
permission) ends that one request without answers (`[ib error] daily closes of contract …`) — a contract keeps the
closes of an earlier sync; an error after the request ended (IB confirming a cancelled scan) is dropped without a line,
and so is an answer after the end (a scan updating itself). The answers arrive on the reader thread while the startup
thread waits, so they sit in concurrent collections; `answersOfFinishedRequests()` serves only requests that have ended
(a timeout leaves a half-received one out), and a bar whose date can't be read is skipped — an exception there would stop
the reader loop's whole batch.

`ib.Holding` is a record mirroring `updatePortfolio` exactly — the broker is the source of truth, so no
field is hand-entered — plus `conId`, IB's stable contract id, its second component. Derived math
(`costBasis`, `unrealizedPnlPercent`) lives on the record. `PortfolioSnapshot`, `DailyClose` and `Benchmark` sit next to
it in `ib`, and so do the scan's: `ScanRow` (one row of one scan), `StockContractDetails` (exchange — `primaryExch`, not
the scan row's `marketName`, which for many NYSE stocks is the symbol —, name, industry, category; named after
`reqContractDetails`, and not plain `ContractDetails`, the IB class `PortfolioWrapper` reads it from), `Scan` (the rows
and the contract details, from which `candidates()` derives one `ScanCandidate` per stock, NYSE / NASDAQ only, with its place in each scan,
and `leftOutStocks()` the console line; and, since decision 14, `analystPriceTargetsByConId` — a second constructor
without it, for before the headlines are read, and `withAnalystPriceTargets(…)`, the same scan with them), and
`TwsReading` / `ScanReading`. The headlines' (decision 14, 2026-10-29): `NewsHeadline` (IB's time, article id and text;
`TIME_FORMAT` is how IB writes a time and wants one back), `NewsHeadlinePaging` (each stock's pages back in time: a stock
is done when its page was not full, brought nothing new, came back empty or reached half a year), `PriceTargetHeadlineReader
(companyName)` (a record: `priceTargetIn(headline)`) and `AnalystPriceTarget(publishedAt, firm, priceTarget,
previousPriceTarget, headline)`, whose `MONTHS_COUNTED` (6) is both how far back the scan reads and the half year the
median is taken over. What IB reports lives with the code that reads it, and `calculation`, `db` and `api` use only these
records, never the IB API itself. Things that are easy to break:
- IB's `reqHistoricalNews` answers with the headlines *before* `startDateTime` and ignores `endDateTime` — the opposite of
  their names (tried 2026-10-29: a start of 2025-10-29 brought headlines of 2024–2025) —, so a page back puts the oldest
  headline so far in `startDateTime`. `DJ-N` keeps about half a year; Briefing.com's `BRFUPDN` goes back years but is
  sparse and isn't read.
- Dow Jones writes a target in four forms only — "X Price Target Raised to $450.00/Share From $300.00 by Firm", "Cut to",
  "Maintained With a", "Announced at" (no "/Share" after the "From" amount) —; "Price Target Set" is only IBD's "SpaceX
  IPO Price Target Set". A new wording drops targets without an error: the `[ib] analysts' price targets` line shows it.
- A stock's feed carries other companies' headlines: a target counts only when each word of Dow Jones's name is the start
  of IB's word in the same place ("Hewlett Packard Ent" ↔ "HEWLETT PACKARD ENTERPRISE"; a leading "The" skipped). A plain
  "letters in order" match was rejected: "Apple" would have matched "APPLIED MATERIALS".

The database layer, `portfolioboss.db`, is now connected to the flow: `PortfolioSyncService.sync` runs
once per connection (called from `TwsPortfolioRunner`, step 5 above), and the API reads only from the
database, never straight from TWS. `HoldingEntity`, `TradeEntity` and `AccountStateEntity` map the tables
`holding`, `trade` and `account_state`, created by `src/main/resources/db/migration/V1__portfolio_schema.sql`.
`sync` upserts by `(account, conId)`: a holding reported by IB is created (`new HoldingEntity(account,
holding, syncedAt)`) or refreshed (`refreshFromIb` — IB's figures only, `status = OPEN`; it never touches
`trades`), and, when its contract details came, `refreshSectorAndIndustryFromIb(contractDetails)` (MY_HOLDINGS_TODO.md,
decision 11 — only the sync writes them; an empty word is `NULL`, details that didn't come keep the old ones); an open
holding IB no longer reports is marked `CLOSED` (`markClosed`), never deleted, so the trades typed in by hand survive
(and its last sector); a `CLOSED` holding that reappears is reopened
automatically. `AccountStateEntity` holds one row, replaced by a new instance (`new AccountStateEntity(snapshot)`)
on every sync. All of this runs in one `@Transactional` method, so a snapshot is stored whole or not at all.
The full sync is `sync(TwsReading reading, Map<Integer, Stock> finnhubDataByConId)`; `sync(snapshot)`,
`sync(snapshot, dailyCloses)` and `sync(snapshot, dailyCloses, scan)` pass it what they don't have as empty (a
`TwsReading` constructor without the holdings' details and targets), so what is stored of those stays.
The hand-entered side is written only by `api.HoldingWriteService`, `api.ManualPositionWriteService` and
`api.InvestorWriteService` (below), through the `TradeEntity` constructors,
`changeDetails` and `changeInvestor`, `ManualPositionEntity`'s constructor and `changeDetails`, `InvestorEntity`'s
constructor and `changeName`, and `InvestorCashMovementEntity`'s constructor and `changeDetails`. `V2__closed_positions.sql` added `trade.commission`
(`NOT NULL`: a trade entered without one stores the default, `calculation.OrderCommission.defaultFor` — 1 cent a share,
at least $5 an order, IBBot's rule — worked out at write time; V2's own comment still calls it
`Utils.calculateOrderCommission`, its name then, since a migration that has run is never edited). `V3__manual_positions.sql` added `manual_position`
(`ManualPositionEntity`): a position PortfolioBoss never saw as a holding, sold before the first sync, kept apart from
`holding` because only the sync creates holdings. Its buys and sells are ordinary `trade` rows — a trade belongs to a
holding **or** a manual position (`holding_id` / `manual_position_id`, exactly one set, checked by the table) — so they
are computed and corrected the same way. V3 also turned V2's one-row-per-round-trip `manual_closed_position` into
manual positions (same ids, one buy and one sell) and dropped it. `V4__investors.sql` added `investor`
(`InvestorEntity`: a name, unique, and `is_account_owner` — at most one, the row "Me" the migration created) and
`investor_cash_movement` (`InvestorCashMovementEntity`: date, `CashMovementType` `DEPOSIT` / `WITHDRAWAL`, amount, note
— only for investors other than the owner), and made `trade.investor_id` `NOT NULL`, every trade entered before it
the owner's. `TradeEntity.investorId()` is a plain id, not a link to the entity: the computation needs only the id,
and the UI gets the names from `investors`. `V5__daily_closes.sql` added `daily_close` (`DailyCloseEntity`: `con_id`,
`bar_date`, `close_price` — an IB figure, so `DOUBLE PRECISION`; a generated `id` plus `UNIQUE (con_id, bar_date)`,
since a two-column key needs a separate key class in JPA): a year of daily closes per contract, for the momentum, keyed by
IB's contract id rather than by holding because SPY is read whether or not it is held. `sync(snapshot, dailyCloses)`
replaces in full the rows of every contract IB sent closes for — IB adjusts past prices for splits, so nothing old is
kept — and leaves the others alone; `sync(snapshot)` is the same with no closes. `V6__stock_analysis.sql` added
`stock_analysis` (`StockAnalysisEntity`): one row per holding per analysis run — `holding_id` (a plain id, like
`investorId`), `analyzed_at`, `model` (the one that actually answered), `result` (`JSONB`: Claude's answer, the
`ai.StockAnalysisResult` record, mapped with `@JdbcTypeCode(SqlTypes.JSON)` — a JSON column takes a schema change without a
migration), the tokens, Tavily's credits and `cost_usd`; every run is kept, the UI shows the latest.
`V7__stock_analysis_search_results.sql` added `stock_analysis.search_results` (`JSONB`, `NULL` before 2026-10-10): the
`ai.StockSearches` Claude read for that analysis — the four searches' results that name the stock, and the addresses of
those that didn't — so that a fact missing from an analysis can be traced to the search or to Claude (decision 22); the
API doesn't serve it, it is there for a look in SQL. `V8__trade_price_required.sql` (2026-10-13) made `trade.price`
`NOT NULL`: every trade has a price, which every calculation on the trades needs (all 40 stored then had one).
`V9__scans.sql` (2026-10-26) added `scan` (`ScanEntity`: `scanned_at`, `scan_codes` as a JSON list) and `scan_candidate`
(`ScanCandidateEntity`: `con_id`, `symbol`, `company_name`, `exchange`, `industry`, `category` — IB's words, an empty
text stored as `NULL` —, `rank_by_scan_code` as a JSON map, and `close_at_scan` / `close_date`, the stock's last close
read in the same run: the price the measurement will start from), saved together (`cascade = PERSIST`); every scan is
kept, the page shows the latest. `sync(snapshot, dailyCloses, scan)` stores the scan in the same transaction (a `null`
scan stores none; the two older overloads pass none), `syncScan(scan, dailyCloses)` is the Scan again button's — a scan
and its closes without a portfolio —, and `lastScannedAt()` is what `ScanSchedule` asks.
`V10__candidate_analysis.sql` (2026-10-27, on the real database since then) let `stock_analysis` hold a scanned stock's
analysis too: `holding_id` may be `NULL`, a new `scan_candidate_id`, and the table checks that exactly one of the two is
set (`num_nonnulls`, like `trade`'s); `finnhub_stock` (`JSONB`, the plan's `company_facts` renamed — it holds the whole
`stocks.Stock`, the earnings date too) is what Finnhub answered, `NULL` when it didn't and for a holding's analysis.
`StockAnalysisEntity` has two public constructors: a holding's (`long holdingId`) and a scanned stock's, which takes the
`ScanCandidateEntity` itself, not its id, so a holding's id can't be passed by mistake; `holdingId()` is a `Long` now.
`ScanCandidateEntity.scanId()` maps `scan_id` a second time, as a plain id read only (`insertable` / `updatable` false),
so a candidate's scan is known without loading it — `null` on a candidate persisted in the same session, until it is read
again. `V11__investment_recommendations.sql` (on the real database since the first full run from the page, 2026-10-27)
added `investment_recommendation` (`InvestmentRecommendationEntity`: `scan_id`, `recommended_at`, `picks` — the
picked `scan_candidate` ids in order, as a JSON list; an empty list when none qualified, an answer the measurement counts);
only the picks: why each was picked is worked out again from its stored analysis.
`V12__analyst_price_targets.sql` (2026-10-29, decision 14; backed up before, on the real database since that day's
`./run.sh`) added `analyst_price_target` (`AnalystPriceTargetEntity`: `con_id`, `published_at`, `firm`, `price_target`,
`previous_price_target` — `NULL` when the headline names none —, `headline`; the firm and the headline `TEXT`, so that no
length can fail a scan; an index on `con_id`, no `UNIQUE`): one row per target headline, keyed by contract id like
`daily_close`. A sync stores the scan's targets and the holdings' together, merged by contract so that a holding the scan
found too is stored once (`analystPriceTargetsOfScanAndHoldings`), and `syncScan` the scan's: a stock whose headlines
were read gets them in place of the old ones (an empty list too), one whose headlines didn't come keeps its old ones.
`V13__stocks.sql` (MY_HOLDINGS_TODO.md, session 2, decision 10) added `stock` (`StockEntity`: `con_id` `UNIQUE`,
`symbol`, `fetched_at`, `finnhub_data` — `JSONB`, the `stocks.Stock` as it is; the user named the table and the column):
what Finnhub answered about each holding in USD, replaced per contract by every sync that has an answer for it
(`storeFinnhubData`), kept when the call failed. `V14__holding_industry.sql` added `holding.industry`, IB's `category`
("Semiconductors"; the holding's `sector` is IB's `industry`, "Technology" — the market's names, which IB has the other
way round; `scan_candidate` keeps IB's). Both reached the real database on 2026-11-04 and 05 in runs that missed TWS,
before a backup — harmless, a table and a column added empty; the 2026-11-05 backup (14 of 14 tables equal) came before
the first run that wrote to them. `V15__alerts.sql` (MY_HOLDINGS_TODO.md session 5ב, 2026-11-11; on the real database
since that day, after `portfolioboss-2026-11-11.sql.gz`, 14 of 14 tables equal) added `alert` (`alert_type`, `con_id`,
`symbol`, `subject` — what tells two alerts of one type on one stock apart, `NULL` when nothing must —, `message`,
`created_at`, `ended_at` — `NULL` while the condition is still true: the alert is open —, `seen_at`) with a partial unique
index, `alert_one_open_per_condition` on `(alert_type, con_id, subject) NULLS NOT DISTINCT WHERE ended_at IS NULL` (one open
alert per condition even with a bug, and the index that finds the open ones), and `alert_setting` (`alert_type` the key,
`enabled`, `threshold` `NUMERIC(5,2)`, `NULL` for a type without a number; a row only for a type saved). Their entities and
repositories live in the `alerts` package (below), not in `db`. `V16__flex_reports.sql` (CAPITAL_GAINS_TAX_TODO.md
session 1, 2026-11-15; on the real database since that day, after `portfolioboss-2026-11-15.sql.gz`, 16 of 16 tables
equal) added IB's Flex reports, stored as IB wrote them, signs included: `flex_trade` (one row per execution:
`ib_trade_id` `UNIQUE`, `ib_order_id`, `con_id`, `symbol`, `asset_category` — `STK`, or `CASH` for a conversion —,
`currency`, `trade_date`, `trade_time` — New York's time as IB writes it, `TIMESTAMP` without a zone —, `settle_date` — IB's
`SettleDateTarget` —, `buy_sell`, `quantity`, `trade_price`, `ib_commission`, `ib_commission_currency`, `net_cash`,
`transaction_type`), `flex_cash_transaction` (dividends, tax withheld, Other Fees and their cancellations:
`ib_transaction_id` `UNIQUE`, `transaction_type`, `con_id` and `symbol` — `NULL` for a fee —, `currency`, `amount`,
`transaction_time`, `settle_date`, `description`) and `flex_report` (the downloads' log: `from_date` / `to_date` as IB wrote
them, `downloaded_at`, the two counts). Amounts are `NUMERIC` without a limit on their digits — the tax is computed from
them to the agora, and no value of IB's may be rounded or refused —, IB's text is `TEXT`; no account number. Their
entities and repositories live in the `tax` package (below). `V17__exchange_rates.sql` (CAPITAL_GAINS_TAX_TODO.md session
2, 2026-11-15) added `exchange_rate` (`currency` `VARCHAR(3)`, `rate_date`, `rate` `NUMERIC(12,6)` — shekels for one unit,
the Bank of Israel's up to 3 decimals —, `UNIQUE (currency, rate_date)` with a generated `id`, like `daily_close`): the
Bank of Israel's representative rates, one unbroken stretch per currency, a row never replaced. Its entity and repository
live in the `exchangerates` package (below). `V18__flex_corporate_actions_and_ib_fifo_pnl.sql` (CAPITAL_GAINS_TAX_TODO.md
session 3.0, decision 15, 2026-11-15; on the real database since that day, after `portfolioboss-2026-11-15.sql.gz`, 20 of
20 tables equal) added `flex_corporate_action` (one row per row of an action, as IB wrote it: `ib_transaction_id`
`UNIQUE`, `ib_action_id` — the rows of one action share it —, `action_type` — `RS`, `FS`, … —, `con_id`, `symbol`,
`asset_category`, `action_time`, `quantity` with IB's sign, `proceeds`, `amount`, `description`), `flex_report
.corporate_action_count` (`DEFAULT 0` for the logs before it) and `flex_trade.fifo_pnl_realized` (IB's own FIFO gain of
the execution, `NULL` for a row stored before the field was in the query, until a download of its days replaces it). Its
entity and repository live in the `tax` package too. `V19__tax_confirmation.sql` (CAPITAL_GAINS_TAX_TODO.md session 4,
decision 7, 2026-11-16; on the real database since that day, after `portfolioboss-2026-11-16.sql.gz`, 21 of 21 tables
equal) added `tax_confirmation` (`settlement_day` `DATE` `UNIQUE` — one confirmation for a day, as Phoenix nets a day into
one entry —, `tax_amount` `NUMERIC(20,2)` — shekels to the agora, positive tax to pay, negative a shield —, `confirmed_at` —
when confirmed or last corrected —, `note` `VARCHAR(500)`): only what the user entered; the calculated amount is never
stored. Its entity and repository live in the `tax` package too. `V20__stock_splits.sql` (STOCK_SPLITS_TODO.md session 1,
2026-11-22; on the real database since that day, after `portfolioboss-2026-11-22.sql.gz`, 22 of 22 tables equal) added
`stock_split` (`ib_action_id` `UNIQUE` — a split stored once, whatever number of reports bring it —, `symbol`,
`con_id_before_the_split` / `con_id_after_the_split` — equal for a split in the same contract —, `split_time` — IB's,
New York's time —, `shares_before_the_split` / `shares_after_the_split` — IB's "SPLIT 4 FOR 1" as 1 and 4, two whole
numbers since 1 for 3 has no exact decimal, each `CHECK > 0` —, `stored_at`): written only by
`sync.StockSplitDetectionService`, never changed. `StockSplitEntity` and `StockSplitRepository` (its default method
`storedStockSplits()`, a `calculation.splits.StoredStockSplits`) live in `db/splits`.
`HoldingRepository`
(also `findByStatusOrderById`, the open holdings an analysis of everything runs on), `TradeRepository`,
`ManualPositionRepository`, `InvestorRepository` (`findAllByOrderById` with the cash movements in the same query, and the
default method `accountOwner()`), `InvestorCashMovementRepository`, `AccountStateRepository`, `DailyCloseRepository`
(`findByConIdInOrderByConIdAscBarDateAsc`, its default method `dailyClosesByConId` — the same closes grouped by contract —,
`dailyClosesWithSpy(conIds)` — the same with SPY's added, as a `calculation.DailyClosesWithSpy` (`closesOf(conId)`,
`momentumOf(conId)`), what both read services and the alerts use (2026-11-11: the line that measured a stock's momentum
against SPY was written twice, and the alerts would have been a third) —, and `deleteForContracts` — a hand-written `@Modifying @Query` bulk `DELETE`,
because a derived `deleteBy…` loads every row and deletes them one at a time), `StockAnalysisRepository`
(`findLatestOfEveryHolding` — PostgreSQL's own `DISTINCT ON (holding_id)` in a `nativeQuery`, with `WHERE holding_id IS
NOT NULL`: `DISTINCT ON` would keep one of the scanned stocks' rows as if it were a holding's —, and
`findLatestOfEveryScannedStock(conIds)`, `DISTINCT ON (scan_candidate.con_id)` through a join: a stock found again in each
day's scan keeps its analysis; `conIds` must not be empty, PostgreSQL refuses `IN ()`), `ScanRepository`
(`findFirstByOrderByScannedAtDesc`, its candidates read lazily — an `@EntityGraph` with a "first" would load every scan
into memory —, and `findLatestScannedAt`, a `max` in JPQL), `ScanCandidateRepository` (`findAllById` only: which stock an
analysis or a pick of an earlier scan was of), `InvestmentRecommendationRepository`
(`findFirstByOrderByRecommendedAtDesc`), `AnalystPriceTargetRepository` (`findByConIdIn`, its default method
`analystPriceTargetsByConId` — empty for no contract —, and `deleteForContracts`, like the closes') and `StockRepository`
(`findByConIdIn`, its default method `stocksByConId` — the rows, so that the read has `StockEntity.fetchedAt()` too —,
`deleteForContracts`) are the repositories.
Things that are easy to break:
- Flyway runs the migrations at startup, and a migration that has run is never edited (Flyway checks its
  checksum): a schema change is a new `V21__….sql`.
- `db.JsonColumnMapper` (named in `application.properties`, `hibernate.type.json_format_mapper`) is how Hibernate writes
  and reads a `JSONB` column: the newer Jackson, the API's, with its own defaults. Left to itself Hibernate picked the
  older Jackson the Anthropic SDK brings and stored a date as `[2026, 10, 5]`; now it is `"2026-10-05"`. It wraps
  Hibernate's own `Jackson3JsonFormatMapper`, which is `final`.
- A daily close IB sends twice for the same date is stored once (the later value): a duplicate would break
  `daily_close`'s `UNIQUE` constraint and, with it, the whole sync — which exits the app.
- `ddl-auto=validate` makes Hibernate check the entities against the tables at startup and change nothing;
  never set it to `update` or `create`.
- Figures from IB are `DOUBLE PRECISION` columns under `Double` fields; amounts typed in by hand
  (`trade.quantity`, `price`, `commission`) are `NUMERIC` under `BigDecimal`. A `NUMERIC` column under a `Double` field
  fails validation at startup (tried).
- `HoldingEntity` (a stored row), `ib.Holding` (one IB reading) and `HoldingResponse` (what the UI gets)
  are three different things on purpose — `HoldingEntity.toIbHolding()` rebuilds a `Holding` from the stored
  row (`NULL` → `NaN`) so the derived math is never duplicated outside `Holding`.
- `scripts/backup-db.sh` dumps the database to `~/PortfolioBossBackups`, outside the repo.
- `./run.sh` applies a new migration before it knows whether TWS answers, so a run outside trading hours applies it too:
  back up before **any** `./run.sh` that brings a migration, not only before the one meant to sync (2026-11-04, V13).
- Database tests (`PortfolioSyncServiceTest`, `PortfolioReadServiceTest`, `HoldingWriteServiceTest`,
  `ManualPositionWriteServiceTest`, `InvestorWriteServiceTest`, `StockAnalysisRepositoryTest`, `AnalysisServiceTest`,
  `RecommendationsReadServiceTest`, `RecommendationAnalysisServiceTest`, `ScanServiceTest`, `SyncServiceTest`,
  `alerts.AlertServiceTest`, `tax.FlexReportStorageServiceTest`, `tax.FlexDownloadServiceTest`,
  `tax.SettlementDayRatesServiceTest`, `tax.CapitalGainsTaxServiceTest`, `tax.TaxConfirmationServiceTest`,
  `tax.TaxesReadServiceTest`, `exchangerates.ExchangeRateDownloadServiceTest`, `sync.StockSplitDetectionServiceTest`)
  are `@DataJpaTest`s
  against the real `portfolioboss_test` (see Build & run) — never PostgreSQL is mocked out.
- A migration that moves real data (V3, V4) is tried first on a scratch database holding a copy of the real one, and
  `scripts/backup-db.sh` runs before the `./run.sh` that applies it (for V4 the backup was also restored into a scratch
  database and compared table by table first). One that only adds a table or a column (V5, V6, V7, V9, V10, V11, V12,
  V13, V14, V15, V16, V17, V18, V19, V20) or
  a constraint (V8, after counting the rows it would refuse: none; V10's, which every stored row met) still gets the
  backup first, checked for the same row counts as the database (V12's: each table's `COPY` rows in the dump against a
  `count(*)`, 12 of 12 equal, 2026-10-29). `backup-db.sh` names a backup by the day, so a second backup
  the same day overwrites the first: rename the earlier one first (2026-10-27: `…-before-V10.sql.gz`). A live check that writes test data (a made-up investor) runs against a
  scratch copy too, pointed at with `SPRING_DATASOURCE_URL`: there is no way to delete an investor.

`portfolioboss.calculation` holds only the computation — split on 2026-10-09 at the user's request, since most of its
files were not calculations: `HoldingHistory`, `ClosedPosition`, `TradeFact`, for the investors `PositionTrades`,
`InvestorPart`, `CashMovementFact`, `InvestorSummary`, `InvestorSummaryCalculator`, for the momentum `Momentum`, for the
analysis `ConsensusCalculator`, `AnalystTrendCalculator` and `SignalCalculator`, for the Recommendations page
`HotStockCalculator`, `ReturnsCorrelation`, `ScanSchedule`, `AnalystTargetCalculator`, `RecommendationCandidate`,
`RecommendationCalculator` and `NewsScoreCalculator`, `RiskSignsCalculator` (the risk signs and the timing note, a
scanned stock's and a holding's), `OrderCommission` (the default commission), and since 2026-11-11 `DailyClosesWithSpy`
(stored closes with SPY's: a stock's momentum) and `PortfolioWeightsCalculator(openHoldings, cash)` (a holding's share of
the whole account — the dollar holdings worth more than 0 plus IB's cash, not `netLiquidation` —, the twin of
`wholeAccountOf` in `ui/src/components/graphs/portfolioWeights.ts`: change both together) — pure Java, no
Spring and no database, so it is unit tested directly. What it computes on comes from four packages of plain data:
`ib` (what IB reports, above), `ai` (Claude's answer, below), `stocks` (Finnhub's) and **`model`** — the values the
whole app shares: the enums the entities and the JSON share with the computation (`TradeSide`, `HoldingStatus`,
`CashMovementType`), the warnings (`HoldingWarning`, `HoldingWarningType`, `InvestorWarning`, `InvestorWarningType`), the
labels (`MomentumLabel`, `AnalystTrend`, `HoldingSignal`, `HotStockLabel`, `RiskSign`, `NotPickableReason`),
`AnalystConsensus`, and the news score's parts with their points (`WeighedNewsEvent`, `WeighedAnalystMove`) (see
Conventions for which package uses which).
A holding's
`firstBuyDate`, `lastSellDate`, `holdingDays` and closed positions are derived from its `trade` rows, never stored:
`HoldingHistory.of(List<TradeFact>)` walks them in chronological order (a buy before a sell on the same date) tracking
a running quantity, and splits them into **position periods** (the private record `PositionPeriod`): a buy while flat
opens one, and the sell that brings the quantity back to (near) zero — or below it, an over-sell the quantity check
then points at — closes it. A long-term holding is often sold in full and bought again later, and without this the
first-ever buy date would belong to an unrelated stretch of ownership. The dates come from the last period; every
period that **has a sell** — closed, or still open after a partial sell — becomes a `ClosedPosition` in
`HoldingHistory.closedPositions`: the shares sold so far, at **average cost** (chosen over FIFO in CLOSED_POSITIONS_TODO.md). The private
`AverageCostCalculator` walks the period's trades: each share sold costs the average of the shares held at that
moment and takes the same share of the buy commissions with it, and the sell back to zero takes whatever is left, so
a closed period adds up exactly. `ClosedPosition` keeps raw totals — dates, `boughtQuantity`, `soldQuantity`,
`soldCost`, `sellProceeds`, `commissions` (every sell's, plus the buys' part that left with the shares sold),
`remainingQuantity` (0 once closed) and `tradeIds` — from which it derives the average prices (buy over the shares
sold, sell over the shares sold) and `realizedPnl` with its percent of `soldCost`. Every trade has a price (V8), so the
only period with no realized P&L is one that sold more than it bought (an over-sell) — the extra shares' proceeds
would count as pure profit — and `ClosedPosition.warning()` says so in English. A sell entered while already flat is treated as a data-entry mistake and silently ignored, never rejected. `holdingDays` counts to `lastSellDate` when `CLOSED`, or to the last sync's date (`account_state.as_of`,
not the system clock, so the number doesn't drift between page loads) when `OPEN` — and is never negative: a
buy dated after that sync (entered today while serving an older sync, e.g. with TWS off) counts as 0 days. `HoldingRepository
.findByAccountOrderById`'s `@EntityGraph(attributePaths = "trades")` loads a holding's trades in the same
query instead of one extra query per holding (`ManualPositionRepository.findAllByOrderById` does the same);
`TradeEntity.toTradeFact()` reduces a row to what the computation needs (id, investor, date, side, quantity, price,
commission). `HoldingEntity.tradeHistory()` is the history of all of a holding's trades together — its dates and
warnings in `HoldingResponse`. `toPositionTrades()`, on `HoldingEntity` and `ManualPositionEntity` alike, builds a
`PositionTrades` (symbol, currency, the IB figures or `null` for a manual position, every trade) that splits them by
investor: `historyOf(investorId)` runs `HoldingHistory.of` on one investor's trades only, so the **closed positions are
per investor** (one investor can sell out of a holding the other still holds), and `quantitiesByInvestor` gives each
other investor the sum of their trades and the account owner IB's position minus those. `partsByInvestor` turns that
into an `InvestorPart` per investor (`quantity`, `sharesValue`, `sharesCost`, derived `unrealizedPnl` and its percent),
worked out the same way as the cards — another investor's value is their quantity × IB's market price and their cost
`heldCost`, the owner's are IB's value and cost minus the others' — so the parts add up to IB's row for the holding and,
over every holding, to each investor's card: `InvestorSummaryCalculator` sums the same `sharesValueOf` / `sharesCostOf`.

The investors (INVESTORS_TODO.md): the **account owner is the residual** — the owner's cash, total value and shares cost
are IB's minus the other investors', so the cards always add up to exactly what IB reports, missing trades or not.
Another investor's cash = deposits − withdrawals − buys + sells − commissions (`TradeFact.cashFlow()`,
`CashMovementFact`), derived on every read and never stored; their shares value is their quantity × IB's market price,
their cost `HoldingHistory.heldCost` (the average cost of the shares still held, from the same `AverageCostCalculator`).
Profit is from the shares only, the same for everyone: unrealized = value − cost, realized = their closed positions,
**per currency** (`realizedPnlByCurrency`); everything else is USD only. `InvestorSummaryCalculator` (its inputs as
record components: the owner's id, the investor ids, every `PositionTrades`, the cash movements, IB's cash and NAV)
returns an `InvestorSummary` per investor (`depositsMinusWithdrawals` — `null` for the owner —, `cash`, `sharesValue`,
`totalValue`, `sharesCost`, `realizedPnlByCurrency`, `warnings`; derived `unrealizedPnl`, its percent and `totalPnl`).
Another investor's cash is always known, since every trade has a price; a figure IB did not report (a market price, the
cash) makes what depends on it `null`, never a guess. `InvestorWarning(type, message)` — several at once, shown, never
blocking: `TRADES_NOT_IN_USD` (left out of the cash), `NEGATIVE_CASH`, `MORE_SHARES_THAN_IB`,
`CLOSED_POSITIONS_NOT_COUNTED` (an over-sell: no realized P&L to add). `TRADES_WITHOUT_PRICE` was removed with V8. Dividends, interest and IB's own fees
are in IB's cash, so they land on the owner; they count toward no one's profit.

`HoldingHistory.warnings(status, ibPosition)` checks the trades entered against IB, which stays the source of truth
for the quantity — a gap is shown next to the holding, never a reason to reject a trade. It returns **at most one**
`HoldingWarning(type, message)`, the one to fix first: `NO_TRADES_LOGGED` (no buy at all), else `CLOSED_WITHOUT_SELL`
(a `CLOSED` holding whose current position period no sell ends), else `QUANTITY_MISMATCH` (`netQuantity` more than `0.0001`
from IB's `position`). Without that order a holding with no trades would also be a mismatch, the same gap twice.
Things that are easy to break:
- `netQuantity` is the plain sum of **every** trade (`TradeFact.signedQuantity()`), including a sell entered while
  flat that the dates ignore — so the quantity check still points at it.
- A `NaN` IB position skips the quantity check: `BigDecimal.valueOf(NaN)` throws, which would be a 500.
- Prices and average cost are never compared: IB's average cost includes commissions and would never match.

The momentum (AI_ANALYSIS_TODO.md, decision 5) is derived on every read from the stored daily closes, never stored:
`Momentum.of(closes, spyCloses)` (`null` without a single close) keeps the raw figures as record components — `asOf`,
`lastClose`, `sma20` / `sma50` / `sma200` (simple averages of the last 20 / 50 / 200 closes), `high20` (the highest of
the last 20), `oneMonthReturnPercent` and SPY's over the same calendar month (from the last close on or before the same
day a month earlier, so a weekend takes the Friday) — and derives five checks, a point each: `aboveSma20`, `aboveSma50`,
`sma50AboveSma200`, `nearHigh` (at most 10% below `high20`, inclusive) and `beatsSpy`; `score()` counts them and
`label()` maps it (`MomentumLabel`: `STRONG` 4–5, `NEUTRAL` 2–3, `WEAK` 0–1). A figure there are not enough closes for
is `null`, so is every check that needs it, and the score and label exist only when all five checks do — never a
guess. Appended for the Recommendations page (and in every holding's `MomentumResponse` too): the year's figures over
every close given — a year, what IB sends —, `firstCloseDate`, `high52Weeks`, `yearReturnPercent` (the last close against
the first) and `maxDrawdownPercent` (the biggest fall from a close to a later, lower one), and derived `monthsInMarket()`
and `percentBelowHigh52Weeks()`. Appended for the alerts (2026-11-11): `aboveSma200()`, beside `aboveSma50()` — not one
of the five checks, so the score doesn't change. The private record `DailyCloses` sorts one contract's closes once and
computes the figures from them, so only `of` is static. Things that are easy to break:
- `beatsSpy` is a strict "greater than", so SPY itself can never score more than 4/5.
- The 20-day average and the one-month comparison react fast on purpose (an early warning after a drop): the label
  changes far more often than a 200-day rule would.
- IB's `"1 Y"` starts the trading day after the same date a year earlier (in the real database: 17.10.2025 for a sync on
  16.10.2026), so a "close on or before a year ago" never exists: the year's return is from the first close, and
  `monthsInMarket()` is 12 when that close is at most a week after a year back — otherwise the whole months a young stock
  has traded (the user chose that over "unknown"). IB sends no more than a year, so a ten-year-old stock shows 12 too.

The Recommendations rules (RECOMMENDATIONS_TODO.md, decision 1) are derived on every read from the stored closes, like the
momentum. `HotStockCalculator(momentum)`: `hasLongUpwardTrend()` — the last close above the 200-day average and the
50-day above the 200-day (`null` under 200 closes) —, then `label()` (`model.HotStockLabel`) by `percentAboveSma50()`:
`HOT` 0–15% (15 inclusive), `TOO_HOT` above, `CORRECTING` below, `null` without the long trend; `pulledBack()` — `HOT`
only, 5–15% below `high52Weeks` —, `recovering()` — the year's return below 0, a mark, not a filter —, and
`comparePlaceInList(other)`, the page's order as an ordinary method (`HotStockCalculator::comparePlaceInList` sorts): the
lists in the enum's order, within `HOT` pulled back first, then the higher score, then the less stretched; within the
other two the closest to the 50-day average first (the user's choice). `ReturnsCorrelation(closes, otherCloses)`:
`coefficient()`, Pearson's correlation of the daily returns on the dates both have a close (each return from the previous
shared date, so a missing date is left out of both), `null` under 100 shared dates or when a price never moved;
`movesTogether()` from 0.7. `ScanSchedule(lastScannedAt, now)`: `isScanDue()` — before the first scan, or when the latest
New York close (`latestMarketClose()`, 16:00 on a weekday, holidays unknown) came after the last scan: once a trading day,
measured in New York because the market closes late in the evening here. The thresholds (15%, 5–15%, 0.7, 100 days) are
opening numbers the measurement of session 5 is meant to test.

The analysts' target (RECOMMENDATIONS_TODO.md, decision 14, session 4ב, 2026-10-29) is derived on every read too, from the
stored Dow Jones targets: `AnalystTargetCalculator(targets, lastClose, scanDate)` — `latestTargetOfEachFirm()` (each
firm's latest target of the half year before the scan's day, that day's first one included — `AnalystPriceTarget
.MONTHS_COUNTED` —, the newest first), `firmCount()`, `medianTarget()` (the middle one, or the average of the two in the
middle; `null` under **3 firms** — an opinion, not a target, the user's rule), `upsidePercent()` against the last close,
and `hasEnoughUpside()` — **15%** or more, exactly 15% included through a `ROUNDING_TOLERANCE` as in `HotStockCalculator`
($115 against $100 is 14.999999999999991%). The short list is the first 10 HOT stocks with enough upside
(`RecommendationsReadService.shortListOfLatestScan()`, an `api.ShortList(scanId, hotStockCount, withEnoughUpsideCount,
candidates)`), so the list's order decides who is analyzed only when more than 10 pass. Things that are easy to break:
- The **median, not the average** (the user's choice after it was found): a target is in the price terms of its own day,
  and CRWD split 1:4 in July 2026 — its firms' targets $600–825 before, $230–300 after, IB's closes already adjusted. With
  some firms not updated since, the average came out +4.8%, the median −5.8%. A limit on a target's distance from its
  day's close doesn't separate them: real targets reached 2.85 times the close (ASST, NIQ), the pre-split ones began at 2.3.
- On the 41 HOT stocks of 2026-10-27: 7 with enough upside (SNOW, PRCH, ADPT, PLTR, PENG, ASST, HPE), and on the 11 that had
  a MarketBeat consensus too, the two on the same side of 15% every time (decision 14 has the comparison with Tavily: 47
  of its 59 moves with a target found in Dow Jones the same, none contradicting).

The recommendation (RECOMMENDATIONS_TODO.md, decisions 5, 13 and 14, sessions 3 and 4ב) is picked by code.
`RecommendationCandidate(candidateId, hotStock, analystTarget, stock, analysis)` — a scanned stock as the pick sees it: its
`AnalystTargetCalculator`, Finnhub's `Stock` and Claude's `StockAnalysisResult`, the last two `null` without one — gives
`riskSigns()`, `hasCompanyFacts()` and `reportsWithin14Days(today)` through `RiskSignsCalculator(stock, momentum)`
(MY_HOLDINGS_TODO.md, session 2: the rules taken out unchanged so that a holding has them too — Finnhub's answer and
the closes are all they need; `momentum` may be `null`, a holding without closes): `riskSigns()` (`model.RiskSign`, in
its order: `LOSES_MONEY` net
margin below 0, `HIGH_DEBT` debt more than twice the equity, `EXPENSIVE` P/E above 50, `REVENUE_FALLING` growth below 0,
`RECOVERING` from `Momentum.lowerThanAYearAgo()` — the year's return below 0, which `HotStockCalculator.recovering()`,
the page's mark, reads too; a figure Finnhub didn't give raises no sign — a loss-making company
has no P/E), `hasCompanyFacts()` (at least one of the four figures: without any, "no sign" only means "not checked", and
the stock can't be picked — the user's rule) and `reportsWithin14Days(today)` (the timing note: a
report 0–14 days ahead, not a sign); the candidate itself adds `newsScore(today)`.
`NewsScoreCalculator(newsEvents, recentActions, today).score()` weighs the events
Claude classified — a partnership or a major contract by its magnitude (`HIGH` 3, `MEDIUM` 2, `LOW` 1, none given = 1),
every other kind by kind (`GUIDANCE_RAISED` +3, `EARNINGS_BEAT` +2, `REGULATORY_APPROVAL` +2, `PRODUCT_LAUNCH` +1,
`GUIDANCE_CUT` −3, `EARNINGS_MISS` −2, `SHARE_OFFERING` −2, `LAWSUIT_OR_REGULATION` −2, `NOT_IN_THE_LIST` 0) — and the
analysts' upgrades (+2) and downgrades (−2) of `recentActions` that state a price target and are from the 30 days before
`today` (a rating alone moves nothing, as in the trend: the grading services drop out with no list of names).
`RecommendationCalculator(shortList, today).picks()`: the stocks that `canBePicked` — no `reasonsNotPickable(candidate)`
(`model.NotPickableReason`, in the order of the checks: `NO_ANALYST_TARGET` or `TARGET_TOO_CLOSE` from the target, then
`NO_COMPANY_FACTS` or `RISK_SIGNS_TOO_HEAVY` — signs that weigh more than **2** —; a stock can have one of each) —, the
signs weighed `LOSES_MONEY` 1, `HIGH_DEBT` 2, `EXPENSIVE` 1, `REVENUE_FALLING` 3, `RECOVERING` 1 (the user's weights: the
risks don't all threaten the same), then the highest `scoreOf` — the news score less the risks' weight — first, on a tie
the list's order, up to 3; none qualifying is "no recommendation this time". The target and the risk limit are filters,
never offset by news: the risk limit was 4 until the news could add, and a loss with a debt 16.8 times the equity (3)
then passed on one partnership. All the weights and limits are opening numbers. Things that are easy to break:
- The two weight tables are `Map.of`s keyed by enum: a new `RiskSign` or `NewsEventType` without a weight is a
  `NullPointerException` on the first stock that has it — add both together.
- Claude was stable on the kinds (three checks of 2026-10-27 on stored searches: 10 of 10) but not on magnitude where the
  rule didn't fit (an FDA approval `HIGH` then `MEDIUM`, a downgrade `MEDIUM` then `LOW`): that is why only partnerships
  and contracts have one, and why analysts' moves are counted by the code from `recentActions`, not as news events.
- The news is one week, and most of it is about the price itself: in the run of 2026-10-27, 3 events across 10 stocks. The
  score moves the order seldom, and on purpose.

The stock analysis (AI_ANALYSIS_TODO.md, session 2) lives in `portfolioboss.ai` (console `[ai]` / `[ai error]`).
`AiKeys` holds the two keys (`@Value("${TAVILY_API_KEY:}")` — the colon's empty default keeps a missing key from stopping
the app), says which is missing (`findMissingKeyName()`), and prints the `[ai]` line once the app is up
(`@EventListener(ApplicationReadyEvent.class)`). `TavilyClient` runs four searches (decisions 2, 19 and 21) through
Spring's `RestClient` (no SDK, 30 s each), all with `include_published_date` and `include_usage`:
`searchAnalystForecasts(symbol)` (`topic: general`, a month, `advanced`, 2 credits — forecast pages, for the consensus),
`searchMarketBeatAnalystActions(symbol)` (`news`, a month, `include_domains: marketbeat.com`, `basic`, 1 credit —
MarketBeat's articles, each listing the analysts' recent reports with both targets), `searchLatestAnalystActions(symbol)`
(`news`, a week, any site, 1 credit — the newest moves, often in roundups of many companies) and `searchNews(symbol)`
(`news`, a week, 1 credit). The two action searches exist because a forecast page keeps one address and the search
engine's copy of it lags one to three weeks, while every analyst move is a new article the same day. A `SearchResults` is
the results (`SearchResult`, read straight from Tavily's JSON), the credits and `droppedUrls`; `mentioning(symbol)` keeps
the results that name the symbol as a whole word, in any case (decision 17), and the addresses of the others.
`StockSearches` holds the four together, with `credits()` and its own `mentioning(symbol)`. `StockAnalyzer` is the one call to
Claude (`claude-sonnet-5-5`, effort `low`, 16,000 max tokens, 60 s, no tools): fixed instructions, and a user message of
`StockToAnalyze(symbol, currency, marketPrice)`, today's date and the four searches' results as given, each inside tags
that mark them as data (`analyst_`, `marketbeat_`, `latest_actions_`, `news_search_results`). The instructions take the
consensus from the analyst search only (so MarketBeat's pages aren't counted twice in the spread), up to 10 actions from
any results, each once, only this stock's from a roundup, a date without a year as the latest such date not after today,
each action's result date as `sourcePublishedDate` (decision 20, shown in the UI as "copy of …"), and the headlines and
the sentiment from the news search only — and (RECOMMENDATIONS_TODO.md, decisions 4 and 13, 2026-10-27) from the news
only too: `moveReason` (why the price has been moving, one sentence with the result it rests on; a result that only
reports the move is no reason) and `newsEvents` (each event once, of a fixed kind — `NOT_IN_THE_LIST` when none fits,
**never the closest kind**, the user's rule after an FDA approval came back as a partnership —, analysts' moves left to
`recentActions`, debt no share offering, magnitude only for a partnership or a contract and by what the result says:
one of the world's largest companies or an amount it calls material `HIGH`; general knowledge allowed about the other
company only, never about the stock). It asks for structured output: the SDK derives the JSON schema from the record `ai.StockAnalysisResult`
(`consensusBySource` of `SourceConsensus` with a nested `RatingCounts`, `recentActions` of `AnalystAction`, `headlines`
of `Headline`, `sentiment`, `sentimentReason`, `moveReason` (`MoveReason`), `newsEvents` of `NewsEvent` — its compact
constructor turns the key's absence in older analyses into an empty list), the API holds Claude to it, and the SDK reads the answer back into the
record — one definition for the schema, the answer, the `JSONB` column and the JSON. Until 2026-10-09 it also held
Claude's own `analystTrend`; the analyses stored then still have the key, which Jackson 3 skips when reading (its
default for a key with no component). It also sends
`fallbacks: "default"` (beta `server-side-fallback-2026-07-01`: a refusal in the "cyber" or "frontier_llm" category is
retried on Sonnet 5). A refusal or an answer cut off at the limit fails the stock; `ClaudeReply` is the result, the model
that actually answered, the tokens and their dollars ($2 / $10 per million, constants). `ai.StockAnalysisPipeline`
(2026-10-27, taken out of `AnalysisService` at the user's ask — the name had to say what it is: the steps of a stock's
analysis in order) has one public method, `analyzeAll(stocks, today)`: every stock at once on virtual threads (the four
searches one after another, `StockSearches.mentioning` once, then Claude), a failure caught as the stock's own
`StockAnalysisOutcome(symbol, reply, searches, failureMessage)` with an `[ai error]` line, and a `StockAnalysisRun` — the
outcomes **in the order of the stocks given** (the callers pair them by index) and the run's sums — whose
`[ai] analyzed N of M stocks: …, $0.2056` line it prints; it stores nothing. `api.AnalysisService` — not
`@Transactional`, so no connection waits on the network — checks the keys (503), finds the holdings (`{}` or `null`: every
open one; an unknown id 404, a closed holding 400), runs them through the pipeline and stores the ones that succeeded with
one `saveAll` — each with the very `StockSearches` Claude read (decision 22). A failed stock comes back as a
`FailedAnalysisResponse(symbol, message)`, stores nothing and keeps its previous analysis. The answer,
`AnalysisRunResponse(analyzed, failed, tavilyCredits, inputTokens, outputTokens, costUsd)` (a copy constructor from the
`StockAnalysisRun`), is the same figures as the line.

`api.RecommendationAnalysisService` (2026-10-27, not `@Transactional` either) is the Recommendations page's paid run:
`analyze(null)` takes the short list — `RecommendationsReadService.shortListOfLatestScan()`: the first 10 `HOT` with the
analysts' target 15%+ above the last close, in the page's own order (`api.CandidateWithItsList`: a candidate, its list —
the user's name for it — and, since decision 14, its `AnalystTargetCalculator`) —, `analyze(id)` one
candidate of any list (not in the latest scan's lists: 404). Keys missing: 503 — and for the full run a missing
`FINNHUB_API_KEY` too, before anything is sent, since without Finnhub no stock can be picked. Claude is told the symbol,
`"USD"` (NYSE / NASDAQ only) and the last stored close. Finnhub runs beside the pipeline on its own virtual threads (it
answers in a second); without the key, or when a call fails (`[stocks error] SYMBOL: why`), the stock is analyzed and
stored without Finnhub's answer — and can't be picked. A separate Finnhub stage before the paid analysis was planned and
dropped (the user's choice, 2026-10-29): the target already cuts the list to a handful, and it would have saved only a
stock that passes the target and fails on its signs, at the cost of a table for Finnhub's answers without an analysis.
The analyses are stored with `finnhub_stock`; after the full run `RecommendationCalculator` picks, the `[ai] recommended
ABCD, WXYZ (of 7 analyzed; 7 of 41 hot stocks have the analysts' target 15% above the price)` or `[ai] no recommendation:
none of 7 qualified (…)` line is printed, and an `investment_recommendation` row is stored whenever a scan exists — an
empty one too when no stock had enough upside, with nothing searched or asked (the pipeline prints `[ai] analyzed 0 of
0`). The answer: `RecommendationAnalysisRunResponse(analysis, picks)` — `picks` of `RecommendationPickResponse
(candidateId, symbol, riskSigns, reportsWithin14Days, newsScore, score, targetUpsidePercent)`, `null` for one candidate's
run.

What is decided from an answer is code, derived on every read, so a change of rule applies to old analyses too:
`ConsensusCalculator(sources, marketPrice).consensus()` (decision 16) chooses **MarketBeat first** — when one of its
pages gives a target (the site in the address is `marketbeat.com`), the choice is made among MarketBeat's pages only —,
then a source with a rating breakdown and a target, then the most analysts, the later date, the address; the rating is
worked out from the breakdown on one scale (Strong Buy 1 … Strong Sell 5; up to 1.5 / 2.5 / 3.5 / 4.5), else it is the
source's own label; the spread of the targets is over every source — a `model.AnalystConsensus`.
`AnalystTrendCalculator(recentActions)` (decision 18) is the `model.AnalystTrend`, from **price-target moves only**: an
action counts when it states the target before and after — `raisedTargetCount()` against `loweredTargetCount()`; more
raised `IMPROVING`, more lowered `DETERIORATING`, as many (or targets stated and none moved) `STABLE`, no action with a
target `null`. A rating alone moves nothing, so the services that grade without a target — Zacks Rank, Weiss Ratings,
Wall Street Zen — drop out with no list of names (the user's rule). Claude decided the trend until 2026-10-09 and answered
differently from run to run (NVDA: `IMPROVING` with one raised target, then unknown with two); for a day the code also
counted upgrades and downgrades, and a Zacks downgrade made TTWO `DETERIORATING`.
`SignalCalculator(momentumLabel, analystTrend, sentiment).signal()` (decision 6) is the `model.HoldingSignal`: red for two
warning signs or more (weak momentum, deteriorating analysts, negative news), green for none, yellow for one, `null` while
fewer than two of the three are known. Things that are easy to break:
- The SDK makes a field nullable only for an annotation named `Nullable` on the field itself: `jakarta.annotation
  .Nullable` works, JSpecify's (a type annotation) would not be seen. Claude accepts at most 16 nullable fields in one
  schema (15 now, with `moveReason` and `newsEvents[].magnitude`) — that is why the five counts are one nullable
  `RatingCounts`, and why `newsEvents` is a list, empty rather than `null`.
- Removing a component from a record in `ai` is safe for the stored analyses (the old key is skipped) and so is adding
  one (the missing key reads as `null` — `SearchResults`' compact constructor turns a missing `droppedUrls` into an empty
  list); renaming one loses that field from every analysis stored before.
- The trend rests on what the searches happened to bring — up to 10 actions, from results that differ between runs (NVDA
  was `IMPROVING` at one run and `STABLE` an hour later, when Daiwa's cut came back too; `search_results` shows which) —
  and the rule is coarse on purpose: one small target cut is `DETERIORATING`, and one firm's two moves both count (INTC:
  Mizuho down on 9/3, up on 10/6 — "only each firm's latest move" is an open idea). The UI shows the counts.
- The search engine's copy of a page can be weeks old (INTC's stockanalysis.com page: 9/18, read on 10/10), so a row seen
  in an analysis may be gone from the live page; the UI shows each source's copy date. Tavily Extract (tried once on
  MarketBeat) returned the same old copy, and reading MarketBeat's pages directly is against its terms (decision 7).
- The records in `ai` *are* the schema: a new component is a new required field Claude must fill. Computation on them
  belongs in `calculation`, not on the records.
- The SDK's client checks the Jackson version when it is built, at startup (`StockAnalyzerTest` builds one); Spring Boot
  manages the SDK's Jackson 2 next to its own Jackson 3.
- Tavily doesn't return the same chunks of a page every time, so a figure in a stored analysis may not show in a later
  search of the same page.

Finnhub (RECOMMENDATIONS_TODO.md, session 2) lives in `portfolioboss.stocks` (console `[stocks]`) — its own package
rather than `ai` (the user's call, 2026-10-27): its figures don't come from Claude, and the code computes on them (the risk
signs of decision 5), not only Claude. `FinnhubClient` holds its own key (`@Value("${FINNHUB_API_KEY:}")`,
`hasApiKey()`, the `[stocks] Finnhub off` line at startup) and has two public methods — `stockOrNullOnFailure(symbol,
today)` is the second, the same answer with a failed call printed as `[stocks error] SYMBOL: why` and given as `null`,
what both callers use — and the first, `stock(symbol, today)`: two GETs
through Spring's `RestClient` (10 s each), with the key in the `X-Finnhub-Token` header, never in the address —
`/calendar/earnings` from `today` (the caller's, so the tests don't depend on the clock) to four months ahead, and
`/stock/metric?metric=all`. It returns a `Stock(symbol, nextEarningsDate, companyFacts)`: an `EarningsDate(date, hour)`,
read straight from Finnhub's JSON (`hour` is `bmo` / `amc` / `dmh`, or `null`), and `CompanyFacts(revenueGrowthPercent,
netMarginPercent, debtToEquity, peRatio)`, from Finnhub's `revenueGrowthTTMYoy`, `netProfitMarginTTM`,
`totalDebt/totalEquityQuarterly` and `peTTM` (private records under Finnhub's names, the one with a "/" through
`@JsonProperty`); a figure Finnhub doesn't give is `null`. `Stock` is what Finnhub knows about a stock — not its price,
name or exchange, which come from IB (`ib.StockContractDetails`, renamed from `StockDetails` on 2026-10-27 so it isn't read
as "the details of a `Stock`"). The two are deliberately not one type: IB's come with every scan for every stock found,
Finnhub's only on Analyze for the short list, so half of a merged type would always be empty; they meet side by side in
`calculation.RecommendationCandidate` — a candidate and its `Stock` —, and the `Stock` is stored in
`stock_analysis.finnhub_stock`. `api.RecommendationAnalysisService` calls it (since 2026-10-27) from the Recommendations
page's Analyze, and `TwsPortfolioRunner` (since 2026-11-05, MY_HOLDINGS_TODO.md decision 10) for every open holding in
USD on each `./run.sh` that reached TWS — that `Stock` stored in the `stock` table's `finnhub_data`, read by
`PortfolioReadService` (a holding's `calculation.RiskSignsCalculator`). Things that are easy to break:
- The units, read on AAPL (2026-10-27): the margin and the growth are **percent** (`27.62` is 27.62%), the debt to equity
  a **ratio** (`0.7844`; "twice the equity" is `2.0`), the P/E a ratio.
- The calendar is **not in date order** (AAPL's January report came before November's): the earliest is taken, not the
  first. An hour not known yet comes as `""`, read as `null` by `EarningsDate`'s compact constructor.
- An unknown symbol is a 200 with `"metric": {}` and `"earningsCalendar": []` — every figure `null` and no date —, not an
  error; a missing or wrong key is a 401, which throws.

`PortfolioController` (Spring MVC) serves `GET /api/portfolio` through `PortfolioReadService`
(`@Transactional(readOnly = true)`, since `open-in-view=false` means entities must be read inside the
transaction): 503 until a sync has ever stored an `account_state` row, then 200 with `Cache-Control:
no-store` and a `PortfolioResponse` built from the latest sync — including closed holdings, which the UI
filters. `PortfolioReadService` puts the whole response together: its private methods build each holding
(`holdingResponseOf`), the closed positions and the investors' cards. The response records only hold the result — at
most a constructor that copies fields from one entity or one computed object (`TradeResponse(TradeEntity)`,
`MomentumResponse(Momentum)`, `InvestorResponse(InvestorEntity, InvestorSummary)`, …). They live in
`api/response/`; Jackson
turns them into JSON, so their component names **are** the JSON keys (`ui/src/types/portfolio.ts` mirrors them, by
hand: nothing checks that the two agree, and a key the UI no longer finds is `undefined`, not an error) — a key is
renamed or removed only together with `ui/src/types` and every reader of it, in the same change (MY_HOLDINGS_TODO.md
session 3 moved four so, into `companyInfo`); otherwise add fields. Beyond the original IB
fields, `HoldingResponse` also carries `id`, `conId`, `sector` (IB's since 2026-11-05, see below), `status`, and — derived via
`calculation.HoldingHistory`, see above — `firstBuyDate`, `lastSellDate`, `holdingDays` and `trades`
(a `List<TradeResponse>`, one entry per `trade` row), and `warnings` (a `List<model.HoldingWarning>`, see above);
the UI reads all of them. `HoldingWarning` and its enum go into the JSON as they are, with no `*Response` copy — the
same as `HoldingStatus` and `TradeSide` — so their names are JSON keys and values too.
`PortfolioResponse.closedPositions` (at the top level) is every holding's and then every manual position's closed
positions, each investor's apart, as `ClosedPositionResponse`s (`holdingId`, `symbol`, `currency`, `sector`, the
dates, `holdingDays`, `quantity` — the shares **sold** —, the average prices, `realizedPnl`, `realizedPnlPercent`,
`warning` — `null`, or the over-sell message, which the UI shows as it is in an orange row under the position —,
`commissions`, `source` (`ClosedPositionSource`: `TRADES` for a holding, `MANUAL`), `manualPositionId`, `note` (the
manual position's), `remainingQuantity`, `trades` — the period's own `TradeResponse`s, picked by `tradeIds` — and
`investorId`) — from open holdings as well as closed ones, since a holding still open today may have been sold in full
or in part before. The UI sums the realized P&L per currency — in all, per investor, and per position for its "By
investor" table (no endpoint does it). `PortfolioResponse.investors` (added after it) is one
`InvestorResponse` per investor, the account owner first: `id`, `name`, `accountOwner`, the `InvestorSummary` figures
and its derived ones, `realizedPnlByCurrency` (a `{"HKD": 950, "USD": 500}` map), `cashMovements`
(`CashMovementResponse`s) and `warnings` (`model.InvestorWarning`, as it is). `HoldingResponse.investorQuantities`
(`InvestorQuantityResponse(investorId, quantity, sharesValue, sharesCost, unrealizedPnl, unrealizedPnlPercent)` — one
`InvestorPart` each, only investors holding some of it; the four figures were appended in INVESTORS_TODO.md session 4)
and `TradeResponse.investorId` were appended for the split. `HoldingResponse.momentum` (appended last) is a
`MomentumResponse` — `Momentum`'s figures, its five checks, `percentBelowHigh`, `score` and `label` (`MomentumLabel` goes
into the JSON as it is), then (appended 2026-10-26) the year's figures, `monthsInMarket` and `percentBelowHigh52Weeks` —
or `null` while no closes are stored for the holding: `PortfolioReadService` loads the closes of
every holding and of SPY in one query, and hands each holding's, with SPY's, to `Momentum.of`. `HoldingResponse.analysis`
and `signal` (appended after it) are the latest analysis and the dot: a `StockAnalysisResponse` — `analyzedAt`, `model`,
`consensus` (`model.AnalystConsensus` as it is, chosen on this read against IB's latest price; `null` without any source),
`analystTrend` (the code's since decision 18, under the key that carried Claude's) with `raisedTargetCount` /
`loweredTargetCount` (appended), and the rest of `StockAnalysisResult` as it is (each action with its
`sourcePublishedDate`; `moveReason` and `newsEvents` appended at the end on 2026-10-27 — the drawers show the move reason
under the news, the events only on the Recommendations page, with their points) — or `null`
while the holding has never been analyzed, and the
`HoldingSignal`, `null` without an analysis; `PortfolioReadService` loads the latest analysis of every holding in one
query. Appended after them (MY_HOLDINGS_TODO.md, session 2, 2026-11-04/05), what a scanned stock has, shown only — none of
it changes the dot: `analystTarget` (the same `AnalystTargetResponse` as the Recommendations page's, from the holding's
stored Dow Jones targets against IB's latest price, counted back from the last sync's day — `account_state.as_of`),
`companyInfo` and `industry` (IB's `category`; `sector` is IB's `industry` — the market's names, decision 11). A
`CompanyInfoResponse(finnhubAnsweredAt, nextEarningsDate, reportsWithin14Days, companyFacts, riskSigns)` (session 3, the
same record a `HotStockResponse` has) is what Finnhub answered — its records as they are — with the risk signs and the
timing note worked out on this read, built once, by its copy constructor from a `RiskSignsCalculator` (which holds
Finnhub's answer and the closes; the calculator knows nothing of the response), the answer's time and the day the note
counts from; `riskSigns` is `null` when Finnhub gave none of the four figures (not checked, which `[]` would hide — an
ETF), and `companyInfo` itself `null` while Finnhub never answered for the holding (not in USD, no key, every call
failed). For a holding, `finnhubAnsweredAt` is the `stock` row's `fetched_at` and the note counts from the last sync's
day (`PortfolioReadService.companyInfoOf`). Two more queries: the stored targets and the `stock` rows of every holding. `PortfolioResponse.estimatedAnalysisCost` (appended last, at the top level) is what the Analyze buttons quote
before a paid run, in place of a figure written by hand: an `EstimatedAnalysisCostResponse(costUsdPerStock,
tavilyCreditsPerStock, basedOnAnalyses)` — the average over those same latest analyses (`StockAnalysisEntity.costUsd()` /
`tavilyCredits()`), so the older, cheaper runs of fewer searches don't count; `BigDecimal`s to 4 places — or `null`
before the first analysis. `asOf` is an ISO-8601 string. `portfolioboss.utils.Utils.finiteOrNull` turns IB's `NaN` / infinity into
`null` for both JSON and the database (`nanIfNull` is the reverse, used by `toIbHolding()`); without it
Jackson writes the *string* `"NaN"`, which breaks the UI's `number | null` types. The port is `server.port`
in `application.properties`; `ui/vite.config.ts` proxies `/api` to it.

`RecommendationsController` serves `GET /api/recommendations` through `RecommendationsReadService`
(`@Transactional(readOnly = true)`): 503 before the first scan, then 200 with `Cache-Control: no-store` and a
`RecommendationsResponse(scannedAt, candidateCount, hotStocks, tooHotStocks, correctingStocks, latestRecommendation)`
built from the latest scan — the closes of its stocks, of the open holdings and of SPY in one query; a stock with no
closes or no long upward trend is in no list but counted in `candidateCount`. A `HotStockResponse` is the candidate's row
(`candidateId`, `conId`, `symbol`, `companyName`, `exchange`, `industry`, `category`), `label`, its `MomentumResponse`
(the price is its `lastClose`), `percentAboveSma50`, `pulledBack`, `recovering`, `correlations` — the 3 highest with open
holdings, never with itself, as `CorrelationResponse(holdingId, symbol, coefficient, movesTogether)` — and
`heldByInvestorIds` (the `partsByInvestor` keys of the holding it is, if any); appended in session 3: `analysis` (a
`StockAnalysisResponse`, the stock's latest **by conId, from any scan** — decision 12, so a new scan, once a trading day,
doesn't hide what was paid for —, consensus against the last close; two queries: the analyses, then
`ScanCandidateRepository.findAllById` for which stock each was), `companyInfo` (the `CompanyInfoResponse` a holding has,
since MY_HOLDINGS_TODO.md session 3 in place of four flat keys: Finnhub's answer stored with that analysis, the analysis's
`analyzedAt` as its time, built from `RecommendationCandidate.riskSignsCalculator()` — the same calculator the pick
weighs —, the timing note from the latest scan's date, not the clock; `null` while never analyzed, or when Finnhub
didn't answer with the analysis) and `newsScore` (from the analysis's own date, as the pick weighed it; `null` never
analyzed); appended in session 4, so that the page shows the rule's verdict without holding a weight:
`weighedNewsEvents` (every event of the analysis with its points, `model.WeighedNewsEvent(event, points)` — 0 for
`NOT_IN_THE_LIST`) and `countedAnalystMoves` (`model.WeighedAnalystMove(action, points)`, only the moves that count),
both from `NewsScoreCalculator`'s `weighedNewsEvents()` / `countedAnalystMoves()`, which `score()` now sums, and empty
while never analyzed; `riskWeight` and `score` (`RecommendationCalculator.riskWeightOf` / `scoreOf`, `null` like
`companyInfo.riskSigns`) and `canBePicked` (`RecommendationCalculator.canBePicked`, public since; false without Finnhub's figures) —
asked of a `RecommendationCalculator` of that one stock, since none of it depends on the rest of a short list; appended
in session 4ב (decision 14): `analystTarget` (an `AnalystTargetResponse(medianTarget, upsidePercent, hasEnoughUpside,
firmCount, latestTargetOfEachFirm)` — the last a list of `ib.AnalystPriceTarget` as it is, `publishedAt` a local
date-time —, known for every stock before any analysis), `inShortList` (the same short list the full run takes) and
`reasonsNotPickable` (`model.NotPickableReason`s, in their order; `canBePicked` is "none").
`latestRecommendation` is a `LatestRecommendationResponse(recommendedAt, scannedAt, picks)` — its own scan's
date, and `PickedStockResponse(candidateId, conId, symbol)`s in order, found in today's lists by `conId` — or `null`
before the first full run. No cost estimate here: the page quotes `/api/portfolio`'s `estimatedAnalysisCost` × the
stocks marked `inShortList`, the same work per stock. `ui/src/types/recommendations.ts` mirrors them. `ScanController` /
`ScanService` serve `POST /api/scans` (JSON only, `{}`): `TwsReader.readScan()`, then `PortfolioSyncService.syncScan`, and
200 with a `ScanRunResponse(scannedAt, candidateCount)`; `TwsBusyException` is 409, `TwsUnavailableException` 503, its
user's message as the `detail` — `ApiErrorHandler`'s, since 2026-11-11. `ScanService` is not `@Transactional`: no
connection waits on TWS.

Sync now (MY_HOLDINGS_TODO.md session 5א, decision 14, 2026-11-11; browser-checked by him against TWS the same day):
`SyncController` / `api.SyncService` serve `POST /api/syncs` (JSON only, `{}` — `SyncRequest`): `syncNow()` —
`isScanDue()`, `TwsReader.readPortfolioUnlessBusy`, then `storeWithFinnhubData` (Finnhub, `PortfolioSyncService.sync`,
and `PortfolioSyncedEvent`) — and 200 with a `SyncRunResponse(syncedAt, holdingCount)`; 409 / 503 as for a scan. The
startup runs the same flow through `syncAtStartup()` (it waits for a reading already running, and TWS off is a console
line, not an error). Not `@Transactional`, like `ScanService`. Its time was not measured (the console has no times).

The alerts (MY_HOLDINGS_TODO.md session 5ב, decisions 14–17, 2026-11-11) are a package of their own, `portfolioboss.alerts`
(console `[alerts]` / `[alerts error]`; the user's ask — "the files spread over the packages is really bad"): its data,
rules, tables, service, controller and its `request/` and `response/`. It sits **on top**: it uses `api` (only
`RecommendationsReadService.shortListOfLatestScan`, for the short-list alert), `db`, `calculation`, `ib`, `stocks` and
`model`, and nothing uses it — the sync tells it through a Spring application event (`api.PortfolioSyncedEvent`, published
by `SyncService`; `alerts.AlertsAfterSync` listens with `@EventListener`, a class of its own because a call from inside
`AlertService` to its own `@Transactional` method would skip the transaction), and the bell has its own `GET /api/alerts`
(`/api/portfolio` doesn't carry the alerts). `AlertType` holds the nine kinds — `MOMENTUM_TURNED_WEAK`,
`CLOSED_BELOW_SMA50`, `CLOSED_BELOW_SMA200` (decision 6's א, split in three), `EARNINGS_SOON` (default 7 days),
`ANALYST_CUT_TARGET` (7 days), `FAR_BELOW_AVERAGE_COST` (15%), `ABOVE_WEIGHT_LIMIT` (15%), `TRADES_DONT_MATCH_IB`,
`ENTERED_SHORT_LIST` — each with its default number and `ThresholdUnit` (`DAYS` 1–60 whole, `PERCENT` 1–100);
`AlertSettings` is the saved `alert_setting` rows, a type never saved on and with its default. A check
(`AlertService.checkAlerts()`, after every sync and after the settings are saved — not after Scan again) builds, for every
holding of the account, a `HoldingAlertsCalculator(holding, status, momentum, finnhubData, analystPriceTargets, warnings,
percentOfAccount, settings, today)` — today is the last sync's day — whose `conditionsTrueNow()` are `AlertCondition(key,
symbol, message)`s: the momentum `WEAK`, the close not above the 50- / 200-day average, the next report 0–N days away
(subject: its date), every target cut — the new one below the one it replaced — in the last N days, that day included
(subject: the firm and the headline's time; a rolling window, so a week without a sync misses a cut that left it), the
unrealized P&L −N% or worse (exactly N counts), more than N% of the whole account (`PortfolioWeightsCalculator`; exactly
N doesn't), and the holding's warning triangle (subject: the warning's type) — a closed holding only that last one —; and
for every stock of the latest scan's short list an `ENTERED_SHORT_LIST` condition (built in `AlertService`: the rule is the
short list itself). Then `AlertChangesCalculator(openAlertKeys, conditionsTrueNow)`: a condition with no open alert of its
`AlertKey(type, conId, subject)` becomes a new `alert` row, an open alert no longer true gets its `ended_at`; the open alerts
are the previous check's state, so the first check alerts everything already true once (24 alerts on the real data,
2026-11-11). A type turned off is left out of both sides — not checked, not shown, its open alerts left open. Messages are
English, written when the condition became true (`Locale.ROOT` numbers: "Down 17.3% from the average cost of 182.40"), in
the screens' words since UI_POLISH_TODO.md's stage ד (2026-11-25): "Momentum turned weak: 1 of 5", "Reports on
30/11/2026, in 6 days" (`HoldingAlertsCalculator.DATE_AS_THE_SCREENS_WRITE_IT`; the subject stays the ISO date, an open
alert's key), "Entered the short list: hot, target 31.8% above the price" — an alert stored earlier keeps its words.
`AlertController`: `GET /api/alerts` (`no-store`) → `AlertsResponse(alerts, settings)` — `AlertResponse(id, alertType,
conId, symbol, holdingId, message, createdAt, seenAt, group)`, the types turned on only, newest first, ended ones too
(history), `holdingId` from the latest account's holdings by `con_id` (a closed holding's too), `null` for a stock not held,
`group` the bell panel's (`model.alerts.AlertGroup`: `YOUR_HOLDINGS`, `SHORT_LIST`) from
`calculation.alerts.AlertGroupCalculator(alertType, stockIsHeldNow).group()` — the user's rule: a stock held now is first
of all a holding, so only `ENTERED_SHORT_LIST` of a stock with no open holding is `SHORT_LIST` —,
`AlertSettingResponse(alertType,
enabled, threshold, defaultThreshold)` per type; `POST /api/alerts/seen` (`{}`, 204: one `UPDATE` of the shown types'
unseen rows); `PUT /api/alerts/settings` (204, `@Valid AlertSettingsRequest` — every type exactly once,
`isEveryTypeGivenOnce`, and each `AlertSettingRequest(alertType, enabled, threshold)` with `isThresholdRightForItsType`, both
`@AssertTrue` —; saves, then checks at once). `ui/src/types/alerts.ts` mirrors them. Things that are easy to break:
- The partial unique index refuses a second open alert of one key: a key found twice in one check (one firm's two
  headlines at the same minute) is made once (`conditionsToAlert` keeps the first).
- `@Id` on an enum (`AlertSettingEntity.alertType`, `@Enumerated(STRING)`) works in this Hibernate; `save` of a type
  already saved replaces its row (a merge).
- The bulk `markAsSeen` doesn't touch the entities already loaded in the same transaction — none are written back after
  it in a check, since a check changes only `ended_at` and adds rows.
- `ui/src/components/alerts/alertTexts.ts` copies `ThresholdUnit`'s ranges by hand: change both together.

The capital gains tax (CAPITAL_GAINS_TAX_TODO.md) is a package of its own, `portfolioboss.tax` (console `[tax]` /
`[tax error]`), under `api` (see Conventions). Session 1 (2026-11-15) downloads **IB's Flex reports** and stores them —
the account's every execution and cash transaction as IB reported it, for the tax the later sessions compute; nothing
reads them yet, and nothing here touches `trade` or `holding`. The query is the user's, on IB's site (the plan's 1.1):
XML, Trades as executions (with `IBOrderID` and `Date/Time`), Cash Transactions, no Statement of Funds (the Cash
Transfers to and from Phoenix appear only there, decision 8, and the tax doesn't need them). The pieces:
- `FlexClient` (a `@Component`, `RestClient` like `FinnhubClient`, both keys — `hasKeys()`, the startup's `[tax] Flex off`
  line): `downloadReport(requestedPeriod)` is `SendRequest` (`t`, `q`, `fd` / `td` — the period in place of the query's
  own, at most 365 days both ends included —, `v=3`) for a reference code, then `GetStatement` by it after a 10-second
  wait, again while IB answers "Please try again shortly" (`FlexReportRequestStatus.saysTryAgainShortly()`: 1001,
  1004–1009, 1018, 1019, 1021), 12 tries at most; any other status fails at once with IB's code and words. Every failure is
  a `exceptions.FlexReportUnavailableException` whose message never holds the address (`callFlexWebService`,
  `reasonWithoutTheAddress`).
- `FlexReportReader` (Java's own DOM reader — no Jackson XML, which would have Spring read and write XML on every endpoint
  —, a `DOCTYPE` refused): `isReport` (`<FlexQueryResponse>`, against `<FlexStatementResponse>`, the service's status),
  `readReport` → `FlexReport(period, trades, cashTransactions, corporateActions)` of `FlexReportPeriod`,
  `FlexReportTrade`, `FlexReportCashTransaction` and (session 3.0) `FlexReportCorporateAction` — IB's fields under readable
  names, IB's signs; of the corporate actions only the `levelOfDetail="DETAIL"` rows —, `readRequestStatus` →
  `FlexReportRequestStatus(status, referenceCode, errorCode, errorMessage)`. A field the tax needs that is missing or
  unreadable fails the whole report naming the field and the row (`<Trade tradeID="…"> has no dateTime: is it ticked in
  the Flex query?`); an empty attribute (`conid=""` on a fee) is `null`. A trade's `fifoPnlRealized` is optional: only
  the check against IB's FIFO needs it.
- `FlexDownloadSchedule(lastStoredDate, earliestTypedTradeDate, today).periodsDue()`: up to the last weekday before today
  (`lastDayToRead`; IB prepares a report once a day), in periods of at most 365 days, oldest first, each starting and
  ending on a weekday (`firstWeekdayOnOrAfter`, `latestWeekdayOnOrBefore`) — never stored: from the first of January of
  the earliest typed trade's year (`db.TradeRepository.findEarliestTradeDate()`, the user's choice: automatic, no
  button); without a typed trade, the last 365 days; stored: from a week before the last stored day; stored up to the
  last weekday: nothing.
- `FlexReportStorageService` (`@Transactional`: `storeReport(report, downloadedAt)`, `lastStoredDate()`) and
  `FlexDownloadService.downloadWhatIsMissing(today)` (not transactional; a `ReentrantLock` lets one download through,
  the other prints `skipped`; the periods one after another, a failure stopping the rest), called by
  `api.SyncService.downloadMissingFlexReports` after the store and the alerts of every sync that reached TWS — not at a
  startup with TWS off (the user's OK).
Things that are easy to break:
- **IB refuses a period that starts or ends on a Saturday or a Sunday** with 1003 "Statement is not available" — IB
  documents none of it. Found in the first live runs (2026-11-15: the four periods ending on a Saturday were refused, the
  five ending on a Friday went through) and confirmed by the user by hand on IB's site. Two wrong readings came first and
  were dropped: a pause between requests (60 s didn't help) and one report per sync (a period ending on a Saturday was
  refused as a sync's first request too). The market's holidays are not known: a period starting or ending on one may be
  refused as well — a 1003 with a weekday date would say so. Elsewhere 1003 is said to mean a period before the account
  opened.
- The token is in the address, and Spring's exceptions print the address: never let one of them reach a message or the
  console from `FlexClient` (`FlexClientTest` checks both failure kinds).
- A field unticked in the query fails every download from then on, with its name: tick it again, or make it optional in
  `FlexReportReader` — after deciding the tax can do without it.
- A period counts both its ends: 365 days is `fromDate.plusDays(364)`. A calendar leap year is 366, over IB's limit.
- A row IB sends twice in one report is stored once (two new rows with one IB id would break the `UNIQUE`); a trade IB
  cancels after it was stored stays stored — Flex doesn't mark a row as deleted (rare; a confirmed day keeps Phoenix's
  amount, and the calculated one beside it would show the gap).
- To download the history again (a field added to the query): delete the `flex_report` rows; the stored rows are then
  replaced by IB's id, not doubled.

Session 2 (2026-11-15) adds to `tax` only which days the tax needs a rate of — the rates themselves are the
`exchangerates` package's (below):
- `SettlementDayRatesService.downloadMissingDollarRates(today)` (not transactional), called by
  `api.SyncService.downloadMissingDollarRates` right after the Flex reports — they bring the account's settlement days —
  of every sync that reached TWS: the settlement days in dollars of the Flex trades (buys too: a lot's cost in shekels is
  at its buy's rate, decision 11) and cash transactions (`FlexTradeRepository` / `FlexCashTransactionRepository
  .findSettleDatesInCurrency`; a fee is a tax shield), and of the typed trades (`db.TradeRepository
  .findTradeDatesInCurrency`, a holding's or a manual position's), handed to `exchangerates.ExchangeRateDownloadService`.
  Only the dollar: the Bank of Israel has no Hong Kong dollar rate in any series (checked 2026-11-15).
- `UsSettlementDayEstimate(tradeDate).estimatedSettlementDay()`: a typed trade has no settlement day, so it is estimated —
  one business day after the trade since 28.05.2024, two before, weekends skipped, US holidays unknown (it can be a day
  early). Session 3 takes the real day from the trade's twin in Flex (same contract, same day —
  `TypedTradeSettlementDays`) and this estimate only for a typed trade Flex doesn't have; the download covers both, since
  a Flex day is already among the Flex trades'.

Session 3 (2026-11-15) is the tax itself, worked out on every call from what is stored — nothing of it is stored —,
printed after every sync (`api.SyncService.printCapitalGainsTax`, after the rates) and, since session 4, served by
`GET /api/taxes` (below). Pure records, no Spring and no database, in the order the calculation runs (the
names are the user's — `TaxCalculationTrade` for `TradeForTax`, `SharesSoldFromOneBuy` for a "lot", which he didn't
understand; "lot" appears nowhere in the code):
- `TaxCalculationTrade(tradeId, symbol, currency, tradeDate, settlementDay, side, quantity, price, commission)`: a buy or
  a sale in one shape for both sources, the quantity and commission positive. `TradesOfOneStock(conId, tradesInOrder,
  notComputedReasonOfEverySale)`: each source groups and orders its own trades.
- `FlexTradesAfterTheSplits(flexTrades, stockSplits, corporateActions).tradesOfEveryStock()` (decision 15;
  `SplitAdjustedFlexTrades` until 2026-11-22, when it began following the **stored** splits — STOCK_SPLITS_TODO.md, below):
  the account's stock executions (conversions, `CASH`, left out), by contract, ordered by IB's time and then IB's trade id
  as a number; at each stored split the trades of the account's open position — since it last held none of the stock —
  translated, the quantity times the split and the price divided by it, under the contract of after it; a position sold
  in full before it stays as IB wrote it, as on the broker's statement of its day. In memory only. A corporate action not
  stored as a split makes every sale of its contracts `CORPORATE_ACTION_NOT_SUPPORTED`. A commission in another currency
  than the trade's makes the stock's sales `COMMISSION_IN_ANOTHER_CURRENCY`. `sharesHeldJustBefore(conId, moment)` is
  what the account's trades before a moment add up to, after the splits given — the check of
  `SplitsInFlexCorporateActions`.
- `TypedTradeSettlementDays(accountStocks)`: a typed trade's settlement day is its Flex twin's — a holding's by contract
  and day, a manual position's by symbol and day, after the splits —, else `UsSettlementDayEstimate`.
- `FifoSalesCalculator(tradesInOrder).sales()` (decision 3) → `FifoSale(sale, sharesSoldFromEachBuy,
  quantitySoldBeyondTheBuys)` of `SharesSoldFromOneBuy(buy, quantity, cost, proceeds)`: each sale takes from the earliest
  buys still held; a buy's commission goes into its shares' cost and a sale's off their proceeds, by quantity. The closed
  positions stay at average cost (`calculation.HoldingHistory`, untouched).
- `TaxableGainOfSharesFromOneBuy(sharesSold, buyRate, saleRate)` (decision 11): `gainInDollarsAtTheSaleRate()`,
  `gainInShekels()`, and `taxableGain()` — the one closer to zero, 0 when their signs differ. `SaleTaxableGain(fifoSale,
  notComputedReasonOfEverySaleOfTheStock, exchangeRatesByCurrency)`: the sum over the buys, each buy and the sale at their
  own settlement day's rate (`ExchangeRates.rateForSettlementDay`), or a `SaleNotComputedReason`, the first that holds:
  `CORPORATE_ACTION_NOT_SUPPORTED`, `COMMISSION_IN_ANOTHER_CURRENCY`, `SOLD_MORE_THAN_BOUGHT`, `NO_RATE_FOR_THE_CURRENCY`
  (HKD), `RATE_NOT_PUBLISHED_YET`. A sale not computed is left out of the balance, never guessed.
- `SettlementDayEntry(settlementDay, sales, expenses, exchangeRatesByCurrency)` (decision 4): the day's computed sales and
  its `TaxDeductibleExpense`s — IB's Other Fees, with IB's sign, at the day's rate (`rateOf(expense)`,
  `inShekels(expense)`, empty while the rate isn't known) —; `taxAmount()` is 25% (decision 2) in agorot, half up:
  positive "tax to pay", negative a "tax shield".
- `TaxYearCalculator(year, entriesInDateOrder, lastSyncDay)` (decision 5): `taxableGain()`, `sales()` and `balance()` — a
  `TaxYearBalance(taxAmountBySettlementDay, lastSyncDay)` of each entry's tax amount: `monthlySettlements()` →
  `MonthlySettlement(month, entriesOfTheMonth, settlementAmount, shieldLeft, settled)` — owed so far is max(0, the year's
  amounts so far), and the settlement is that less what was paid before (positive paid, negative refunded) —,
  `taxOfTheYearSoFar()`, `shieldLeft()`. The balance is a record of its own (session 4) so that the same rule runs on the
  amounts as recorded at Phoenix. The year is the settlement day's (decision 16, `CapitalGainsTaxCalculator.taxYearOf
  (settlementDay)`).
- `CapitalGainsTaxCalculator(tradesOfEveryStock, expenses, exchangeRatesByCurrency, lastSyncDay)`: `sales()`,
  `settlementDayEntries()`, `taxYears()` — the same calculation for the account and for an investor (without expenses).
- `OurFifoAgainstIbsFifo(accountSales, ibsFifoGainByIbTradeId)`: our FIFO gain of each Flex sale against IB's
  `fifoPnlRealized`; more than a cent apart is listed (`salesWhoseGainDiffersFromIbs`).
- `CapitalGainsTaxService` (`@Transactional(readOnly = true)`): `capitalGainsTaxOfTheAccountAndInvestors()` →
  `CapitalGainsTaxOfTheAccountAndInvestors(accountTax, otherInvestorsTax, ourFifoAgainstIbsFifo)` with an
  `InvestorCapitalGainsTax(investorId, investorName, tax)` per investor other than the account owner, from their typed
  trades alone — in shekels, never converted, their cash untouched (decision 6); the owner is the account's residual, not
  computed alone. "Today" is the last sync's day; only the dollar has rates (`SettlementDayRatesService.US_DOLLAR`).
  `printCapitalGainsTax()` prints `CapitalGainsTaxConsoleLines`; a failure is `SyncService`'s `[tax error]`, never the
  sync's.
Things that are easy to break:
- **Flex's Trades section shows no corporate action**: a reverse split into a new contract leaves a stock's buys and sales
  in two contracts, and without the Corporate Actions section its sales are `SOLD_MORE_THAN_BOUGHT` (the first live run,
  before the history was downloaded again). A query without that section, or with one of its fields unticked, brings that
  back.
- IB lowers a stock's cost by a **return of capital**; our FIFO doesn't. That is the one sale of the live check whose gain
  differs from IB's (in HKD, its tax not computed anyway). Phoenix taxed the return of capital as income, so our cost is
  Phoenix's — no change; the dividends are session 7.
- Phoenix's own fees aren't in IB's reports: the tax it withholds can be lower than 25% of the same taxable gain (the 867
  of 2024) — a known gap; a day whose Phoenix amount differs is confirmed with Phoenix's amount.
- The half-up rounding of each day's entry and "a refund is never more than what was paid" are assumptions; the days the
  user confirms with another amount than the calculated one are where they would show.

Session 4 (2026-11-16) is the confirmations of decision 7 and the page's endpoints, in `tax` (the user's rules: a confirmed
amount is **final**, there is **no "matches" / "differs"**, and only a day with a calculated entry can be confirmed):
- `TaxConfirmation(id, settlementDay, taxAmount, confirmedAt, note)` — a `tax_confirmation` row (`TaxConfirmationEntity`,
  `TaxConfirmationRepository`) as the calculation reads it; public, so that `TaxConfirmationResponse` copies it.
- `SettlementDayWithItsConfirmation(entry, confirmation)`: `isConfirmed()` and `recordedTaxAmount()` — the confirmed
  amount, else the calculated one. `TaxYearWithItsConfirmations(taxYear, confirmationsOfTheYear)`: `settlementDays()` —
  every calculated day with its confirmation, and a confirmation whose day the calculation no longer has (IB changed a
  settlement day in a report downloaded again) as a day of an empty entry, so it never disappears without a word —, and
  `recordedBalance()`, the `TaxYearBalance` of the recorded amounts. `AccountTaxWithItsConfirmations(accountTax,
  confirmations).taxYears()`: each confirmation in its settlement day's year, a year with only a confirmation included.
- `TaxesReadService.taxes()` (`@Transactional(readOnly = true)`, empty before the first sync): `CapitalGainsTaxService`'s
  result and the stored confirmations in one transaction, built into `tax/response/` records in private methods (the tax's
  records are internal to the package): `TaxesResponse(lastSyncDay, accountTaxYears, otherInvestorsTax)` of
  `TaxYearResponse(year, taxableGain, taxOfTheYearSoFar, shieldLeft, settlementDays, monthlySettlements)` — the account's
  tax, shield and months as recorded, an investor's (`InvestorTaxResponse`) as calculated —, `SettlementDayResponse
  (settlementDay, sales, expenses, taxableGainOfTheDay, computedTaxAmount, confirmation)` — `confirmation` `null` while
  the day waits and always on an investor's day —, `SaleResponse` (`notComputedReason`, `SaleNotComputedReason` as it is,
  public since), `SharesSoldFromOneBuyResponse` (both ways of decision 11 per buy; the rates and shekels `null` on a sale
  not computed, its buys still listed), `TaxDeductibleExpenseResponse` (the rate and the shekels, `null` while unknown),
  `MonthlySettlementResponse` (`month` a `YearMonth`, `"2026-06"` in the JSON) and `TaxConfirmationResponse`.
- `TaxConfirmationService` (`@Transactional`): `confirm(TaxConfirmationsRequest)` — every line together or none: 400 when
  a day has no calculated entry (it asks `CapitalGainsTaxService` for the account's days), 409 when one is confirmed
  already —, `correct(id, TaxConfirmationCorrectionRequest)` (the amount and the note; `confirmed_at` becomes now) and
  `cancel(id)` (the day waits again); 404 for an unknown id; a note trimmed, blank `null`.
- `TaxController`: `GET /api/taxes` (`no-store`, 503 before the first sync), `POST /api/taxes/confirmations` (201 with
  each new confirmation), `PUT` and `DELETE /api/taxes/confirmations/{confirmationId}` (204), JSON only. The bodies:
  `TaxConfirmationsRequest(confirmations)` — `@NotEmpty`, every day once (`isEveryDayGivenOnce`, `@AssertTrue`) — of
  `TaxConfirmationRequest(settlementDay, taxAmount, note)` — `@PastOrPresent`, `@Digits(integer = 18, fraction = 2)` with
  its own message, a note of 500 at most — and `TaxConfirmationCorrectionRequest(taxAmount, note)`. `ui/src/types/taxes.ts`
  mirrors them since session 5.
Things that are easy to break:
- The console lines stay the calculation's own (`TaxYearCalculator.balance()`), confirmations not counted: they are the
  check of the rules, and the page is where the recorded amounts are.
- A confirmed day's amount wins over a calculation that changed after it — on purpose (the user's rule). A code fix that
  changes old days' amounts doesn't touch the confirmed ones.
- Two confirmations of one day at the same moment would pass the 409 check and break the `UNIQUE` (a 500): one user, so
  not guarded further.

Session 5 (2026-11-16/17, browser-checked by the user) is the Taxes page, with three additions to `/api/taxes` — keys
only added, so that the page holds no tax rule:
- `SettlementDayStatus` — each day's state, decided in Java. `SettlementDayEntry.status()` checks in this order:
  `SOLD_MORE_THAN_BOUGHT` (a sale beyond its buys), `CANNOT_BE_CALCULATED` (a sale in a currency without rates, a
  corporate action not supported, a commission in another currency — `SALE_REASONS_THAT_CANNOT_BE_CALCULATED`: the
  broker's amount has to be typed in), `WAITING_FOR_A_RATE` (a sale's or a fee's rate not published yet), then
  `CALCULATED_TO_ZERO` or `CALCULATED`. `SettlementDayWithItsConfirmation.status()` is `CONFIRMED` once confirmed, whatever
  the entry; an investor's day takes its entry's. Served as `SettlementDayResponse.status`.
- `FifoSale.costAtTheAverageCost` — what the shares sold cost at the average cost, as the Closed page counts it:
  `FifoSalesCalculator`'s private `SharesHeldAtTheAverageCost` walks the same trades beside the FIFO buys, the average
  carried from one sale to the next and a sale of every share taking all that is left (recomputing it from the buys FIFO
  still holds was the first plan, and wrong from the second sale on). `fifoCostDiffersFromTheAverageCost()` — more than a
  cent — is `SaleResponse.fifoCostDiffersFromAverageCost`, the page's "first in, first out" note. The closed positions stay
  `calculation.HoldingHistory`'s.
- `TaxesResponse.flexConnected` (`FlexClient.hasKeys()`) and `flexReportsStoredUpTo` (`FlexReportStorageService
  .lastStoredDate()`, `null` before the first download): which setup card the page shows, if any.

The page (`ui/src/pages/TaxesPage.tsx`, `/taxes`, the seventh navigation item; its pieces in `ui/src/components/taxes/`;
`ui/src/types/taxes.ts`; `useTaxes(portfolioSyncedAt)` — 503 is "nothing synced yet", and it loads again on a new `asOf`):
- The year (a `SelectInput`, the last sync's year first) and `InvestorTabs` (`taxYears.tabsOf`: Portfolio, then each
  investor besides the account owner — the owner's tab waits for the Flex-trades session); a new tab or year closes every
  opened row. `TaxSetupCard` stands in for everything while IB's report isn't connected or nothing is stored (no button:
  the history comes with the next sync).
- `TaxYearCard`: the tax of the year so far, Settled ("Paid … · Refunded …"), Not settled yet (the current month's
  settlement or the shield carried) and, on Portfolio, Checking off ("3 of 5 days confirmed", "1 to type in"); on the last
  sync's year, a strip under it for a shield the broker won't refund on 31 December, with the days left — information in
  the accent, never a warning.
- `SettlementDaysTable`: one table per year — Settles · Sales · Taxable gain · Entry · Check (Portfolio only) —, the months
  newest first (`MonthHeadingRow`: "June 2026 · tax ₪1,279.40 → paid", then "2 days to confirm" with
  `ConfirmWholeMonthButton`, or "Checked off"). A day (`SettlementDayRows`) opens into `DaySales` — a line per stock
  (`stockSalesOfTheDay`: a sale IB reported in parts is one line, "3 parts"), the FIFO note's "?" when the API flags it, and
  IB's fees —, a stock's line into `SharesSoldFromEachBuyTable` (Bought · Shares · Cost · Sold for · In dollars · In shekels
  · Counts). `TaxCheckCell` follows `status`: Confirm and the pencil (`BrokerRecordedAmountForm`: the amount in shekels,
  Tax / Shield only when a sale wasn't calculated, a note, "Remove the confirmation"), the ✓ with its hint (when, the note,
  and "PortfolioBoss calculated ₪…" when another amount was confirmed — the user's pick), the clock, the pencil outlined,
  or nothing. Confirm and Confirm the whole month send the calculated amounts (`confirmTaxDays`), the pencil
  `confirmTaxDays` / `correctTaxConfirmation` / `cancelTaxConfirmation`, each then a reload. The words are `taxTexts.ts`'s.
- `components/HelpOnHover.tsx` was the page's "?" (and the ✓ / clock), and is the whole app's since UI_POLISH_TODO.md's
  stage ה (below: the ✓, the clock, ₪0.00 and a day's or a buy's date are `Hint`s on the item since, the "?" only by the
  longer explanations): a Radix **Tooltip**, open while the pointer or the keyboard is on it, closed by Esc or a click. It
  was a popover first, and the user's browser check found it reopening by itself: a popover gives the focus back to its
  button when it closes, and the button opened on focus. A tooltip never moves the focus.
- `components/InvestorTabs.tsx` replaced `graphs/WeightsTabs.tsx`: one tab row for the weights, the Weight tab, If I buy,
  the Averaging calculator and Taxes, each naming it (`ariaLabel`). `lib/format.ts` gained `formatShekels`,
  `formatSignedShekels`, `formatDollars`, `formatAmountInItsCurrency` and `formatExchangeRate` (3 decimals): the first page
  with two currencies side by side, every amount carries its sign.
Things that are easy to break:
- React passes a click inside a portal (a "?"'s bubble, the pencil's popover, a confirm) up to the rows around it in the
  component tree: `HelpOnHover`, `Hint` and `TaxCheckCell` stop it, or the day's row would open or close (a month's
  heading row opens nothing, so its button needs no such guard).
- `BrokerRecordedAmountForm`'s two decimals copy `TaxConfirmationRequest`'s `@Digits(fraction = 2)`, and its 500 the
  note's limit: change both together.
- `@radix-ui/react-tooltip` is pinned to 1.2.16, which shares its inner Radix parts with the installed dialog and
  popover; a newer one brings a second copy of the part that knows which open layer is on top (Esc in a drawer).

The stock splits (STOCK_SPLITS_TODO.md, session 1, 2026-11-22; the names are the user's — his word "translated",
"מתורגמת", for a trade counted in the shares of after a split; "position period" and "split-adjusted" were not clear to
him): a split is stored once, and the trades typed in stay as typed and are translated on every read. The pieces:
- `calculation.splits.StockSplit(ibActionId, symbol, conIdBeforeTheSplit, conIdAfterTheSplit, splitTime,
  sharesBeforeTheSplit, sharesAfterTheSplit)`: `isBeforeTheSplit(tradeDay)` — a trade of the split's day was before it,
  IB books it at 20:25 after the close —, `isBeforeTheSplit(tradeTime)` for an execution, `quantityAfterTheSplit` /
  `priceAfterTheSplit` (16 digits), `keepsTheSameContract()`, `ratioInIbsWords()` ("4 for 1"). `StoredStockSplits(splits)`
  (in time order): `splitsOfTheHolding(conId)` — only those that kept its contract —, `isStored(ibActionId)`.
- `calculation.tax.SplitsInFlexCorporateActions(corporateActions, flexTrades)`: `splitsUnderstood()` and
  `splitsNotUnderstood()` (`SplitNotUnderstood`, `WhySplitNotUnderstood`). Only `FS` / `RS` actions, of two kinds — one
  row in the same contract (the shares it adds, or takes away), or two rows at one time into a new contract —, without
  money, and only when IB's words ("SPLIT n FOR m", every row the same) and the shares agree: the shares held just before
  (`FlexTradesAfterTheSplits.sharesHeldJustBefore`, after the splits understood before it) times the split are the shares
  after. In `calculation/tax`, not `calculation/splits`: it needs the tax's Flex trades, and `splits` can't use `tax`.
- `sync.StockSplitDetectionService.storeNewSplits()` (`@Transactional`): every split understood and not stored yet is
  stored and printed; each not understood is printed on every sync. `SyncService` runs it after the Flex reports and
  before the rates and the tax — and at a startup that couldn't read TWS too, since it reads only what is stored (the
  first run after V20 stored the two splits already in the reports); a failure is `[db error]`, never the sync's.
- `HoldingHistory.openPositionTrades`: the trades of the current position period while it is open — the trades a split
  counts. `calculation.splits.TradesAfterTheSplits(tradesAsTyped, splitsOfTheHolding)`: `allTrades()` — for each split,
  each investor's `HoldingHistory.of(trades of the split's day or before).openPositionTrades()` translated, the commission
  as it was — and `translatedTrades()`, only those. A position sold in full before the split stays as it happened (IB
  reports no split of a stock no longer held); a part of the open position sold before it is translated with it, or the
  period wouldn't add up to IB's quantity.
- `HoldingEntity.tradesAfterTheSplits(stockSplits)`, `tradeHistory(stockSplits)` and `toPositionTrades(stockSplits)` take
  the splits as a required parameter, so that no reader of a holding's trades forgets them: `PortfolioReadService` (the
  holdings, the closed positions, the investors' cards), `AlertService` (`TRADES_DONT_MATCH_IB`),
  `RecommendationsReadService` (who holds a scanned stock — worked out once per read) and `CapitalGainsTaxService` (an
  investor's typed trades). Every reader reads the splits once (`StockSplitRepository.storedStockSplits()`). A manual
  position is never translated: no contract, sold before the first sync.
- `HoldingResponse.stockSplits` (`StockSplitResponse(splitDay, sharesBeforeTheSplit, sharesAfterTheSplit)`) and
  `HoldingResponse.translatedTrades` (`TranslatedTradeResponse(tradeId, quantity, price)`); `ui/src/types/portfolio.ts`
  mirrors them (`StockSplit`, `TranslatedTrade`).
- The UI (session 2, 2026-11-23), in `ui/src/components/stockSplits/`: `StockSplitRow` (the grey line in a holding's
  trades — "Split 4 for 1 · from IB's report", its meaning on hover from `stockSplitTexts.whatTheSplitMeans` —, after the
  trades of its own day), `TradeBeforeTheSplitRow` (the line under a translated trade: "= 10 at 400.00 before the split"),
  `stockSplitTexts.ts` (the words) and `sharesAsTheyWereThen.ts` (`isBeforeTheSplit`, `splitsAfterTheTradeDate`,
  `quantityAsItWasThen`, `priceAsItWasThen` — the one place the UI works out a split itself). In `TradesPanel.tsx`
  `tradesAndSplitsInDateOrder` places the splits among the trades, and a translated trade's row shows
  `quantityAndPriceAfterTheSplits` — the numbers IB's quantity and every figure above the table count —, the delete
  confirm both. The Closed page's read-only trades of a holding show the translation too (`ClosedPositionsPage` gets
  `holdings` for it), but no split line: a period sold in full before a split wasn't touched by it. `TradeModal`:
  `SplitsAfterTheDateNote` under the fields (`InfoBand`'s `placement="below"`) for a date on or before a split, and the
  backfill's prefill (`prefilledQuantityAndPrice`) follows the date (`updateTradeDate`) while the quantity and price are
  still what it filled in.
Things that are easy to break:
- **`TradeResponse` stays what was typed.** The edit form shows and sends only it; a translated number sent back would be
  stored, and the next read would translate it again. The translation is beside it, on the holding, by `tradeId` — not in
  `TradeResponse`, since a write endpoint answers with one trade and a trade's translation depends on the others. So the
  pencil of a row showing 40 at 100.00 opens the form with the 10 at 400.00 typed, and the note under the fields says why.
- The trade form's prefill of a holding without trades (IB's quantity and average cost) is in the shares of after the
  splits: a buy dated before a split typed from it would count twice, so `prefilledQuantityAndPrice` turns it into the
  date's shares. Never exercised in the browser — no real holding has a split and no trades.
- `sharesAsTheyWereThen.isBeforeTheSplit` copies `StockSplit.isBeforeTheSplit(tradeDay)` (the split's own day counts as
  before it): change both together.
- The alerts are checked on `PortfolioSyncedEvent`, before the Flex reports: on the sync that brings a split,
  `TRADES_DONT_MATCH_IB` may open once and end on the next sync.
- A reverse split that moves an **open holding** to a new contract isn't followed for the holdings: the sync closes the old
  holding and opens a new one without trades (out of the plan's scope); the tax follows it.

The exchange rates are a package of their own, `portfolioboss.exchangerates` (console `[exchangerates]` /
`[exchangerates error]`; the user's ask, 2026-11-15): a source of data, like `stocks` — it downloads, stores and answers
"which rate does this day take", and knows nothing of trades or the tax. The pieces:
- `BankOfIsraelClient` (a `@Component`, `RestClient`, no key): `exchangeRates(currency, period)` — the SDMX service's
  `EXR` series `RER_{currency}_ILS`, `startperiod` / `endperiod`, `format=csv` —, read by `BankOfIsraelRatesReader` into
  `BankOfIsraelRate(rateDate, rate)`s. A failure is an `exceptions.ExchangeRatesUnavailableException` with a short message
  (never the HTML page the Bank of Israel answers an error with).
- `BankOfIsraelRatesReader`: the columns found by their names (`TIME_PERIOD`, `OBS_VALUE`), not their places; a line
  with another number of fields than the header, an unreadable day or rate, a missing column or an empty answer fails the
  whole answer, naming the line.
- `ExchangeRates(currency, ratesByDate)` — a currency's stored rates in a `TreeMap` — and its rule (decision 1, 2.2):
  `rateForSettlementDay(day)` is the day's own rate, or the last one before it (`floorEntry`) when the day is inside the
  stored stretch; empty after the last stored rate (not published yet, or a holiday not yet told apart) and before the
  first. `knowsRateForSettlementDay(day)`. Session 3 converts with it. Built by `ExchangeRateRepository.storedRatesOf
  (currency)`, one query.
- `ExchangeRateDownloadSchedule(storedRates, settlementDaysNeedingARate, today).periodsToDownload()`: only the days
  really missing, checked before any call (the user's rule) — a settlement day up to today the stored rates don't know;
  a later one can't be published yet. Nothing missing: no call. Nothing stored: from two weeks before the earliest
  missing day to today. Before the first stored rate: from two weeks before it to the day before the first stored rate.
  After the last: from the day after it to today. Both: two calls, oldest first. Each call starts or ends next to the
  stored stretch, so it stays unbroken and no stored day is asked again; the two weeks bring the earliest day's "last rate
  before it".
- `ExchangeRateDownloadService.downloadMissingRates(currency, settlementDaysNeedingARate, today)` (not transactional, a
  `ReentrantLock` like Flex's): the schedule, the client, `saveAll` of what came; a failure is printed and stops the rest,
  never the sync — the next sync asks again for what is still missing.
Things that are easy to break:
- The Bank of Israel answers a period without any rate — a weekend, a holiday, today's rate before it is out (it
  publishes in the afternoon) — with **HTTP 404 and an HTML page**, not an empty CSV, and the same for a series that
  doesn't exist. So a 404 is "no rate" unless the period is longer than 14 days (`MAX_DAYS_WITHOUT_ANY_RATE`): then the
  series is gone, and it is an error naming it.
- The Bank of Israel publishes Monday to Friday, except on Israeli and international holidays (1.1, Good Friday, 25–26.12,
  …): 93 weekdays without a rate in 2020–2026, at most 7 days between two rates (`BankOfIsraelRate
  .LONGEST_GAP_BETWEEN_RATES_IN_DAYS`; Pesach 2023 and 2026). Both two-week numbers are twice it. Some of the account's
  settlement days fall on such a day: the "last rate before it" is an assumption seen in one case only (December 2025)
  — the ✓ of session 4 will check it.
- Only the edges of the stored stretch are ever extended. A gap inside it would read as a holiday and give a wrong rate —
  never store rates of a period that isn't next to the stretch.

The Overview's weight graphs and the holding drawer's Weight tab (2026-10-13) are worked out in the UI from what
`/api/portfolio` already sends — no endpoint, nothing stored. Both show a row of tabs (`components/InvestorTabs.tsx` —
`graphs/WeightsTabs.tsx` until the Taxes page shared it —, hidden with a single investor): **Portfolio**, then each
investor, the account owner first. A tab is an `InvestmentsAndCash` (`ui/src/components/graphs/investmentsAndCash.ts`,
`weightsTabsOf`): its `Investment`s — a holding as the tab weighs it:
all of it on Portfolio (IB's quantity, market value, average cost), one investor's `investorQuantities` part on their
tab (their quantity, `sharesValue`, `sharesCost` ÷ quantity), only the holdings they hold some of — its cash (IB's
`totalCashValue`, or the investor's card's `cash`), and its wording ("the whole account" / "all of Shay's money", "the
money in stocks" / "Shay's money in stocks", the cash's hint, "IB's average cost" / "the average cost of Shay's shares").
The parts follow the investors' cards' rule, so the investors' tabs add up to Portfolio's.
`ui/src/components/graphs/portfolioWeights.ts` weighs one tab (`portfolioWeightsOf`): each sector's and each holding's
share of the money in stocks and of "the whole account" — the stocks weighed plus the tab's cash (`wholeAccountOf`),
**not** `netLiquidation` (a hair more: it also counts accrued interest and dividends), so the holdings and the cash add up
to exactly 100% and, on Portfolio, agree with the Account summary's stocks-and-cash bar (which stays the account's). A
holding in another currency (the API has no exchange rate yet), with no market value, or worth 0 or less is left out and
named under the graphs. The sector ring (`DonutChart`, hand-drawn SVG — no chart library) is "of the money in stocks"
and colors the four heaviest sectors, the rest folded into "Other" — IB's sectors since 2026-11-05, so the outlined "No
sector" slice is now the holdings IB gives none for (an ETF: "1 holding IB gives no sector for") —; "By holding" is "of the whole account": the ten
heaviest holdings, the rest as "n more", then the cash, and a holding's bar opens its drawer on the Weight tab of the
same investor (the link's state, `HoldingDrawer.OpenOnTab` with `weightsInvestorId`). The tab's arithmetic is
`ui/src/components/weightCalculator/targetWeight.ts` (`holdingInAccountOf(holding, tab)`): the whole shares to sell
(rounded up, so it lands at the target or just under) or buy (rounded down), the weight after, the default commission
and, for a sale, the realized P&L at the tab's average cost — on the same whole account, which a sale or a purchase
doesn't change (cash and shares trade places). On the tab of an investor who holds none of the holding it starts from 0
shares, so only buying is worked out (`investmentIn`). Since MY_HOLDINGS_TODO.md's session 1 (2026-11-04) the tab has a
second field, "Or buy for an amount" (`buildPurchasePlanForAmount`: the whole shares the amount buys at IB's last price,
`calculateWholeSharesForAmount`, shared with If I buy; typing in one field empties the other), and every purchase —
from the target or the amount, one `buildPurchasePlan` — carries an `AverageCostAfterPurchase`
(`calculateAverageCostAfterPurchase`): the new average = (shares held × the tab's average cost + shares bought × the
price + the commission) ÷ every share after — the commission counted in, as IB counts it in its average cost —, and how
far the price must rise to reach it, against today's average (decision 2's example: 100 shares at 50, the price 40, 50
more for a commission of 5 — 46.70, +16.75% against +25%). At or above the average the row says "Already reached: the
price is 4.0% above the average"; without an average cost, or on the tab of an investor who holds none, there is no
average to change and the rows are left out. Since the design's second round (2026-10-25) the two graphs are
the halves of one "Weights" card (`graphs/WeightGraphs.tsx`) whose header holds the tab row; every row of the legend and
of the bars can be reached with the keyboard and explains itself in a `Hint` on the row itself (`wrapInSpan={false}`; a
`Tip` of its own until UI_POLISH_TODO.md's stage ה) — a slice of the ring lights its legend row without a bubble (the
user's choice) —, and the bars are drawn to scale by amount.
Things that are easy to break:
- `ui/src/lib/orderCommission.ts` copies `calculation.OrderCommission`'s rule (1¢ a share, at least $5) by hand: change
  both together.
- The ring's color order in `index.css` (`--color-chart-1` … `-4`) puts blue and violet, the pair hardest to tell apart,
  2nd and 4th, which never touch in a ring; a color-blindness palette validator checked it — check again after a change.
  `chart-3` is the design system's darker pink, `#db6898` (the first, `#ec7fa9`, was 2.6:1 on white, under the 3:1 a
  mark needs). The validator (2026-10-25) passes the four colors and every pair that touches but one: the grey "Other"
  next to the teal 1st sector, where the ring closes on itself when there is no "No sector" slice — known, not fixed.

The Averaging calculator (`ui/src/pages/AveragingCalculatorPage.tsx`, `/averaging`, the third navigation item;
MY_HOLDINGS_TODO.md session 3ב, decision 13; built from Claude Design's fourth round, DESIGN_BRIEF.md §9, arrangement A)
plans buying more of several holdings at once, worked out in the UI from `/api/portfolio` only — no endpoint, nothing
stored or sent. Its tabs are the weights' (`weightsTabsOf`, `InvestorTabs`), and each tab is a plan of its own: the page's
state keeps what is typed by tab and by holding (`TypedTexts`, gone when the page is left), and it opens on Portfolio or
on the tab a link asked for — `averagingCalculator/AveragingCalculatorLink.tsx`, shown under the drawer's Weight tab
calculator whatever is typed, carries the investor, and `investorIdToOpenOn(state)` reads it. The arithmetic is
`components/averagingCalculator/averagingPlan.ts`, over `targetWeight.ts` unchanged:
`splitHoldingsByAverage(tab)` turns the tab's holdings — on an investor's tab only those they hold some of: buying one
they hold none of is a new purchase, not averaging down — into `AveragingRow`s below the average cost (the farthest below
first, `calculateRiseNeededToReachAveragePercent`) and at or above it (the closest first,
`calculatePriceAboveAveragePercent`; both exported from `targetWeight.ts`, the second also used inside
`calculateAverageCostAfterPurchase`), and `LeftOutHolding`s with the Weight tab's own reasons plus "No average to bring
down: … is unknown."; `calculateRowResult(row, typedTexts)` gives a `RowResult` — `nothingTyped`, `purchase` (the Weight
tab's `PurchasePlan`, from `weightPlanOf` or `buildPurchasePlanForAmount`), `unusable` (which field, and why),
`atOrAboveTarget` (the page only buys; selling stays in the Weight tab) or `alreadyAtTarget` —; and
`calculatePlanTotal(rowResults, cash)` adds up the rows that buy into a `PlanTotal` (count, amount, commissions, total,
`cashLeft`). The purchases don't change one another: a purchase turns cash into shares, so each row's weight after is as
if it were the only one, and the one thing they share is the cash. The pieces, in `components/averagingCalculator/`:
`AveragingCashCard` (sticky at the top of the screen: the cash with its hint, the plan's sum and Clear, or the real
warning "626.60 short: …"), `AveragingSection` (the heading and its count; the second section folds, with "1 purchase
planned" while folded), `AveragingTable` (eleven columns in one row under Now · Plan a purchase · After the purchase, the
two fields `NumberInputWithUnit` — exported from `WeightCalculatorPanel.tsx` with a `widthClass` and an `ariaLabel` —, a
3px accent edge on a row that buys, the row's message above its line), `WhatToKnowLine` (under each row, five stacked
slots since UI_POLISH_TODO.md's stage ב: `SignalWords`, `HoldingCompanyRisks`, `MomentumMark`, `MedianTargetWithUpside`
from `AnalystTarget.tsx`, and `NextEarningsDate`) and `NotOnThisPage`. Three deliberate departures from the design (his
OK): the highlighter on "in 5 days" as everywhere else, not on the date; the Weight tab's own sentences in "Not on this
page"; 32px fields like every other. (A fourth, the cash's hint in `title`, ended with stage ה: its `Hint` opens below
"Cash", since the card sticks to the top.)

The Recommendations page (`ui/src/pages/RecommendationsPage.tsx`, `/recommendations`, the sixth navigation item; its
pieces in `ui/src/components/recommendations/`, built from Claude Design's third round — DESIGN_BRIEF.md §8, which
designed the whole page and marked each piece [now] or [later]; [now] in session 1b, [later] in session 4 — reworked
where decision 13 had overtaken the design: no text from Claude anywhere). App passes it the portfolio snapshot (the
cost estimate, and for If I buy the holdings and the cash). From the top: the scan card (`ScanCard`: the scan line —
"Scanned … · 68 stocks · 34 hot, 19 too hot, 8 correcting, 7 in no list" — with Scan again and its status, then each
investor's free cash from `/api/portfolio`), the recommendation (`RecommendationCard`, below), then Hot, Too hot and
Correcting, each a `StockListCard` (a `ListHeading` with its meaning and a grey band — the lists are not tones —, then the
table, whose last column is the stock's Analyze). The columns are defined like `POSITION_COLUMNS`, one entry each
(`hotStockColumns.tsx`: `HOT_COLUMNS`, `TOO_HOT_COLUMNS`, `CORRECTING_COLUMNS`), cells on two lines, the figure then its
mark: `InfoMark` (Pulled back, Moves together — grey, information) and `RiskSignTag` (the five risk signs, in the
warning-sign tone — `WARNING_SIGN_TAG_CLASS` —, with the figure that raised each — "Expensive · P/E 52" — where there is
room; `CandidateRiskSigns` sets a scanned stock's in a row: "—" never analyzed, then the shared `RiskSigns` — "—" with
"No risk signs" on hover, "Not checked" with `CANDIDATE_NOT_CHECKED_HINTS`' reason). Every list has
"Analysts' target" after the price (`TARGET_COLUMN`, decision 14, no new design round: the upside in its profit/loss tone
as `TargetWithUpside` colors one, the firms under it, the median on hover — `AnalystTarget.tsx`). Under each analyzed
Hot row, `AnalysisDetailLine`: one labeled line — "Risk signs … · Analysts BUY IMPROVING · Earnings … · News +2" (the
MarketBeat upside left it in session 4ב: two different upsides on one row would confuse; it stays in the drawer)
— and "Why it moves: …" in full under it when the news says (the user's pick, 2026-10-27, over the design's second header
row of slots, which didn't line up with the columns and cut the reason); the row and its line hover and select together
(`[&:has(+tr:hover)]` / `[tr:hover+&]`). The timing note — the report within 14 days — is run over with the yellow
highlighter (`HIGHLIGHTER_CLASS`, the `highlight` color, the user's choice in MY_HOLDINGS_TODO.md session 3 over the
design's outline), never the warning tone; in the line the date and "in 6 days" beside it. The texts the lists and the
drawer share are in `hotStockTexts.ts`.
`RecommendationCard` has three states: none yet (an invitation with Analyze & recommend, primary, and the estimate),
the picks (up to 3 `PickCard`s, numbered: name and IB's sector, the risk signs with their figures and the timing note, a
line the code writes from the figures — pulled back, Dow Jones's target against the price and its firms, the earnings
date —, "Score +4 · news +5 · risk signs weigh 1", and `NewsScoreParts`), or none qualified; then "Why not the others" —
every hot stock not picked: the analyzed ones one by one with their reason (no analysis, or `NotPickableReasons` — each
API reason in words with its figure, shared with the Company tab —, or its score below the picks), the rest grouped by
their target ("Past the first 10 with the analysts' target 15%+", "Analysts' target under +15%" with each one's upside,
"No analysts' target, fewer than 3 firms") — shown only when the recommendation is of the latest scan, since only then
are today's marks its own; a pick no longer in today's lists says so. `AnalyzeButtons.tsx`: `AnalyzeShortListControl`
(Analyze & recommend, after a confirm: "Analyze the 7 hot stocks whose analysts' target is 15% or more above the price?
About $0.33…"; disabled, with the reason, when none has one; the count is the stocks the API marks `inShortList` —
`shortListCountsOf` / `shortListWords`, so the UI holds no 10), `RowAnalyzeButton` (the lists' last
column: an icon, a confirm toward the left, the spinner while its own run runs, a triangle with the reason when it
failed — its clicks, and the confirm's, which React counts as inside it, never open the row; the user's choice over a
drawer-only button) and `AnalyzeCandidateButton` (the drawer's "Analyze KRVN", at once, like a holding's). The estimate
is `/api/portfolio`'s `estimatedAnalysisCost` × the stocks of the short list.
`useRecommendationAnalysis` holds the page's one run (`RecommendationAnalysisState`: the short list's — `candidateId`
`null` — or one stock's), so every button reads the same state and the others wait while one runs; it runs
`analyzeRecommendations` then the reload. `NewsScore.tsx`: each part of a news score with its points ("+3 Partnership ·
high: …", "−2 Downgraded by …"), points in the news's tones. A row (or a symbol in the card) opens `CandidateDrawer`
(`/recommendations/:candidateId`, the holding drawer's `DrawerFrame`, the risk signs and timing note under the key
figures once analyzed; ‹ › through the three lists): Summary — why it is in its list, the year, the stock, the holdings
it moves with —, the existing `MomentumPanel`, Analysts & news (first `AnalystTargetSection` — "Analysts' target · Dow
Jones": the median and its upside, the rule, each firm's latest target with the one it replaced, the headline on hover;
there before any analysis —, then the shared `AnalysisPanel` with the news score's parts under the news), Company
(`CandidateCompanyPanel`: "Not analyzed yet…", else the shared `CompanyPanel` with "For the recommendation" as its
children — the analysts' target, the signs' weight, whether it can be picked with every reason against it, the score)
and If I buy
(`weightCalculator/IfIBuyPanel`: an amount → whole shares at the last close, the commission, the stock's weight after
and its sector's — of the money in stocks — on each weights tab, the cash checked; the sector picked from the open
holdings' sectors (IB's since 2026-11-05, so a scanned stock's first word now matches exactly), already picked when one
of IB's two words matches one, or "A sector not held yet";
`ifIBuy.ts` the arithmetic, beside `targetWeight.ts`). `useRecommendations(portfolioSyncedAt)` treats 503 as "no scan
yet", not an error, and loads again when the portfolio's `asOf` changes (a sync may have scanned).
`useReadingFromTws(readFromTws, reloadAfterReading)` (2026-11-11, in place of `useScanAgain`) is every button that reads
TWS — Scan again here, Sync now in the top bar: it runs the call then the reload, and tells 409 (another reading runs,
"busy") from a failure by `ApiError.status`; both draw `ReadingFromTwsButton` (↻, a spinner and the running text while it
runs; size `regular` here, `small` in the top bar). `lib/drawerAddress.ts` (`idFromAddress`, `OPENED_FROM_TABLE`) is
shared with Positions.

The top bar (`components/topBar/TopBar.tsx` since UI_POLISH_TODO.md's stage א; `AppShell.tsx` until then): the alerts
bell first, **left of the "PortfolioBoss" wordmark** (his place, after two tries: right of Sync now, then right of the
name), the tabs after them, and on the right the sync in words and Sync now — the stage's file has the rest. Since stage
ד (2026-11-25, his ask) it is **sticky** (`sticky top-0 z-30`): scrolled down, a drawer opened under a bar that had
scrolled away "hung in the air"; `html`'s `scroll-padding-top` keeps what the keyboard reaches out from under it, and the
Averaging cash card sticks 8px under it. A Sync now that fails or finds another reading running shows the shared
`ErrorBanner` with the server's words, "The figures below are from the last sync." and Try again.
`components/alerts/` (decision 17; redesigned in Claude Design's sixth round, stage ד): `AlertsBell` (`useAlerts
(portfolioSyncedAt)` — `GET /api/alerts` on mount and on every new `asOf` —; a Radix popover opening 8px under the bar,
its left edge on the bell's — `PANEL_OFFSET_UNDER_THE_BELL` —; the badge is the unseen count in the accent blue, not red —
red is loss and the signal's tones only, his pick; opening marks the shown alerts seen at once (`markAlertsAsSeen`, then a
reload), and the ones new at the opening stay new while the panel is open), `AlertsPanel` (in place of `AlertList`:
"Alerts · 36 new · ⚙", then the server's two groups — `alertGroups.ts`: Your holdings, then The short list — each with its
count, its five newest rows and "Show 22 more" in place, at most `min(600px, 100vh − 80px)` with only the groups
scrolling; a row is two lines, the symbol and `alertTime.ts`'s "17:15" / "Yesterday" / date, then the message, a link to
the holding's drawer or to `/recommendations`) and `AlertSettingsDrawer` (`DrawerShell width="narrow"`, 440px, around
`AlertSettingsForm`: "Checked on every sync · 9 of 9 on", the kinds in the sentence-case groups of `alertTexts.ts` as
hairline lists, `AlertKindRow` — `SwitchNamedBy` (`Switch.tsx`: named by its sentence, which holds the field) and the
52 × 26 `ThresholdField` inside the sentence, the unit in the words after it, "Default: 7 days" under a changed number —,
Save checks the ranges first, then `saveAlertSettings`, a reload and `useCloseDrawer()`).
`DrawerShell` (2026-11-11, taken out of `DrawerFrame`) is every drawer's frame — the Radix dialog, 640px (or `narrow`)
from the right under the top bar, the veil, Esc not while typing, `DrawerTitle`, `DrawerCloseButton` —, and since stage ג
its motion: it leaves with its exit (`useCloseAfterExitAnimation`: `open` turns false, Radix plays the exit, `onClose`
runs on the element's own `animationend`), and gives what is inside `useCloseDrawer()` for a Cancel or a Save. **The
pages hold the shell** (Positions, Closed, Recommendations; each drawer exports its label — `holdingDrawerLabelOf`, …)
and key only the drawer inside it per item, so ‹ › fade the next item in rather than sliding the drawer in again;
`DrawerFrame` (a holding's, a closed position's, a scanned stock's) is the inside — the header, the tabs
(`navigation: DrawerNavigation | null`, ‹ › disabled while the first load runs), the panel keyed by its tab. `Modal` does
the same: its `onSubmit` answers whether the form saved, and the modal then closes itself with its exit — a form never
calls `onClose` after a save. The first load (stage ג): `OverviewPage` and `PositionsPage` take `snapshot | null` and show
skeletons beside their cards (`AccountSummarySkeleton`, `PositionsTableSkeleton` with each column's
`barWidthsWhileLoading`, `HoldingDrawerSkeleton`, …, on `components/firstLoad/SkeletonBar.tsx`), the other pages nothing
until the data comes; `EmptyBox` is a plain card — dashed means "add".

The hover explanations (UI_POLISH_TODO.md's stage ה, 2026-11-25, browser-checked by him): **no `title` anywhere** — the
browser's own tooltip shows in no screenshot or video —, only two ways, both Radix tooltips (drawn over the page, so no
card or scrolling table cuts them, and never moving the focus; one `HintsProvider` around the app in `main.tsx`: 400ms,
and no skipping the delay after a hint closes). **One sentence about one thing is a `Hint` on the thing**
(`components/Hint.tsx`): after 400ms of hover, at once on keyboard focus, gone on leaving or Esc; `placement` (`above` /
`below`), `align`, `term` (the dotted underline — only a term in running text: "Cash", "7 in no list", never a figure in a
table), `focusable` (false on a button or a link, which is its own stop), `wrapInSpan={false}` (the hint on the element
itself: a weights row, and text that must stay inline to wrap — the highlighted timing note, a company risk, "Why it
moves"), `disabled` (the bell while its panel is open; `ConfirmBeforeRun`'s `triggerHint` while its confirm is open). An
icon button keeps its `aria-label` and gets a `Hint` with the same words (the drawer's ‹ › ✕, the bell, ⚙, edit and
delete). **Two sentences or more, a list, or how a heading's figures are worked out is the one "?"**, `HelpOnHover` (20px,
opens at once — the user's choice over the design's click-to-pin popover; `HelpPopover.tsx` and the weights' `Tip.tsx`
are gone), 4px after the words it explains, never at a card's far edge: `CardHeading`'s, `ListHeading`'s and the tax
year card's `YearCardCell`'s `help`. **A table row the keyboard reaches as a whole** (Positions, Closed, the
Recommendations lists with their detail line, a tax day) wraps itself in `RowReachedAsAWhole`: its hints are no Tab stops
of their own (the user's choice; ten rows would be some sixty stops) — the same figures are stops in a drawer or a card.
`ShortenedText` is a text cut with "…", whole in its hint (a note, the Averaging sector, IB's company name);
`WarnMark` takes a `label` (its aria-label) and goes inside a `Hint` for its message.
Things that are easy to break:
- A disabled button gets no pointer events, so its hint — often the reason it is disabled — would never open: `Hint`'s
  span lets the pointer through (`[&_button:disabled]:pointer-events-none`).
- A `Hint` around a Radix trigger (a popover's, a dialog's) goes outside it, never as its `asChild` child: Radix's `Slot`
  hands its props to the child, and a component that drops them breaks the trigger (`ConfirmBeforeRun` puts the `Hint`
  around `Popover.Trigger`).
- Radix keeps a bubble open while the pointer is on its way to it and checks that only on the next pointer move: a test
  that jumps the pointer in one step sees a bubble stay open and the next one not open — move in steps
  (`page.mouse.move(x, y, { steps: 15 })`).

What a holding and a scanned stock both show (MY_HOLDINGS_TODO.md session 3, 2026-11-05/07) is outside
`recommendations/`, each piece taking only the part of a stock it shows — never a whole `HotStock` or `Holding`, whose
same-named fields can mean different things (a scanned stock's `analysis` is when Finnhub was asked, a holding's isn't).
`components/companyInfo/` takes a `CompanyInfo`: `RiskSigns.tsx` (`RiskSignTag`, `RecoveringTag`, `RiskSignTagList` — the
tags only, so another row can hold them —, `RiskSigns` — the tags, "—" or "Not checked" —, `RiskSignsLine` — a scanned
stock's drawer —, `NotCheckedHints` — the words, per kind of stock, for the two ways the signs go unchecked, Finnhub never
answered or gave no figure; `notCheckedHintOf` picks one), `TimingNote.tsx` (`earningsText`, `TimingNoteWhenDue` — the
whole note with the highlighter —, `NextEarnings` — the Company tab's words —, `NextEarningsDate({companyInfo,
markPlacement})` — the date with "in 6 days" `below` it in a table cell or `beside` it in a line; `bmo` / `amc` / `dmh`
are Finnhub's codes for before the open / after the close / during the session) and `CompanyPanel.tsx` (the Company tab:
"From Finnhub, answered …", the next report and the four figures with their rules, `CompanyRow`; the reason in place of
it when Finnhub never answered; `children` after it). `components/AnalystTarget.tsx` takes an `AnalystTarget`
(`TargetUpside`, `TargetCell`, `AnalystTargetSection({analystTarget, ruleText})` — the rule line is the caller's:
`TARGET_RULE_FOR_A_PICK` in `hotStockTexts.ts`, `TARGET_RULE_FOR_A_HOLDING` in `HoldingDrawer.tsx`). The holding's side:
the Positions table's Analysts' target (`TargetCell`) and Earnings (`NextEarningsDate`, below) — under Columns, "—" on a
closed holding —, and an open holding's drawer: the line under the key figures — since UI_POLISH_TODO.md's stage ב
`HoldingSignsAndRisks` (`components/HoldingWarningSigns.tsx`): "Signal" with the dot's three in words (`SignalWords`, from
`warningSignsOf`) and "Company risks" (`HoldingCompanyRisks`, shared with the Averaging calculator: each with its figure,
"No company risks", or "Not checked" with `HOLDING_NOT_CHECKED_HINTS`' reason) — the user's two names, so that they can't
be confused; the dot still counts its three —, then the timing note, `AnalystTargetSection` first in Analysts & news, and a Company tab
(`CompanyPanel`, no children); a closed holding gets none of them, its company info and targets no longer read.

The drawers' "Analysts & news" tab is one `AnalysisPanel` for a holding and a scanned stock (`HoldingAnalysisPanel` adds
the holding's button, open holdings only, and the dot's "?"): under the news, "Why it moves" (`moveReason`), and in the
analysts' list only the actions that state a price target — the rule the trend and the news score count by; the others
(a rating service's letter or rank, Weiss, Zacks; a rating kept without a target) count for nothing and are left out, with
a faint "n actions without a price target are not shown" (the user's call, 2026-10-27, after Weiss showed as DOWNGRADED).
`ConfirmBeforeRun` (in `AnalyzeControls.tsx`) is every paid run's confirm popover; the Positions toolbar's Analyze uses it
too.

`HoldingWriteController` serves the writes through `HoldingWriteService` (`@Transactional`, the write-side
twin of `PortfolioReadService`): `POST
/api/holdings/{holdingId}/trades` (201 + the new trade as a `TradeResponse`), `PUT /api/trades/{tradeId}`
(200 + the trade) and `DELETE /api/trades/{tradeId}` (204) — the last two for any trade, a manual position's too.
`PUT /api/holdings/{holdingId}/sector` and its `SectorRequest` were removed on 2026-11-04 (MY_HOLDINGS_TODO.md,
decision 11: a holding's sector comes only from IB; the request now answers 404, `HoldingWriteControllerTest` pins it).
`ManualPositionWriteController` / `ManualPositionWriteService` (same pattern) serve `POST /api/manual-positions`
(201 + a `ManualPositionResponse` with the id; the body, `NewManualPositionRequest`, carries the details plus a first
buy and first sell of the same quantity, so the position always has a sell to show — its cross-field rule, sell date
not before buy date, is an `@AssertTrue` private method), `PUT /api/manual-positions/{id}` (204, details only:
`ManualPositionRequest`), `DELETE /api/manual-positions/{id}` (204, its trades deleted with it) and `POST
/api/manual-positions/{id}/trades` (201, a `TradeRequest`). The symbol and currency of a manual position are stored in
capitals. A manual position's **last sell** can be corrected but not deleted or turned into a buy — it would vanish
from the closed positions — so `HoldingWriteService` answers that with 409 Conflict. `InvestorWriteController` /
`InvestorWriteService` serve `POST /api/investors` (201 + an `AddedInvestorResponse`) and `PUT
/api/investors/{investorId}` (204, a rename — the owner too) with an `InvestorRequest(name)`; a name already taken,
ignoring case, is 409, and the account owner is created only by V4, never by the API. `POST
/api/investors/{investorId}/cash-movements` (201), `PUT /api/cash-movements/{movementId}` (200) and `DELETE
/api/cash-movements/{movementId}` (204) take a `CashMovementRequest(movementDate, type, amount, note)`; a deposit for the
account owner is 400 — the owner's cash comes from IB. `TradeRequest` and `NewManualPositionRequest` gained an optional
`investorId`: a new trade without one is the account owner's, a **correction without one keeps the trade's investor**,
and an id that doesn't exist is 400 (`InvestorWriteService.investorOfTrade`, used by both trade services). After a
write the UI reloads all of `/api/portfolio`, so the endpoints stay small. The bodies are the `api/request/` records
(`TradeRequest`, `NewManualPositionRequest`, `ManualPositionRequest`, `InvestorRequest`,
`CashMovementRequest`), checked by `@Valid` before the method runs, with
limits that mirror the columns (`@Digits(integer = 14, fraction = 6)` for a price or commission in `NUMERIC(20,6)`,
with its own readable message; a quantity is a **whole number** of shares, `@Digits(integer = 14, fraction = 0)` —
"must be a whole number (at most 14 digits)", which also rejects a raw `10.0` since it counts the decimals as
written; `@Size` for the `VARCHAR`s, `@PastOrPresent` dates). A trade's price is required (`@NotNull`, since
2026-10-13 — "price: must not be null"; the table refuses it too, V8), and 0 is a price. A commission left empty stores
the default (`OrderCommission.orDefault`); 0 is a commission too. A manual position's sector and a note are trimmed,
and blank becomes `null`.
An unknown id is a `ResponseStatusException` with 404. `ApiErrorHandler` (`@RestControllerAdvice extends
ResponseEntityExceptionHandler`) makes every error a `ProblemDetail` JSON body (`status`, `title`, `detail`)
and overrides only the validation case, so that `detail` names the fields — `"quantity: must be greater than
0"`, sorted, in English (Hibernate Validator ships no Hebrew messages). The UI shows that `detail` as it is;
its write calls are all in `ui/src/lib/apiClient.ts`, named after the service's methods.
Things that are easy to break:
- `spring-boot-starter-validation` must stay in `pom.xml`: without it `@Valid` is silently ignored — no
  startup error, invalid input just reaches the database.
- IB's average cost spreads the commissions over every share, so it has more decimal places than the 6
  `@Digits` allows (`188.2990476`, found in the live check): the UI's `TradeModal` rounds it to 4 places when it
  prefills a price. Anything else that sends an IB figure as a trade amount has to round it too.
- `consumes = APPLICATION_JSON_VALUE` states the JSON-only rule, but it is not the only guard: a
  `@RequestBody` record can only be read from JSON, so another `Content-Type` gets 415 even without it (tried).

`UiLauncher` copies IBBot's `launchVisualizer()`: `bash -l -c "npm run dev"` in `ui/` (a login shell,
so `npm` is on the PATH), output to `ui/dev-server.log`, then macOS `open` once the port answers.
Things that are easy to break:
- It probes every address `localhost` resolves to, because Vite listens on `[::1]` only on this
  machine — a plain `127.0.0.1` probe never succeeds.
- A shutdown hook stops npm *and its child processes*; killing only npm leaves Vite holding the port.
- An already-running Vite is reused, and is neither restarted nor stopped on exit.
- Failures are logged as `[ui error]` and never stop the API.
- `TwsPortfolioRunner.UI_PORT` must match `ui/vite.config.ts` (`strictPort` is on), and `ui/` is found
  relative to the working directory — which is why `run.sh` `cd`s to the project root before
  starting Maven.

## Conventions

- Console output uses `[ib]` prefixes for connection lifecycle, `[ib error]` for real errors, `[api]`
  for the local API, `[ui]` / `[ui error]` for the UI launcher, and `[db]` / `[db error]` for the sync
  (`[db] synced N holdings (N new, M updated, K closed)` on success, then `[db] stored N daily closes for M contracts`;
  `[db] TWS unreachable; serving the portfolio from the last sync, if any` when TWS could not be read; `[db error]` only
  when the sync itself fails, which is also the only case that still exits the app). The daily closes print
  `[ib] requesting a year of daily closes for N contracts` and `[ib] daily closes received for N of M contracts`. The
  scan prints `[ib] scanning for hot stocks: …`, `[ib] requesting the contract details of N stocks`, `[ib] scans found N
  stocks, M of them on NYSE or NASDAQ`, `[ib] left out, not on NYSE or NASDAQ: XYZ (ARCA), …` when any is, `[ib] scan
  skipped: …` when none is due and `[ib] scan unavailable: no scan answered`; its Dow Jones headlines `[ib] requesting
  half a year of Dow Jones headlines for N stocks`, `[ib] Dow Jones headlines still missing after 20s; keeping what
  arrived` when a round runs late, and `[ib] analysts' price targets: N in the Dow Jones headlines of M of K stocks`;
  storing it, `[db] stored a scan of N stocks` and `[db] stored N analysts' price targets for M stocks`. The holdings'
  (MY_HOLDINGS_TODO.md, session 2) print `[ib] requesting the contract details of N stocks` a second time (for the
  holdings the scan didn't ask about), `[ib] contract details received for N of M holdings`, `[ib] analysts' price
  targets of the holdings: N for M of K holdings`, then `[stocks] Finnhub answered for N of M holdings in dollars` and
  `[db] stored Finnhub's data for N holdings`.
  The
  analysis prints `[ai] analysis ready` (or `[ai] analysis off: … is not set`) at startup, `[ai] analyzed N of M stocks:
  N Tavily credits, N input and N output tokens, $N` per run, and `[ai error] SYMBOL: why` for a stock that failed; the
  Recommendations page's full run then `[ai] recommended ABCD, EFGH, WXYZ (of 7 analyzed; 7 of 41 hot stocks have the
  analysts' target 15% above the price)` or `[ai] no recommendation: none of 7 qualified (…)`. Finnhub prints `[stocks] Finnhub off: FINNHUB_API_KEY is not set (config/local.env)` at startup
  when its key is missing, and nothing when it is set; `[stocks error] SYMBOL: why` when a call fails during a run.
  The alerts print `[alerts] checked: 2 new (ABCD, WXYZ), 1 no longer true` (or `checked: nothing new, 0 no longer true`)
  after every sync and every save of the settings, and `[alerts error] could not check the alerts: …` when a check after a
  sync fails (the sync stays stored). The Flex reports print `[tax] Flex off: FLEX_TOKEN is not set (config/local.env)`
  at startup without a key; on a sync `[tax] Flex: asking IB for 2026-11-03 – 2026-11-13`, then `[tax] Flex: 41 trades, 6
  cash transactions, 0 corporate actions (2026-11-03 – 2026-11-13)` per period; `[tax] Flex skipped: already read up to
  13.11` (or `another download is running`), and `[tax error] Flex: IB refused the request: 1012 Token has expired.` when it
  fails (the sync stays stored). The tax prints, after the rates of every sync that reached TWS, a line per tax year of the
  account — `[tax] capital gains tax 2025: 1,234.56 ILS on a taxable gain of 4,938.24 ILS (12 sales; not computed: 1 in a
  currency without exchange rates)`, with `, a shield of 300.00 ILS left` when there is one — and per year of each other
  investor (`[tax] capital gains tax of Avi 2026: …`), then `[tax] our FIFO against IB's: 75 of 76 sales match` and each
  sale that doesn't (`… ABCD sold on 2026-03-02 (IB trade 1000000001): ours 50.00 USD, IB's 47.50 USD`, the first
  ten); `[tax error] could not compute the capital gains tax: …` when it fails (the sync stays stored). The exchange rates print, on a sync that misses some, `[exchangerates] Bank of Israel: asking for
  the USD rates of 2026-11-14 – 2026-11-16`, then `[exchangerates] Bank of Israel: stored 3 USD rates (2026-11-12 –
  2026-11-16)` or `[exchangerates] Bank of Israel: no USD rate published in 2026-11-14 – 2026-11-16`; `[exchangerates]
  Bank of Israel skipped: every settlement day up to 16.11 has its USD rate` (or `another download is running`); and
  `[exchangerates error] no answer from the Bank of Israel (Read timed out)` when it fails (the sync stays stored).
  The splits print, after the Flex reports, `[db] stored a stock split: ABCD 4 for 1 on 2026-03-10` per new split (with
  `, into a new contract` when it moved the stock), `[db] a stock split not understood: ABCD on 2026-03-10: …` per split
  not understood, on every sync, and `[db error] could not store the stock splits: …` when the step fails (the sync stays
  stored).
- Packages are named after their area, and where the area prints to the console its name is the prefix:
  `ib` / `[ib]`, `api` / `[api]`, `ui` / `[ui]`, `db` / `[db]`, `ai` / `[ai]`, `stocks` / `[stocks]`, `alerts` /
  `[alerts]`, `tax` / `[tax]`, `exchangerates` / `[exchangerates]`. `calculation`, `model`, `utils` and `exceptions`
  print nothing.
- A feature of several files gets a package of its own, named after it, holding all of it — data, rules, entities,
  service, controller, `request/`, `response/` — rather than a file in each layer's package (`alerts`, 2026-11-11, the
  user's call: "the files spread over the packages is really bad"); only what is general (`PortfolioWeightsCalculator`,
  `DailyClosesWithSpy`) goes to `calculation`.
- Package dependencies point one way, with no cycle: `utils ← ai ← model ← calculation ← db ← api`, and `ib` — which
  uses none of them — is used by `calculation`, `db` and `api`. `exceptions` (2026-10-26, the user's request: the app's
  own exceptions in one package — `TwsUnavailableException`, `TwsBusyException`) uses nothing and is used by `ib`,
  `api`, `tax` and `exchangerates`. `stocks` (2026-10-27: Finnhub's client and what it reads) uses only `utils`; like `ib` it is a source of data,
  used by `calculation`, `db` and `api` (since session 3). `alerts` (2026-11-11) sits on top of them all: it uses `api`
  (only `RecommendationsReadService`, for the short list), `db`, `calculation`, `ib`, `stocks` and `model`, and no package
  uses it — `api.SyncService` announces a sync with `PortfolioSyncedEvent` instead of calling it. `tax` (2026-11-15, the
  capital gains tax) is the other way round, **under** `api`: `api.SyncService` calls `tax.FlexDownloadService` and
  `tax.CapitalGainsTaxService` directly, so it must never use `api` (a cycle) — which is why its endpoints are its own
  `tax.TaxController` (session 4), with `tax.request` and `tax.response`, like `alerts`; it uses `db` (`TradeRepository`, and since session 3 the holdings, manual positions, investors and the account
  state), `model` (`TradeSide`), `exchangerates`, `exceptions` (`FlexReportUnavailableException`) and `utils`. `exchangerates`
  (2026-11-15, the Bank of Israel's rates) is **under** `tax`: a source of data like `stocks`, it uses only `exceptions`
  (`ExchangeRatesUnavailableException`), is told which days need a rate (`tax.SettlementDayRatesService`), and must never
  use `tax` or `api` (a cycle: `tax` converts with its `ExchangeRates`). `TwsPortfolioRunner`
  (the root package) uses `api.SyncService`. `calculation` holds only what computes; the
  data it computes on lives where it comes from — IB's readings in `ib`, Claude's answer in `ai`, Finnhub's in `stocks` —
  or, when the whole app shares
  it (an enum both an entity and the computation need, like `TradeSide`; a warning; a label), in `model`. `calculation`
  uses nothing of Spring, the database or the IB API — only those packages' records. Before 2026-10-08 three enums sat
  in `db` and the two packages depended on each other; until 2026-10-09 `calculation` also held all the data. The
  records in `api.response` only hold the JSON's shape; putting them together is `PortfolioReadService`'s job, as
  private methods, not static factories on the records.
- Readability over brevity, in Java and TypeScript alike: descriptive names (`holding`,
  `sortState`, `response`), never one-letter variables (the conventional `e` in a `catch` is fine),
  and small functions/components with a single job instead of long inline expressions.
- The UI's colors are the design system's roles only: `ui/tailwind.config.js` defines no raw Tailwind color
  (`slate-500` simply doesn't exist), the values are CSS variables in `ui/src/index.css`, and the recurring pieces —
  buttons, fields, tables, tags, chips — are shared classes in `ui/src/lib/styles.ts`. Green and red are for profit
  and loss and the signal's tones only (BUY/SELL are grey); the two warning levels are told apart by shape and lightness
  (`WarnMark`: a yellow outlined triangle for minor, a solid orange one for real), and the only red fill is the confirm
  dialog's Delete. The one set of colors that only tells things apart is the sector ring's `chart-1` … `chart-4` (with
  the grey `chart-rest`): none of them green, red, yellow or orange, which already mean something. The stocks and the
  cash keep one color each wherever they appear side by side (`STOCKS_COLOR_CLASS`, `CASH_COLOR_CLASS`). One yellow is
  not a warning: `highlight` (#ffe94d, `HIGHLIGHTER_CLASS`), a highlighter over a date to look at — the next report 0–14
  days away (MY_HOLDINGS_TODO.md session 3, the user's choice) —, brighter and more saturated than the minor warning's
  pale band so the two read apart.
- One entry point: `Main` starts the Spring Boot app, and `TwsPortfolioRunner` runs the startup (its sync is
  `api.SyncService`'s, shared with Sync now). `ib.TwsReader` owns the TWS host/port/client id and opens each `IbGateway`,
  one at a time — the startup, Sync now and the Scan again button all read through it. Don't add a second entry point or
  a second place that connects to TWS.
- API response types go in `portfolioboss.api.response`, request bodies in `portfolioboss.api.request` (a feature
  package keeps its own: `alerts.request`, `alerts.response`, `tax.request`, `tax.response`); the
  accessors, derived-math and write methods another package needs (`Holding.costBasis()`,
  `HoldingEntity.id()`/`sector()`/`industry()`/`trades()`/`toIbHolding()`, `TradeEntity`'s constructors and
  `changeDetails()`/`changeInvestor()`, `ManualPositionEntity`'s constructor and `changeDetails()`, `InvestorEntity`'s
  constructor and `changeName()`, `toPositionTrades()`, `TradeResponse(TradeEntity)` — built by the write services in
  `api` —, the other response records' copy constructors, which `PortfolioReadService` calls, …) are
  `public`. Everything internal to a class's own package — `HoldingEntity`'s and `AccountStateEntity`'s
  from-a-snapshot constructors, `refreshFromIb`, `refreshSectorAndIndustryFromIb`, `markClosed`, `StockEntity`'s
  constructor, the `PortfolioWrapper` methods `IbGateway` calls,
  `HoldingHistory.QUANTITY_TOLERANCE` — is marked `protected` rather than
  left as the unmarked package-private default: an explicit keyword is easier to spot while reading than the
  *absence* of one. (On a `record`, always `final`, or on a package-private top-level class, `protected` is
  exactly as reachable as package-private in practice — nothing outside the package can subclass either — so
  this is a readability choice, not a wider one.) What only Spring calls, by reflection, is `private`: the
  controllers' constructors and endpoint methods, and `TwsPortfolioRunner`'s constructor. JPA entities' empty
  constructors stay `protected`: Hibernate's lazy-loading stand-in subclass calls them (`private` broke tests). `static`
  only with a real reason — `main`, helper classes with a private constructor (`Utils`, `AppMetadata`, `Benchmark`,
  `OrderCommission`, `HotStockScan`), a factory that has to compute before the record exists (`HoldingHistory.of`,
  `Momentum.of`) — and constants stay `static final`; a helper that doesn't touch fields is still an instance method, and
  a factory that only copies fields is a constructor. A computation with several inputs is a record of those inputs with
  ordinary methods (`InvestorSummaryCalculator`, `ConsensusCalculator(sources, marketPrice).consensus()`,
  `SignalCalculator`, `HotStockCalculator`, `ScanSchedule(lastScannedAt, now).isScanDue()`), not a
  static method — the user's stated preference (repeated 2026-10-26: avoid static methods as far as possible). He also
  asked for long, meaningful names over short ones, and rejected constructs he found unreadable — a generic method's
  `private <K>` (so `RequestBatch` numbers its own requests and names its type parameters `<Key, Answer>`), and a class
  named after a rule (`ScanSchedule`, not `ScanOncePerTradingDay`): explain an unfamiliar Java construct before using it.
  A helper used by more than one layer —
  `portfolioboss.utils.Utils`, shared by `api`, `db`, `ai` and `calculation` — is the one exception to "narrow package,"
  and lives in its own package rather than being duplicated per layer.
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
- Milestone 1 direction: Maven, Spring Boot REST, the PostgreSQL schema and entities, the sync at connection,
  deriving buy/sell dates and holding period from `trade` rows, the write endpoints for the sector and trades,
  the UI to show and enter them, the reconciliation warnings, the closed positions (commissions, average cost,
  manual positions) and the investors sharing the account are in, and so are the momentum score and the analysis of
  AI_ANALYSIS_TODO.md, backend and UI; the API reads only from the database. From Milestone 3, the weight of each holding
  and sector (TODO.md's principle 3) and a target-weight calculator — with the new average after a purchase since
  MY_HOLDINGS_TODO.md's session 1 — are in — of the whole account and of each investor's
  own money —, in the UI only. RECOMMENDATIONS_TODO.md's scan, hot-stock lists and page (sessions 1 and 1b), Finnhub's
  client (session 2), the analysis and recommendation's backend (session 3) and their UI (session 4, with V11 and the
  first full run from the page) are in, and so is session 4ב (the analysts' target from Dow Jones, decision 14), checked
  live with V12; the measurement (session 5) comes after. MY_HOLDINGS_TODO.md is the current plan: its sessions 1 (the
  new average in the Weight tab) and 2 (the holdings' analysts' target, Finnhub's figures and risk signs in the API, the
  sector and industry from IB — V13, V14 —, checked live 2026-11-05) and 3 (their UI, with `companyInfo` shared by a
  holding and a scanned stock) are in, and so is session 3ב (the Averaging calculator, a sixth page, UI only); session 5
  came before 4 (2026-11-11): Sync now (5א, browser-checked) and the alerts with their bell and settings drawer (5ב, V15,
  browser-checked — his first look moved the bell to the left of the name), with no daily run (decision 14)
  and the alerts' channel left for later; session 4 (holdings that move together, a market drop of 20%) waits.
  CAPITAL_GAINS_TAX_TODO.md is the current plan (TODO.md's "nice-to-have" tax, brought in by the user): session 1, IB's
  Flex reports downloaded and stored on every sync (`tax`, V16), and session 2, the Bank of Israel's dollar rates of the
  settlement days (`exchangerates`, V17), are built, and so is session 3, the calculation — FIFO, the taxable gain, the
  yearly balance, the corporate actions (V18) —, checked live against IB's FIFO and the 867s of 2024 and 2025, and so is
  session 4, the confirmations and the page's endpoints (V19, `/api/taxes`, checked live), and so is session 5, the Taxes
  page (browser-checked), built before the session still to plan — the Flex trades not typed in, offered and split between
  investors (the user's idea), which brings the owner's tab and the "day trade" tag. STOCK_SPLITS_TODO.md is the current
  plan: session 1, the splits stored from the Flex reports (V20) and the trades translated on every read, is built and
  checked live (2026-11-22); session 2, its UI, is built and browser-checked (2026-11-23). HOLDING_DETAILS_TODO.md's sessions 8–9 (detected-change trade drafts, a stale-data banner) are optional
  ideas. There is **no written thesis per holding** (decided 2026-10-06: data, not text): the product is the data on
  each holding that helps decide whether to stay in it — not the IB reader.
