# PortfolioBoss — UI redesign brief

A brief for designing the new look and layout of PortfolioBoss's web UI. It lists every piece of information the
UI shows today, where each should go in the new layout, and made-up sample data to design with. **Every number,
symbol, name and account id in this brief is made up.**

Work through it **one screen at a time**, in this order: the app shell and the Overview page, the Positions table,
the holding drawer, the Closed page, the Investors page, then the modals.

**Status:** sections 1–6 were the **first round**: every screen in them was designed and then built on 2026-10-11, and
they describe the UI as it was *before* that round. **Section 7 is the second round:** the weight graphs and the
drawer's Weight tab, built on 2026-10-13 from the first round's tokens but without a design pass, then designed in
Claude Design and rebuilt from it on 2026-10-25.

---

## 1. What PortfolioBoss is

- A **local, single-user** web app for one person's **long-term** Interactive Brokers portfolio. It runs on
  `localhost` in a desktop browser. Nobody else ever sees it.
- **Read-only against the broker:** it never places an order. It shows the holdings and adds what the broker
  doesn't know: each holding's sector, the trades it was built from, a momentum score, an analysis of analyst
  ratings and news, the positions already sold, and how the account divides between several people whose money is in it.
- **Used a few times a week, not all day.** It is a calm decision-support tool, not a trading screen. The data is
  as of the last sync, with no live prices and no streaming.
- **Tone:** calm, trustworthy, quiet, like a well-made financial report. Nothing flashes or pulses, there is no
  gamification and no marketing look. It is dense with figures, but every figure has a clear place.

### What is wrong today

1. **Everything is on one long page:** the account summary, the investors' cards, a 12-column positions table whose
   expanded row shows four stacked boxes (momentum, analysts and news, split by investor, trades with their form),
   and then the closed positions table with its own expanded rows and forms.
2. **The theme is dark** (near-black slate with pastel green/red/amber accents). It should be **light**.

---

## 2. Goals

1. **A light theme only.** No dark mode for now.
2. **Separate pages, each with its own URL,** and a top navigation bar: Overview · Positions · Closed · Investors.
3. **A side drawer for a holding's details** (sliding in from the right, the table still visible behind it), with
   tabs, instead of the row that expands inside the table and pushes everything below it down.
4. **Forms in modals:** add/edit trade, new closed position, add/rename investor, add/edit deposit.
5. **A leaner Positions table:** fewer columns by default, the rest available from a "Columns" menu.
6. **Explanations behind a "?" icon** (popover), instead of legend text that is always on screen.

### Non-goals

- **No new features and no new data.** Every figure shown today must stay reachable, even if it is one click deeper.
- **No change in behavior:** sorting, validation and what each button does stay as they are. This is about
  layout and look.
- **Not mobile.** Design for a desktop or laptop screen, usable down to about 1280 px wide. A wide table may scroll
  sideways.

---

## 3. Technical constraints

- **React 19, Tailwind CSS 3, lucide-react icons.** Prefer plain Tailwind. If the drawer, modal, popover or dropdown
  needs a small headless library for focus handling and keyboard support, name it and say why.
- **Colors as named roles, not raw Tailwind colors.** Define the palette as roles (each a CSS variable mapped into
  the Tailwind config), and use only these roles in the design:
  - page background, surface (cards, table), raised surface (drawer, modal, popover), subtle fill (table header,
    hover), border, strong border
  - text, muted text, faint text
  - accent (primary buttons, the active navigation item, focus ring)
  - positive (profit, "good"), negative (loss, "warning sign"), in-between (amber), unknown (grey)
  - holding warning, minor (see §4), holding warning, real (orange)
  - link (sources, opening in a new tab)
- **Text contrast at least WCAG AA** on its own background. The current pastel shades (Tailwind 300–400 greens,
  reds and yellows) do not pass on white.
- **Language:** English UI text, left to right.
- **Numbers:** tabular (fixed-width) figures, right-aligned in tables. Money has 2 decimals and thousands
  separators. Profit/loss and percentages always carry a sign (+/−). A whole number of shares has no decimals.
  **An unknown figure shows as "—".** Dates are `yyyy-MM-dd`. A holding period shows its two largest units:
  "1y 4m", "3m 9d", "45d".
- **URLs:** `/` Overview, `/positions`, `/positions/:holdingId` (the drawer open on that holding, so a refresh keeps
  it open), `/closed`, `/investors`.

---

## 4. Color meanings to keep

These meanings are settled; only the exact shades change.

