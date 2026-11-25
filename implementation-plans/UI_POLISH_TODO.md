# UI polish — plan of record

The UI gets a polish of its small details, for a calmer, clearer experience — **not a redesign**: no new feature, no new data, no change in behavior. The design was
done in Claude Design's sixth round (DESIGN_BRIEF.md §11, his notes and decisions in §11.6); this file is how it is
built, stage by stage. Written 2026-11-24, after stages א and ב, so that a new conversation can
pick it up.

## Working rules (the user's)

- Reply to him in Hebrew; code, comments and the app's texts stay English.
- Say what will change and why **before** editing; give a browser check after each stage as click-by-click steps with
  the exact texts to expect.
- **No commit by Claude** unless he asks (he commits with `/commit`). Everything below is uncommitted as of 2026-11-24.
- Never real portfolio data in a screenshot: work on the demo database only.
- Build from the design system's specs (README + CSS), not its preview code. Use the app's own roles and tokens.

## Where things are

- **The design:** the Claude Design design-system artifact `https://claude.ai/artifact/9YzKiGRVmxN49NmK6xK8D2` — read it
  with the Artifact tool (`action: "read"`, `paths: [...]`):
  - `project/Polish.md` — every finding of round 6 and its part 2, by screen (T1–T6, O1–O5, P1–P4, D1–D6, A1–A7,
    R1–R13, X1–X4, L1–L2, C1–C4, E1–E3, K1–K2, I1–I3, M1–M3, CD1–CD5, AS1–AS4), each marked "In the system" or "Build";
  - `project/components/<Name>/README.md` — each component's spec (`Motion`, `FirstLoad`, `AlertsPanel`, `AlertSettings`,
    `Hint`, `HotTable`, `ClosedTable`, `ClosedDrawer`, `InvestorCard`, `DaySales`, `LotTable`, `RecPick`,
    `CandidateDrawer`, `TaxList`, `TaxYearCard`, `TradeModal`, `ColumnsMenu`, `SignalGlance`, `WeightsSection`, …);
  - `project/components/bundle.css` — the exact CSS; the round-6 rules are near the end ("§11 Round 6, part 2: rules for
    every screen"); `project/components/bundle.js` — the components' code, to read what a README leaves out;
  - `project/tokens.json` — the tokens (`content-max-wide`, the retired `status-*`).
- **His notes as rules for every screen:** DESIGN_BRIEF.md §11.6 — no messy rows, no generic pastel tags, no table that
  scrolls sideways at 1280–1440, Positions' Sector second and one P&L column, every date dd/mm/yyyy, labels before
  values.
- **The demo database** `portfolioboss_demo`: `scripts/demo/create-demo-db.sh` + `demo-data.sql` (made-up positions of
  real US stocks on the real database's market data; it reads the real database read-only and checks its row counts).
  Account `U1234567`, investors Omri and Dana. No SNDK: its analysts' target counts the targets of before its 19.11
  split (+2708% on screen) — a real bug of the app, told to him, not fixed and not part of this plan.
- **The tools** in `scripts/demo/` (`npm install` there once — `playwright-core`, which drives the installed Chrome):
  - `node take-screenshots.mjs <folder>` — 36 screenshots of every page, drawer, modal and state at 1440 × 900 @2×
    (`ONLY=<part of a name>` for some); every request that writes is blocked except Sync now;
  - `node find-iso-dates.mjs` — every screen checked for a date still written yyyy-MM-dd;
  - `node all-columns.mjs <folder>` — Positions with every column on at 1280 and 1440: "fits" or "SCROLLS".
- **Screenshots sent to Claude Design:** `~/Desktop/PortfolioBoss-round-11/` (34, before the polish). Screenshots 13–19
  (NVDA's other drawer tabs, By investor, WMT never analyzed) never reached it — asked him to send them again; if its
  findings come back, add them to stage ו.

## Running

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/portfolioboss_demo ./run.sh 7599
```

Port 7599: no TWS there, so Sync now fails (its error band is a screen to check) and the real account never reaches the
demo database. Don't press Analyze (paid). A change to `ui/tailwind.config.js` (keyframes, a color) reaches the dev
server only after stopping the app and running it again. Checks after a stage: `cd ui && npm run build`, then the tools
above, then look at the screenshots.

## Done

### Stage א — the shell and the dates (built, browser-checked by him 2026-11-24)

- `components/topBar/` — `TopBar.tsx` (bell left of the name — his place —, its count 7px out with a white ring; the
  `BrandMark` "P"; seven tabs, "Averaging" short, never wrapping; the sync in words in a 168px slot with its `Hint`:
  "From TWS, 22/11/2026 19:10 · account U1234567"; states Loading… / Syncing with TWS… / Sync failed / Not connected; no
  load dot), `BrandMark.tsx`, `syncSentence.ts`, `useCurrentMinute.ts`; `public/favicon.svg`.
- `components/Hint.tsx` — the one hover explanation (Radix Tooltip, 400ms, at once on keyboard focus); used in the bar
  only so far.
- `hooks/useDocumentTitle.ts` — "NVDA · Positions · PortfolioBoss", called by every page.
- `AppShell.tsx` — the white error band (icon in the loss tone, secondary button), the footer "Read-only: PortfolioBoss
  never places an order with Interactive Brokers.", `max-w-content` = `clamp(1200px, 80vw, 1536px)`; `status-*` colors
  removed.
- `PageHeader.tsx` — every page has a line under its title.
- Dates: `formatDate` / `formatDateTime` give dd/mm/yyyy (`formatDayOf`, `formatTimeOf`), ~20 dates shown raw wrapped,
  `DateInput` typed as dd/mm/yyyy (`isoDateFromTypedDate`), the forms' messages say dd/mm/yyyy.

### Stage ב — words instead of tags, the momentum mark, Positions, the drawer (built, browser-checked 2026-11-24)

- `CHIP_CLASS` is a verdict word (bold, tone color, sentence case through `lowercase first-letter:uppercase` on an
  `inline-block`); `labelClassOf` gives text colors only, `fillClassOf` the fills.
- `Momentum.tsx` — `MomentumMark` (five rising bars + the word, `showScore` in the Momentum tab); `MomentumChip` gone.
- `Signal.tsx` — `SignalWords`, the one form of the signal in words (Positions, Needs attention, the drawer, Averaging);
  `SignalCell` wraps in 132–160px, without the score.
- Company risks (his name; "Signal" for the dot's three) — `RiskSignTag` is words (name 600 + figure), `RiskSigns`
  "No company risks" / "Not checked", "·" between; `LabeledValue.tsx`; `HoldingWarningSigns.tsx` now
  `HoldingCompanyRisks` + `HoldingSignsAndRisks`; every "Risk signs" text says "Company risks".
- The analysts' upside is grey everywhere (his OK). `COUNT_CHIP_CLASS` is a plain figure.
- `DrawerShell` focuses the drawer itself; `DrawerFrame` 4/16/20 rhythm, flush tabs, equal key figures, no "USD" after
  a US symbol; the holding drawer's line "Signal … · Company risks …".
- `PositionsTable.tsx` rewritten: Symbol, Sector, Signal, Momentum, Qty (split under it, only investors holding some),
  Market value (no USD), "$ Unrealized P&L / Unrealized P&L %" in one column, Held; optional Avg cost · Last, Analysts'
  target · Upside, Earnings, Bought · Last sold (`TwoFigures`); 6px cells, 56px rows, sticky Symbol as a fallback; fits
  1280 with all twelve (1134px). `ColumnsMenu`'s storage key is `.v2`.
- Averaging: `WhatToKnowLine` — five stacked slots (Signal · Company risks · Momentum · Analysts' target · Earnings);
  no accent bar on a planned row (his OK).

### Stage ג — motion and the first load (built, browser-checked by him 2026-11-25)

- **Motion** — a "Motion" block in `index.css` (his OK: plain CSS, not `tailwind.config.js` — the exits on `data-state`
  and reduce motion read in order there, and no restart is needed): `--motion-quick/base/slow` 120/180/240ms, their exits
  `--motion-quick-exit/base-exit/slow-exit` 80/120/160ms (named here; raw numbers in the design), `--ease-enter/exit`;
  the classes `drawer-motion`, `drawer-veil-motion`, `modal-motion`, `modal-veil-motion`, `popover-motion` (every
  popover, the Columns menu, the alerts panel), `hint-motion` (`Hint`, `HelpOnHover`: in only), `fade-in-motion`,
  `turning-icon` (every spinner, 1.2s; Sync now's and Scan again's ↻ itself turns — his OK); one reduce-motion block.
- `hooks/animation/useCloseAfterExitAnimation.ts` (his folder): the drawer and the modal stay rendered with
  `open={false}` while Radix plays their exit, then call `onClose` on their own `animationend`.
- `Modal`'s `onSubmit` answers whether it saved, and the modal closes itself with the exit: the four forms and
  `ConfirmDialog` no longer call `onClose`. Centred by `inset-x-0 mx-auto` — the animation's transform overrode
  `-translate-x-1/2`. The alert settings' Save still closes at once: stage ד rebuilds that drawer.
- **The drawer's shell moved to the pages** (Positions, Closed, Recommendations): `DrawerFrame` is only the inside now
  (`navigation: DrawerNavigation | null` for ‹ ›, `title: ReactNode`), each drawer exports its label
  (`holdingDrawerLabelOf`, `closedDrawerLabelOf`, `candidateDrawerLabelOf`). ‹ › change what is inside one shell, so
  the header changes at once and the panel fades in; a tab's panel is keyed by the tab and fades in.
- **The first load** — `components/firstLoad/SkeletonBar.tsx` (`SkeletonBar`, `SkeletonDot`); each skeleton beside its
  card, on the card's own pieces: `AccountSummarySkeleton` (+ `StocksAndCashBarSkeleton`), `NeedsAttentionSkeleton`,
  `SignalGlanceSkeleton` (the four colours' names shown: words known before the data), `WeightGraphsSkeleton`
  (`SectorWeightsCardSkeleton` with `DonutChartSkeleton` — the real ring in grey —, `HoldingWeightsCardSkeleton`),
  `InvestorsStripSkeleton`, `PositionsTableSkeleton` (the same head, `barWidthsWhileLoading` in each column's entry, rows
  of the real 56px), `HoldingDrawerSkeleton` (the symbol a bar: the address holds only the id). `OverviewPage` and
  `PositionsPage` take `snapshot | null`; the other five pages show nothing under the bar until the data comes (his OK).
  The column fades in once when the data comes (`AppShell`). `EmptyBox` is a plain card, not dashed.
- Checked: `npm run build`; a one-off Playwright check (30 of 30: enter and exit animations, the address changing only
  after the exit, ‹ › keeping one shell, the modal centred, reduce motion, the drawer keeping its shell when the data
  comes; a save answered "204" in the browser — the demo database untouched — closes the modal with `modal-out`);
  `take-screenshots.mjs` (36 now: `first-load-positions`, `first-load-holding-drawer` added).
- Found from stage ב and fixed (his OK): a drawer opened from its address drew the focus ring around itself (it takes
  the focus); `DrawerShell` keeps its own shadow while focused (`focus-visible:shadow-raised`), its controls keep theirs.
- Steps 1–8 of the browser check passed (his, 2026-11-25). For the first load, Safari has no simple slow-network switch:
  a 12-second recording of it (frames from Playwright joined by Homebrew's ffmpeg — Playwright's own recording wants a download
  of its ffmpeg) stands in.

### Stage ד — the alerts (built, browser-checked by him 2026-11-25)

- **The panel** — `components/alerts/AlertsPanel.tsx` (in place of `AlertList.tsx`), `alertGroups.ts`, `alertTime.ts`:
  400px, its left edge on the bell's, 8px under the bar (`PANEL_OFFSET_UNDER_THE_BELL` in `AlertsBell`: the bell's
  bottom is 12px above the bar's), at most `min(600px, 100vh − 80px)`, only the groups scroll, their headings sticky;
  "Alerts · 36 new · ⚙"; "Your holdings" then "The short list", each with its count, 5 rows then "Show 22 more" in place;
  a row of two lines (symbol and "17:15" / "Yesterday" / the date, then the message), new with the accent dot, seen
  muted; empty: `BellOff` and "No alerts yet. They are checked on every sync."
- **The group is the server's** (the user's rule: "a stock in my holdings shows its alert first of all in my
  holdings"): `model.alerts.AlertGroup`, `calculation.alerts.AlertGroupCalculator(alertType, stockIsHeldNow).group()` —
  `SHORT_LIST` only for `ENTERED_SHORT_LIST` of a stock with no open holding —, `AlertResponse.group` (appended),
  `AlertsReadService` (the open holdings' contracts; a closed holding is held no more). Tests: `AlertGroupCalculatorTest`,
  `AlertsReadServiceTest` (a held stock, one not held, one sold), `AlertControllerTest`.
- **The messages' words** (his OK): "Momentum turned weak", "the analysts’ target", "Reports on 30/11/2026"
  (`HoldingAlertsCalculator.DATE_AS_THE_SCREENS_WRITE_IT`; the subject stays the ISO date — it is an open alert's key).
  An alert stored before keeps its words; on the demo database the 36 were deleted (his OK) and made again by a check
  (`PUT /api/alerts/settings` with the settings as they were — the body is `{settings: [...]}`, whole numbers).
- **The settings** — `AlertSettingsDrawer.tsx` (`AlertSettingsForm`, `AlertKindRow`, `ThresholdField`): `DrawerShell`
  `width="narrow"` (440px) and `useCloseDrawer()` (a context: Save and Cancel leave with the exit, as promised in stage
  ג); "Checked on every sync · 9 of 9 on" counted live; sentence-case headings, hairline lists; `SwitchNamedBy` in
  `Switch.tsx` (named by the sentence, which holds the field); the field 52 × 26 inside the sentence
  (`INPUT_FRAME_CLASS`, `formInput.ts`), the unit in the words after it (`alertTexts.ts`); "Default: 7 days" under a
  changed number (`defaultThresholdText`); a kind off muted, its field disabled keeping its number.
- Checked: 769 Java tests, `npm run build`, screenshots, and a Playwright check of the interactions (12 of 12; the save
  answered "204" in the browser).
- Also (his note): the Averaging cash card says "Not enough cash: … — 96.15 more than the cash of 15,000.00." in place
  of "96.15 short" — "short" reads as selling short (`costsMoreThanTheCash`).
- After his first look: the short-list message shortened to the design's "Entered the short list: hot, target 31.8%
  above the price" (one line of the panel; the demo alerts made again); and **the top bar stays on screen** (his ask —
  scrolled down, the drawer under a bar that had scrolled away looked "hanging in the air"): `TopBar` `sticky top-0
  z-30`, `html { scroll-padding-top }` so that what the keyboard reaches stops under it, and the Averaging cash card
  sticks 8px under the bar (`top-[calc(theme(spacing.topbar)_+_8px)]`). Checked in Chrome: the bar at 0 after a
  scroll, the cash card at 64px under a 57px bar.

## Next

### Stage ה — one way to explain on hover

- `Hint` README: `components/Hint.tsx` (built) everywhere in place of the browser's `title` (most of the app), the
  weights card's `Tip` and the Taxes page's `HelpOnHover`. One sentence about one thing → a `Hint` on the thing (a dotted
  underline only for a term in running text; an icon button keeps its `aria-label` and gets a `Hint` with the same
  words); two sentences or more, a list, how a heading's figures are worked out → a "?" (`HelpPopover`) 4px after the
  heading, never at a card's far edge (O3, R4). The drawer's ‹ › ✕ and Sync now get `Hint`s. List the `title=` uses
  first (`grep`) and go page by page. `@radix-ui/react-tooltip` stays pinned at 1.2.16.

### Stage ו — the rest of the findings

- **Recommendations' Hot table** (his note 1; HotTable README; R1–R4): one-line heads, no second head row; Pulled back
  and Moves together as small icons beside their figures with a `Hint`; no "In the portfolio" column — a small "held"
  pill by the symbol; the line under an analyzed row as four labeled slots on a grid (Company risks · Why it moves ·
  Analysts · Earnings); rows 12px top and bottom, the line 6px under, 13/18; IB's company name 12/16 faint, a trailing "/"
  trimmed; a pick's lone "—" → "Company risks not checked" / "No company risks".
- **The scanned stock's drawer** (R5–R13): "Hot list" in words, not a pill; a date never breaks; the pulled-back mark
  beside its figure; the head's "Company risks" label; `MomentumMark` with its score and the "?" after the line; the
  Company tab's label column 200px; no dashed empty box in If I buy and the Weight tab — a plain muted line.
- **Closed** (C2–C4, CD1–CD3): Sector second; Avg buy over Avg sell, Realized P&L over %, Bought over Sold; no "USD"; the
  note wraps (260px); fits 1280 — check like `all-columns.mjs`; "partial" a small pill with "40 shares still held" in its
  `Hint`; the drawer's line as labeled pairs ("**Investor** Omri · **Bought** 02/02/2026 · …"), no "· USD".
- **Investors** (I1–I3): "account owner" as words after the name (12px muted), no "USD" on any row, ’ not '.
- **Forms** (M2, M3, CD4, CD5): the note's placeholder "Why?"; helper lines 12/16 faint; a disabled primary on `subtle`
  with faint text; the Delete block's frame in `border`, only its button red.
- **Taxes** (X1, X2, X4): the Check column aligned right under the month's controls; an opened day in one frame, the
  buys' table with only a hairline above its head; "1 of 1 day confirmed".
- **Overview** (O3, O4): the "?" of Signal at a glance 4px after its title; the Weights card 5fr : 7fr, the ring (168px)
  centered above its legend.
- **Small** (A1, E3, K1, T5): the averaging cash card's shadow only while it sticks; Sync now back to its rest look after
  failing; the Columns menu focuses its first item, no ring on the popover; every "Loading..." is "Loading…".

## After the polish

1. **Screenshots again** (`take-screenshots.mjs`) and a last look together; then commit when he asks.