- **Profit / loss:** green / red, for any P&L figure and its percentage.
- **Signal tones,** one meaning everywhere: **good** (green), **in-between** (amber), **warning sign** (red),
  **unknown** (grey). They are used by:
  - the momentum label: STRONG good · NEUTRAL in-between · WEAK warning
  - the analysts' trend: IMPROVING good · STABLE in-between · DETERIORATING warning
  - the news sentiment: POSITIVE good · NEUTRAL in-between · NEGATIVE warning
  - the analyst rating: STRONG BUY / BUY good · HOLD in-between · SELL / STRONG SELL warning
  - analyst actions: UPGRADED / TARGET RAISED good · DOWNGRADED / TARGET LOWERED warning · NEW COVERAGE /
    RATING KEPT grey
  - **the signal dot:** GREEN (no warning sign), YELLOW (one), RED (two or more). The warning signs are weak
    momentum, analysts deteriorating and negative news. Before a holding has been analyzed the dot is an **empty grey ring**.
- **Holding warnings have two levels**, and both must stay clearly visible on a light background:
  - **minor** (today yellow): "No buy entered yet…". It is expected while old trades are still being entered and
    shows on many rows at first.
  - **real** (orange): the trades don't match IB's quantity, or IB no longer reports a holding that no sell closed.
  The same orange marks a closed position that sold more than it bought, and an investor's warnings.
- **Source links:** link color with a small external-link icon, opening in a new tab.

---

## 5. The screens and what each shows

### 5.1 App shell (every page)

- **Top bar:**
  - The name "PortfolioBoss".
  - The navigation: **Overview · Positions · Closed · Investors**.
  - On the right:
    - the account id ("U1234567")
    - "Synced from TWS 2026-10-10 16:05": the data is as of that sync, not live
    - a small load-status dot (loading amber, ready green, error red)
- **Error banner** under the top bar when the local API can't be reached: "Could not reach the PortfolioBoss
  API… Is ./run.sh running?" with a **Retry** button. The last loaded data stays visible under it.
- The line "Read-only against Interactive Brokers · sector and trades are stored locally" can move to an info
  popover or a footer.

### 5.2 Overview (`/`), a new page built only from data that already exists

- **Account summary,** four figures: Net liquidation, Cash, Positions value, Unrealized P&L (green/red).
- **Needs attention:** a short list where each item opens that holding's drawer. Each item shows the symbol, why
  it is on the list (the warning's text, or "Signal RED: weak momentum, analysts deteriorating"), and its signal
  dot. The items are:
  - holdings with a **real** warning (minor ones are counted, not listed: "3 holdings have no trades entered yet")
  - holdings whose signal is **RED**
  - holdings never analyzed
  - When nothing needs attention: a calm empty state ("Nothing needs your attention.").
- **Signal at a glance:** how many open holdings are green / yellow / red / not analyzed.
- **Investors strip,** only when there is more than one investor: each investor's name, total value and total P&L,
  linking to the Investors page.
- The designer may propose a better arrangement. The rule is: no figure that the API doesn't already provide.

### 5.3 Positions (`/positions`)

**Toolbar:**
- **Analyze** (analyzes every open holding). It is a **paid** run: about $0.04 and 5 search credits per stock, so
  about $0.30 for 7 stocks. It takes tens of seconds and shows "Analyzing…" with a spinner. Then it shows the result
  inline: "Analyzed 7 · 35 credits · $0.30", plus in red "· Failed: XYZ" when some stock failed (the reason on
  hover), or the server's error text. The button's tooltip says what it does and what it costs. There is no confirm
  dialog today; the designer may suggest one.
- **Show closed (2)** toggle. Holdings IB no longer reports are hidden by default.
- **Columns** menu (new).
- The attention line "2 holdings need attention" (orange), adding "(1 closed, hidden)" while closed holdings are hidden.

**Table,** one row per holding. Clicking a row opens the drawer.

| Column | Default? | Notes |
|---|---|---|
| Symbol | yes | + a warning triangle (minor or real color, message on hover), + a "closed" tag on a closed holding |
| Signal | yes | see "the Signal cell" below |
| Sector | yes | editable: click to edit inline, "+ add" when empty |
| Qty | yes | with several investors, "40 (20 · 20)": the account owner first, then the others (names on hover) |
| Market value | yes | in the holding's currency |
| Unrealized P&L | yes | green/red, signed |
| % | yes | unrealized P&L as a percent of the cost, signed |
| Held | yes | holding period ("1y 4m"), exact days on hover |
| Avg cost | in "Columns" | |
| Last | in "Columns" | IB's last price |
| Bought | in "Columns" | first buy date of the current holding period |
| Last sold | in "Columns" | |

(This split of default and optional columns is a proposal; the designer may change it.)

- **Sorting** by any column header. The default is market value, high to low. Empty values ("—") always sort last,
  in both directions.
- A **closed** holding (when shown) is dimmed and tagged "closed".
- **The Signal cell** today is four small pieces in a row:
  - the **dot**
  - **"M 4/5"**, the momentum score, colored by its label
  - **"A"**, the analysts' trend
  - **"N"**, the news sentiment

  "A" and "N" appear only after the first analysis, and every piece explains itself on hover. It is compact but
  cryptic to a newcomer. Propose something that stays one column wide but reads more easily.

### 5.4 The holding drawer (`/positions/:holdingId`)

It slides in from the right over the table, roughly 560–680 px wide. It closes with ✕, Esc or a click outside, and
may offer previous/next holding arrows.

**Header:**
- the symbol and currency, a "closed" tag if closed, the signal dot
- the sector, editable inline
- key figures: Qty, Last, Market value, Unrealized P&L (and %), Held
- the holding's warning, if it has one (at most one), in its color with its message

**Tabs** (the designer may propose a "Summary" tab first that shows the three parts of the signal side by side):

1. **Momentum**
   - "STRONG 5/5 · daily closes as of 2026-10-09"
   - the five checks, each with ✓ / ✗ / ? and its figures:
     - Above the 20-day average · 468.15 vs 455.20
     - Above the 50-day average · 468.15 vs 441.80
     - 50-day average above the 200-day · 441.80 vs 420.35
     - Within 10% of the 20-day high · 1.2% below 473.80
     - Ahead of SPY over the last month · +4.30% vs SPY +1.20%
   - an unknown check reads, e.g., "Fewer than 200 daily closes"
   - behind a "?": "STRONG 4–5: hold or add · NEUTRAL 2–3: wait, don't add · WEAK 0–1: consider selling part"
   - empty state: "No daily closes stored yet: they are read from IB on every run.sh."
2. **Analysts & news**
   - the header line "analyzed 2026-10-09 10:52 by claude-sonnet-5-5", with the button **Analyze MSFT** (open
     holdings only; paid, about $0.04)
   - **Analysts:**
     - the consensus line: rating chip · "48 analysts" · "target 545.20 (+16.46%)" · the source site as a link ·
       "copy of 2026-10-02". The copy date is the search engine's date for its copy of that page, which may be weeks
       old; there is a hint on hover.
     - "Targets 528.00–561.30 across 4 sources", with every source's figures on hover
     - "Trend" chip + "3 targets raised, 1 lowered", or the reason there is no trend ("no price targets among the
       analyst actions")
     - up to 10 action rows: date · firm · action chip · rating change · target change · source link · copy date
   - **News:** the sentiment chip + a one-line reason, then up to about 5 headlines (date · source · title as a link)
   - behind a "?": what the dot's colors mean
   - empty states: "Not analyzed yet. Analyze searches the web for its analyst ratings and news, and Claude reads
     what it finds." For a closed holding: "Not analyzed: the analysis runs on open holdings only."
3. **Trades**
   - the holding's warning again at the top, if any
   - a table: Date, Side (BUY/SELL), Qty, Price, Commission, Note, Investor (only with several investors), and per
     row edit and delete icons
   - an **Add trade** button that opens the trade modal
4. **By investor:** shown only when an investor other than the account owner holds or traded part of this holding
   - a table: Investor, Qty, Cost, Value, Unrealized P&L, %, Realized P&L, then a **Total** row equal to IB's
     figures for the holding

### 5.5 Closed (`/closed`)

- **Header:**
  - "Realized P&L" summed per currency ("+1,757.89 USD · +950.00 HKD"), plus "(1 to check)" in orange when a
    position sold more than it bought
  - with several investors, the same per investor under it: "Me +1,757.89 USD +950.00 HKD · Dana +0.00 USD"
- **+ Add closed position** opens a modal for a position sold before PortfolioBoss existed.
- **Table,** newest sale first:

  | Column | Notes |
  |---|---|
  | Symbol | + a "manual" tag (entered by hand), + a "partial · still holding 35" tag (sold in part, the rest still held) |
  | Investor | only with several investors |
  | Sector | |
  | Qty sold | |
  | Avg buy | average cost of the shares sold |
  | Avg sell | |
  | Commission | |
  | Realized P&L | signed, green/red, with its currency in the cell: "+242.89 USD"; "—" only when the position sold more than it bought (below) |
  | % | |
  | Bought / Sold / Held | |
  | Note | manual positions only, truncated, full text on hover |

- **A position that sold more than it bought** shows "—" as its P&L and, **always visible** (not on hover), an orange
  line under its row: "Sold 30 shares but bought 20 in this period: check its trades."
- **Clicking a row** opens the same kind of drawer:
  - **a holding's sold period:** its trades, read-only, with the hint "Correct them in Positions" linking to the
    holding's drawer
  - **a manual position:**
    - its details (symbol, currency, sector, note), editable
    - its trades: add, edit and delete. The last sell can be edited but not deleted; the server's message shows if
      someone tries.
    - the By-investor table
    - a **Delete position** button, confirmed with "Delete this position and its 3 trades?"

### 5.6 Investors (`/investors`)

- **With only the account owner,** an empty state that explains what this is for: "Someone else whose money is in
  this IB account. Their deposits and trades are entered for them; the account owner gets the rest of IB's
  figures." It has an **Add investor** button.
- **With several investors,** one card per investor, the account owner first:
  - the name, an "account owner" tag, a rename icon
  - a source line: "IB's figures, less the other investors'." for the owner, "From the deposits and trades entered
    for them." for the others
  - figures: **Deposited** (others only: deposits less withdrawals), **Cash**, **Shares**, **Total value**,
    **Unrealized P&L** (with %), **Realized P&L** (one line per currency), **Total P&L**
  - the investor's warnings in orange, e.g. "Negative cash: more was bought than deposited."
  - for the others, **Deposits (2)**, which opens their deposits and withdrawals: Date, Type (DEPOSIT /
    WITHDRAWAL), Amount, Note, edit/delete per row, and an **Add** button that opens a modal
- The section's promise, said once: "Together they always add up to IB's figures."

### 5.7 Modals

Every form shows the server's error text under it, as it comes (English, e.g. "quantity: must be greater than 0").
Save buttons read "Saving…" while saving.

- **Trade** (add / edit):
  - Investor: a select, only with several investors
  - Date: defaults to today, can't be in the future
  - BUY / SELL toggle
  - Quantity: a whole number greater than 0
  - Price: required (0 is a price)
  - Commission: optional; empty means the default (1¢ a share, at least $5 an order), explained on hover
  - Note: optional, placeholder "Why? (optional)"
  - Buttons: Save / Save changes, Cancel
  - For a holding with no trades yet, the form starts filled in as a BUY of the whole position at IB's average cost.
- **New closed position:**
  - Symbol, Currency, Sector, Note
  - Quantity
  - Buy: date, price, commission
  - Sell: date, price, commission
  - Investor (only with several investors)
  - The sell date can't be before the buy date.
- **Add investor / rename investor:** a name. A name already taken is refused by the server.
- **Deposit / withdrawal:** Date, Type, Amount, Note.
- **Confirm delete:** for a trade, a deposit, or a manual position.

---

## 6. Sample data (made up)

### Account

- Account U1234567, synced 2026-10-10 16:05
- Net liquidation 82,500.00 · Cash 12,407.80 · Positions value 70,092.20 · Unrealized P&L +4,428.00

### Open holdings (USD)

| Symbol | Sector | Qty | Avg cost | Last | Market value | Unrealized P&L | % | Bought | Held | Signal | M | A | N | Warning |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| MSFT | Technology | 40 (20 · 20) | 412.30 | 468.15 | 18,726.00 | +2,234.00 | +13.55% | 2025-03-14 | 1y 7m | GREEN | STRONG 5/5 | IMPROVING | POSITIVE | — |
| AMZN | Consumer | 60 (35 · 25) | 182.75 | 214.40 | 12,864.00 | +1,899.00 | +17.32% | 2025-06-02 | 1y 4m | YELLOW | NEUTRAL 3/5 | STABLE | NEGATIVE | — |
| ASML | Semiconductors | 12 | 905.10 | 742.60 | 8,911.20 | −1,950.00 | −17.95% | 2025-11-20 | 10m 24d | RED | WEAK 1/5 | DETERIORATING | NEUTRAL | — |
| COST | Consumer staples | 15 | 880.00 | 935.20 | 14,028.00 | +828.00 | +6.27% | 2026-01-08 | 9m 5d | GREEN | NEUTRAL 2/5 | unknown (no targets) | POSITIVE | — |
| V | Financials | 25 | 268.40 | 301.80 | 7,545.00 | +835.00 | +12.44% | 2025-08-01 | 1y 2m | GREEN | STRONG 4/5 | STABLE | NEUTRAL | — |
| PLTR | Technology | 100 | 24.80 | 21.35 | 2,135.00 | −345.00 | −13.91% | 2026-07-01 | 3m 11d | YELLOW | WEAK 1/5 | STABLE | NEUTRAL | real: "The trades entered add up to 80 shares; IB reports 100." |
| GOOGL | — (empty: "+ add") | 30 | 165.20 | 196.10 | 5,883.00 | +927.00 | +18.70% | — | — | none (never analyzed) | STRONG 4/5 | — | — | minor: "No buy entered yet, so the buy date and holding period are unknown." |

### Closed holding (shown only with "Show closed")

| Symbol | Sector | Qty | Status | Warning |
|---|---|---|---|---|
| DIS | Communication | 0 | closed | real: "IB no longer reports this holding, but no sell closing it was entered." |

### Analysis of MSFT (for the drawer)

- Analyzed 2026-10-09 10:52 by claude-sonnet-5-5
- Consensus: **STRONG BUY** · 48 analysts · target 545.20 (+16.46%) · marketbeat.com · copy of 2026-10-02
- Targets 528.00–561.30 across 4 sources:
  - marketbeat.com (copy of 2026-10-02): STRONG BUY, 48 analysts, target 545.20
  - stockanalysis.com (copy of 2026-09-18): BUY, 41 analysts, target 528.00
  - financhill.com (copy of 2026-10-04): BUY, 50 analysts, target 561.30
  - tipranks.com (copy date unknown): STRONG BUY, 35 analysts, target 549.90
- Trend **IMPROVING** · 3 targets raised, 1 lowered
- Actions (the firms are fictional):
  - 2026-10-07 · Northgate Securities · TARGET RAISED · Buy · target 520.00 → 560.00 · example-news.com · copy of 2026-10-07
  - 2026-10-03 · Harbor Capital · UPGRADED · Hold → Buy · target 500.00 → 540.00 · marketbeat.com · copy of 2026-10-04
  - 2026-09-29 · Lakeview Research · TARGET LOWERED · Overweight · target 575.00 → 550.00 · example-wire.com · copy of 2026-09-30
  - 2026-09-24 · Summit Partners · RATING KEPT · Outperform · target 530.00 → 545.00 · marketbeat.com · copy of 2026-09-25
  - 2026-09-18 · Crescent Bank · NEW COVERAGE · Buy · target 555.00 · example-news.com · copy date unknown
- News **POSITIVE**: "Cloud revenue beat expectations and the company raised its guidance."
  - 2026-10-08 · Example Wire · Microsoft's cloud unit grows faster than expected
  - 2026-10-06 · Market Daily · Analysts lift targets ahead of earnings
  - 2026-10-04 · Example News · Microsoft expands its data center build-out in Europe

### Trades of AMZN (for the Trades tab)

| Date | Side | Qty | Price | Commission | Note | Investor |
|---|---|---|---|---|---|---|
| 2025-06-02 | BUY | 45 | 180.10 | 5.00 | Long-term retail + cloud thesis | Me |
| 2025-06-02 | BUY | 25 | 185.00 | 5.00 | | Dana |
| 2026-08-14 | SELL | 10 | 205.00 | 5.00 | Trimmed after the run-up | Me |

### Closed positions

| Symbol | Tags | Investor | Sector | Qty sold | Avg buy | Avg sell | Commission | Realized P&L | % | Bought | Sold | Held | Note |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| NKE | — | Me | Consumer | 50 | 98.40 | 76.10 | 10.00 | −1,125.00 USD | −22.87% | 2025-02-10 | 2026-09-02 | 1y 6m | |
| AMZN | partial · still holding 35 | Me | Consumer | 10 | 180.10 | 205.00 | 6.11 | +242.89 USD | +13.49% | 2025-06-02 | 2026-08-14 | 1y 2m | |
| TSLA | manual | Me | Auto | 20 | 210.00 | 342.50 | 10.00 | +2,640.00 USD | +62.86% | 2024-11-04 | 2026-06-18 | 1y 7m | Sold before PortfolioBoss |
| 9988 | manual | Me | Consumer | 400 | 78.20 | 80.80 | 90.00 | +950.00 HKD | +3.04% | 2025-01-15 | 2025-12-01 | 10m 20d | Hong Kong listing |
| SNAP | — | Me | Communication | 30 | 9.80 | 11.40 | 10.00 | — | — | 2026-03-02 | 2026-05-20 | 2m 19d | |

The SNAP row has the always-visible orange line: "Sold 30 shares but bought 20 in this period: check its trades."

Totals: **+1,757.89 USD · +950.00 HKD (1 to check)**

### Investors

| | Me (account owner) | Dana |
|---|---|---|
| Source line | IB's figures, less the other investors'. | From the deposits and trades entered for them. |
| Deposited | — | 20,000.00 |
| Cash | 5,342.80 | 7,065.00 |
| Shares | 55,369.20 | 14,723.00 |
| Total value | 60,712.00 | 21,788.00 |
| Unrealized P&L | +2,640.00 (+5.01%) | +1,788.00 (+13.82%) |
| Realized P&L | +1,757.89 USD, +950.00 HKD | +0.00 USD |
| Total P&L | +4,397.89 | +1,788.00 |
| Warnings | — | — |
| Deposits | — | 2025-06-01 DEPOSIT 15,000.00 "Initial"; 2026-02-10 DEPOSIT 5,000.00 |

### Edge cases to design for

- A figure that is unknown ("—") in a money column, a P&L column and a date column.
- A holding with no sector, no trades and no analysis (GOOGL above).
- A holding analyzed with no trend (COST: "no price targets among the analyst actions").
- An analysis with **no consensus** ("No analyst consensus in the search results.") and no headlines.
- A momentum score with an unknown check ("?/5": fewer than 200 daily closes).
- A long sector name, a long note, a long headline.
- One investor only (no Investor columns, no "(20 · 20)" in Qty, no investors strip) **and** several.
- The Analyze button in each state: idle, running, done, done with a failure, error.
- The API unreachable (the error banner) and the first load (nothing to show yet).

---

## 7. Second round: the weights

Since the first round, three things were built in the UI from the first round's tokens, without a design pass:
- a bar of **the stocks against the cash** under the account's figures
- two **weight graphs** on the Overview: the money by sector (a ring) and by holding (bars)
- a **Weight tab** in the holding drawer that works out how many shares to sell or buy for a target weight

With several investors, the graphs and the Weight tab both have a row of tabs to weigh one investor's own money.
**Design them as components of the same system.** Section 2's rules still hold:
- no new figure (everything is worked out in the UI from what the API already sends)
- no change in behavior
- the Weight tab only calculates: it sends nothing anywhere and saves nothing (PortfolioBoss never places an order)

### 7.1 What a weight is

- **"The whole account"** is the stocks weighed plus the cash. It is not the net liquidation, which also counts
  accrued interest and dividends (a hair more), so the holdings and the cash always add up to exactly 100%.
- **Two bases**, each graph naming its own in a caption:
  - the sector ring is a share **of the money in stocks**
  - the holding bars, the cash and the Weight tab are shares **of the whole account**
- Only holdings in the account's currency (USD) are weighed: the API has no exchange rate yet. A holding in another
  currency, with no market value from IB, or worth 0 or less is left out and named under the graphs.
- A weight has **one decimal**: "22.7%". Money as in §3.

### 7.2 Whose weights: the tabs

- **Only with several investors:** Portfolio · Me · Dana, the account owner first. With one investor there is no
  row at all.
- **Today:** the same segmented control as the forms' BUY / SELL toggle, above the two graphs and again at the top of
  the Weight tab.
- **Portfolio** weighs the whole account: IB's quantities, values and average cost, and IB's cash.
- **An investor's tab** weighs only that investor's money:
  - their shares of each holding they hold some of, valued at IB's price, at their own average cost
  - their own cash: the Cash on their card
  - The investors' tabs add up to Portfolio's.
- **The wording follows the tab:**

  | On Portfolio | On Dana's tab |
  |---|---|
  | the whole account | all of Dana's money |
  | the money in stocks | Dana's money in stocks |
  | the stocks plus the cash | Dana's shares plus Dana's cash |
  | IB's average cost | the average cost of Dana's shares |

### 7.3 Overview: the stocks and the cash

- **Where:** at the bottom of the Account summary card, under its four figures, as a thin full-width strip.
  - It always shows the whole account: there are no tabs here.
- **What:** one bar, with the stocks filling from the left in the accent color and the cash from the right in the
  soft accent with an accent outline. Each part is named at its end: "Stocks **85.0%**" … "Cash **15.0%**".
- **Cash below zero:** the bar gives way to the line "The cash is below zero: part of the stocks is bought with
  borrowed money."
- **Cash unknown:** nothing is shown.

### 7.4 Overview: By sector and By holding

**Where:** under Needs attention and Signal at a glance comes the tab row, then two cards side by side, each half the
page's width.

**By sector**, caption "Share of the money in stocks":
- **The ring and its legend:**
  - a ring (hand-drawn SVG, no chart library) with a legend beside it
  - a legend row: swatch · sector · percent · value
- **Colors:**
  - the four heaviest sectors are colored `chart-1` … `chart-4` in that order
  - the rest are folded into a grey **"Other (n sectors)"** (`chart-rest`)
  - **"No sector"** comes last, outlined grey like the signal's empty ring. A sector not entered yet is unknown, not a
    sector of its own.
- **The ring's hole** shows the number of sectors ("5 sectors").
- **Hover:**
  - pointing at a slice or at its legend row highlights both, and the hole then shows that slice's percent and name
  - each row's hint: "2 holdings · 25.3% of the whole account"
  - Other's hint names its sectors
  - No sector's hint: "1 holding without a sector yet: add it in Positions · 7.1% of the whole account"

**By holding**, caption "Share of the whole account":
- **The rows:**
  - one row per holding, heaviest first: symbol · bar (accent) · percent · value
  - the longest bar spans the whole track, and the others are drawn to the same scale
- **Past 10 holdings:** the lightest share one grey row, "3 more", which opens Positions. Its hint names them.
- **Cash:** under a hairline, the last row, in the same cash color as in 7.3.
  - It opens nothing.
  - Its hint says what the cash is: "The cash in the account, as IB reported it"; on Dana's tab, "Dana's cash:
    deposits less withdrawals, less buys and plus sells, commissions off"; on the owner's tab, "Me's cash: IB's cash
    less the other investors'".
  - Cash below zero draws no bar and shows its negative percent.
- **A holding's row** opens its drawer **on the Weight tab, on the same tab**: Dana's MSFT bar opens Weight → Dana. Its
  hint: "26.7% of the money in stocks".

**Around the cards:**
- **Under the cards**, only when something is left out: "Not in the graphs: 0700 (in HKD)." The other reasons are "no
  market value from IB" and "a market value of 0 or less".
- **An investor's tab with nothing to weigh** shows one card in place of the two: "Dana holds no stocks the graphs can
  weigh · cash 2,000.00".
- **Hints:** every hint here is the browser's own tooltip (`title`) today.

### 7.5 The holding drawer: the Weight tab

The tab is shown for open holdings only. The drawer's tabs are Summary · Momentum · Analysts & news · Trades (3) ·
**Weight** · By investor.

**From the top:**
1. **The tab row** (7.2). The target typed in stays when the tab changes, to compare.
2. **Now:** "Now **22.7%** of the whole account · 18,726.00 of 82,500.00".
3. **Target weight:** a short number field followed by "%", with the hint "Of the whole account: the stocks plus the
   cash."
4. **The result:**
   - nothing typed: a dashed box, "Type a target weight to see how many shares to sell or buy."
   - a sale or a purchase: a box headed **"Sell 14 shares"**, with "≈ 6,554.10" on the right, then rows:
     - **After the sale** (or **After the purchase**): "26 shares · 14.8% of the whole account"
     - **Commission:** "≈ 5.00"
     - for a sale, **Realized P&L:** "≈ +776.90" (green or red), then "at IB's average cost, the commission off"
     - for a purchase, **Cash:** "12,407.80: enough"
5. **A footer line:** "At IB's last price, 468.15, from the sync of 2026-10-10 16:05. A calculation only: nothing is
   sold or bought."

**The rules** (unchanged; for the designer's understanding):
- **Shares:** a sale rounds the shares **up**, so it lands at the target or just under it, and never sells more than
  every share. A purchase rounds **down**.
- **The commission** is the default: 1¢ a share, at least $5.
- **The realized P&L** is at the tab's average cost, the commission off.
- **The whole account keeps its size:** a sale turns shares into cash, and a purchase turns cash into shares.
- **On an investor's tab for a holding they hold none of,** it starts from 0 shares, and only buying is worked out.

**Its other states:**
- **A target that can't be used** shows a form error: "Type the target as a number, like 15 or 12.5." or "A weight is
  between 0% and 100%."
- **Already at the target:** "Already at the target: no whole share brings it closer to 2.6%."
- **A purchase the cash can't pay for:** the Cash row turns orange with the real-warning triangle: "7,065.00: less
  than the purchase and its commission". It is still shown, and nothing is blocked.
- **Can't calculate:** one line in place of the whole calculator:
  - "This holding is in HKD: the calculator works in USD only for now."
  - "IB reported no price for this holding."
  - "This is not a long position." On an investor's tab: "Dana's part of this holding is below zero: check its
    trades."
  - "No weight can be worked out: the whole account is unknown (IB reported no cash) or not above zero."

### 7.6 Sample data (made up, the same account as §6)

**Whole accounts:**
- **Portfolio:** stocks 70,092.20 + cash 12,407.80 = **82,500.00**
- **Me:** shares 55,369.20 + cash 5,342.80 = **60,712.00**
- **Dana:** shares 14,723.00 + cash 7,065.00 = **21,788.00**

**The stocks against the cash** (Account summary): Stocks 85.0% · Cash 15.0%.

**By holding** (share of each tab's whole; a "—" holding is not on that tab):

| Row | Portfolio | Me | Dana |
|---|---|---|---|
| MSFT | 18,726.00 · 22.7% | 9,363.00 · 15.4% | 9,363.00 · 43.0% |
| COST | 14,028.00 · 17.0% | 14,028.00 · 23.1% | — |
| AMZN | 12,864.00 · 15.6% | 7,504.00 · 12.4% | 5,360.00 · 24.6% |
| ASML | 8,911.20 · 10.8% | 8,911.20 · 14.7% | — |
| V | 7,545.00 · 9.1% | 7,545.00 · 12.4% | — |
| GOOGL | 5,883.00 · 7.1% | 5,883.00 · 9.7% | — |
| PLTR | 2,135.00 · 2.6% | 2,135.00 · 3.5% | — |
| Cash | 12,407.80 · 15.0% | 5,342.80 · 8.8% | 7,065.00 · 32.4% |

**By sector, Portfolio** (the hole reads "5 sectors"):

| Slice | Holdings | Value | Of the money in stocks | Of the whole account (hint) |
|---|---|---|---|---|
| Technology (`chart-1`) | MSFT, PLTR | 20,861.00 | 29.8% | 25.3% |
| Consumer staples (`chart-2`) | COST | 14,028.00 | 20.0% | 17.0% |
| Consumer (`chart-3`) | AMZN | 12,864.00 | 18.4% | 15.6% |
| Semiconductors (`chart-4`) | ASML | 8,911.20 | 12.7% | 10.8% |
| Other (1 sector), grey | V (Financials) | 7,545.00 | 10.8% | 9.1% |
| No sector, outlined | GOOGL | 5,883.00 | 8.4% | 7.1% |

**By sector, Dana's tab** (the hole reads "2 sectors"):
- Technology (MSFT): 9,363.00 · 63.6%, with 43.0% of all of Dana's money in its hint
- Consumer (AMZN): 5,360.00 · 36.4%, with 24.6% in its hint

**The Weight tab:**

| Holding, tab | Now | Target | Result |
|---|---|---|---|
| MSFT, Portfolio | 22.7% · 18,726.00 of 82,500.00 | 15 | Sell 14 shares ≈ 6,554.10 · after: 26 shares, 14.8% · commission ≈ 5.00 · realized P&L ≈ +776.90 at IB's average cost (412.30) |
| V, Portfolio | 9.1% · 7,545.00 of 82,500.00 | 12 | Buy 7 shares ≈ 2,112.60 · after: 32 shares, 11.7% · commission ≈ 5.00 · cash 12,407.80: enough |
| MSFT, Dana | 43.0% · 9,363.00 of 21,788.00 | 30 | Sell 7 shares ≈ 3,277.05 · after: 13 shares, 27.9% · commission ≈ 5.00 · realized P&L ≈ +365.30 at the average cost of Dana's shares (415.25) |
| ASML, Dana (holds none) | 0.0% · 0.00 of 21,788.00 | 40 | Buy 11 shares ≈ 8,168.60 · after: 11 shares, 37.5% · commission ≈ 5.00 · cash 7,065.00: less than the purchase and its commission (orange) |
| PLTR, Portfolio | 2.6% · 2,135.00 of 82,500.00 | 2.6 | Already at the target: no whole share brings it closer to 2.6%. |

### 7.7 What to design

- **The pieces, with their states:**
  - the stocks-and-cash bar (7.3)
  - the By sector card (7.4)
  - the By holding card (7.4)
  - the tab row (7.2)
  - the Weight tab (7.5)
- **Colors to keep:**
  - **Sector colors:** `chart-1` … `chart-4` and `chart-rest` are the one set of colors that only tells things
    apart.
  - **Colors they can't use:** none may be green, red, yellow or orange, which already mean something (§4).
  - **Order in the ring:** blue and violet, the pair hardest to tell apart, sit 2nd and 4th, so they never touch. A
    color-blindness palette check confirmed this; check again after a change.
  - **Stocks and cash** keep one color each wherever they appear side by side: the accent for the stocks, the soft
    accent with an accent outline for the cash.
- **What is open:** the designer may propose a better arrangement. For example:
  - where the tab row sits
  - how the hints show (the system's own tooltip or popover in place of the browser's)
  - how the two cards balance when one is much taller than the other

  The rule stays: no figure the API doesn't already provide, and nothing sent anywhere.
- **Edge cases:**
  - one investor (no tabs) and several
  - more than 4 sectors and more than 10 holdings
  - a holding with no sector
  - a holding left out of the graphs (another currency)
  - cash below zero and cash unknown
  - an investor's tab with nothing to weigh
  - every state of the Weight tab in 7.5
