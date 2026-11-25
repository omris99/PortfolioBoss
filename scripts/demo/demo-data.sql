-- The made-up portfolio of the demo database. create-demo-db.sh runs it inside portfolioboss_demo, in one transaction,
-- after it copied the market data. Real US stocks with made-up quantities and days; a trade's price is the stock's real
-- close on its day (only two older trades, before the year of stored closes, have a plausible price typed in), so each
-- profit and loss is the market's. The account, the cash, the investors and every trade are invented.
--
-- What it gives the screens:
--   - 10 open holdings, the portfolio about 40% up; NVDA and AAPL the heaviest (NVDA above the 15% weight limit), and
--     WMT, TTWO and ZS a little below their average cost (the Averaging calculator); WMT, ZS and MRNA never analyzed
--     (Analyze), so Finnhub has no answer for them either. No SNDK: it split 1 for 8 on 19.11.2026, and the analysts'
--     target still counts the targets of before the split — +2708% on screen;
--   - U and MRNA sold in part, DOCU and RBLX in full (RBLX at a loss), and MSFT a manual position of 2024–2025;
--   - two investors: Omri, the account owner, and Dana, with her deposits and her own trades;
--   - IB's Flex trades of the same trades, so that the Taxes page has 2025 and 2026.

-- When the demo was "synced": just after the latest scan copied (Sunday 22.11.2026, 19:02), whose closes end on Friday
-- 20.11.
CREATE TEMP TABLE demo_account AS
SELECT 'U1234567'::varchar               AS account,
       timestamptz '2026-11-22 19:10+02' AS synced_at,
       15000.00::double precision        AS cash;

-- Each stock: IB's contract and IB's two words for the company — the broad one is the holding's sector, the finer one
-- its industry (V14).
CREATE TEMP TABLE demo_stock AS
SELECT con_id, symbol, sector, industry FROM demo_source.stock_words
UNION ALL
(SELECT DISTINCT ON (symbol) con_id, symbol, industry, category
 FROM scan_candidate
 WHERE symbol IN ('OKTA', 'TEAM', 'U', 'WMT', 'ZS', 'MRNA', 'DOCU', 'RBLX')
 ORDER BY symbol, scan_id DESC);

INSERT INTO investor (name, is_account_owner, created_at) VALUES
    ('Omri', TRUE,  timestamptz '2024-09-01 20:00+03'),
    ('Dana', FALSE, timestamptz '2025-12-20 20:05+02');

INSERT INTO investor_cash_movement (investor_id, movement_date, type, amount, note, created_at)
SELECT investor.id, movement.movement_date, movement.type, movement.amount, movement.note,
       movement.movement_date + time '21:00'
FROM investor,
     (VALUES (date '2026-01-12', 'DEPOSIT',    8000.00, 'Opening deposit'),
             (date '2026-06-22', 'DEPOSIT',    2000.00, NULL),
             (date '2026-10-26', 'WITHDRAWAL', 1000.00, NULL))
         AS movement(movement_date, type, amount, note)
WHERE investor.name = 'Dana';

-- The trades, before their prices. typed_price only for a day before the stored closes (they begin 24.11.2025).
CREATE TEMP TABLE planned_trade (
    symbol        varchar,
    investor_name varchar,
    wanted_day    date,
    side          varchar,
    quantity      numeric,
    typed_price   numeric
);
INSERT INTO planned_trade VALUES
    -- open holdings
    ('NVDA', 'Omri', '2024-09-06', 'BUY',  120, 102.83),
    ('AAPL', 'Omri', '2026-04-01', 'BUY',   35, NULL),
    ('INTC', 'Omri', '2025-11-24', 'BUY',  150, NULL),
    ('TTWO', 'Omri', '2026-07-01', 'BUY',   15, NULL),
    ('TTWO', 'Omri', '2026-10-01', 'BUY',   15, NULL),
    ('OKTA', 'Dana', '2026-04-01', 'BUY',   30, NULL),
    ('TEAM', 'Omri', '2026-07-01', 'BUY',   30, NULL),
    ('TEAM', 'Dana', '2026-07-01', 'BUY',   20, NULL),
    ('U',    'Omri', '2026-04-01', 'BUY',  150, NULL),
    ('U',    'Omri', '2026-09-01', 'SELL',  50, NULL),
    ('WMT',  'Omri', '2026-05-01', 'BUY',   40, NULL),
    ('ZS',   'Omri', '2026-11-02', 'BUY',   25, NULL),
    ('MRNA', 'Omri', '2026-02-02', 'BUY',  100, NULL),   -- a sector besides Technology
    ('MRNA', 'Omri', '2026-09-01', 'SELL',  60, NULL),
    -- holdings sold in full
    ('DOCU', 'Dana', '2026-02-02', 'BUY',   80, NULL),
    ('DOCU', 'Dana', '2026-06-01', 'SELL',  80, NULL),
    ('RBLX', 'Omri', '2026-01-15', 'BUY',   30, NULL),
    ('RBLX', 'Omri', '2026-04-01', 'SELL',  30, NULL);  -- a loss: a tax shield

-- The close of the day, or of the trading day before it; the commission IB's default (1 cent a share, at least $5).
CREATE TEMP TABLE priced_trade AS
SELECT planned.symbol,
       planned.investor_name,
       planned.side,
       planned.quantity,
       stock.con_id,
       coalesce(bar.bar_date, planned.wanted_day)                         AS trade_date,
       coalesce(planned.typed_price, round(bar.close_price::numeric, 2)) AS price,
       round(greatest(5.00, 0.01 * planned.quantity), 2)                  AS commission
FROM planned_trade planned
JOIN demo_stock stock ON stock.symbol = planned.symbol
LEFT JOIN LATERAL (SELECT bar_date, close_price FROM daily_close
                   WHERE planned.typed_price IS NULL
                     AND con_id = stock.con_id
                     AND bar_date <= planned.wanted_day AND bar_date > planned.wanted_day - 7
                   ORDER BY bar_date DESC LIMIT 1) bar ON TRUE;

DO $$
BEGIN
    IF (SELECT count(*) FROM priced_trade WHERE price IS NOT NULL) <> (SELECT count(*) FROM planned_trade) THEN
        RAISE EXCEPTION 'a planned trade has no stock, or no close in the week before its day and no typed price';
    END IF;
END $$;

-- Each holding as IB would report it: the shares the trades add up to, IB's average cost (the buys with their
-- commissions, over the shares bought), the last close as its price; one sold in full as the sync leaves it.
CREATE TEMP TABLE demo_holding AS
SELECT trade.con_id,
       trade.symbol,
       sum(CASE WHEN trade.side = 'BUY' THEN trade.quantity ELSE -trade.quantity END) AS position,
       sum(trade.quantity * trade.price + trade.commission) FILTER (WHERE trade.side = 'BUY')
           / sum(trade.quantity) FILTER (WHERE trade.side = 'BUY')                      AS average_cost,
       min(trade.trade_date)                                                            AS first_trade_date,
       max(trade.trade_date)                                                            AS last_trade_date
FROM priced_trade trade
GROUP BY trade.con_id, trade.symbol;

INSERT INTO holding (account, con_id, symbol, sec_type, currency, sector, industry, position, average_cost,
                     market_price, market_value, unrealized_pnl, realized_pnl, status, first_seen_at, last_synced_at,
                     closed_detected_at)
SELECT demo_account.account,
       demo_holding.con_id,
       demo_holding.symbol,
       'STK',
       'USD',
       stock.sector,
       stock.industry,
       demo_holding.position,
       demo_holding.average_cost,
       price.close_price,
       CASE WHEN demo_holding.position > 0 THEN demo_holding.position * price.close_price ELSE 0 END,
       CASE WHEN demo_holding.position > 0
            THEN demo_holding.position * (price.close_price - demo_holding.average_cost::double precision)
            ELSE 0 END,
       0,
       CASE WHEN demo_holding.position > 0 THEN 'OPEN' ELSE 'CLOSED' END,
       demo_holding.first_trade_date + time '20:00',
       demo_account.synced_at,
       CASE WHEN demo_holding.position > 0 THEN NULL ELSE demo_holding.last_trade_date + 1 + time '20:00' END
FROM demo_holding
CROSS JOIN demo_account
JOIN demo_stock stock ON stock.con_id = demo_holding.con_id
CROSS JOIN LATERAL (SELECT close_price FROM daily_close
                    WHERE con_id = demo_holding.con_id
                      AND (demo_holding.position > 0 OR bar_date <= demo_holding.last_trade_date)
                    ORDER BY bar_date DESC LIMIT 1) price;

INSERT INTO trade (holding_id, trade_date, side, quantity, price, note, created_at, commission, investor_id)
SELECT holding.id, trade.trade_date, trade.side, trade.quantity, trade.price, NULL,
       trade.trade_date + time '22:00', trade.commission, investor.id
FROM priced_trade trade
JOIN holding  ON holding.con_id = trade.con_id
JOIN investor ON investor.name = trade.investor_name;

-- A position sold before PortfolioBoss's first sync, typed in by hand; plausible prices, made up.
WITH position AS (
    INSERT INTO manual_position (symbol, currency, sector, note, created_at)
    VALUES ('MSFT', 'USD', 'Technology', 'Sold before PortfolioBoss', timestamptz '2025-12-20 20:30+02')
    RETURNING id
)
INSERT INTO trade (manual_position_id, trade_date, side, quantity, price, note, created_at, commission, investor_id)
SELECT position.id, typed.trade_date, typed.side, typed.quantity, typed.price, NULL,
       timestamptz '2025-12-20 20:30+02', 5.00, (SELECT id FROM investor WHERE is_account_owner)
FROM position,
     (VALUES (date '2024-05-01', 'BUY',  10::numeric, 397.84::numeric),
             (date '2025-01-21', 'SELL', 10::numeric, 428.50::numeric))
         AS typed(trade_date, side, quantity, price);

-- IB's contract id of Microsoft: the manual position has none, but its Flex trades have one.
CREATE TEMP TABLE demo_manual_position_contract AS SELECT 272093 AS con_id;

-- The settlement day as the app estimates it (UsSettlementDayEstimate): one business day after the trade since
-- 28.05.2024, two before; weekends skipped, US holidays unknown.
CREATE FUNCTION pg_temp.settlement_day(trade_day date) RETURNS date LANGUAGE sql AS $$
    SELECT business_day::date
    FROM generate_series(trade_day + 1, trade_day + 10, interval '1 day') AS business_day
    WHERE extract(isodow FROM business_day) < 6
    ORDER BY business_day
    OFFSET (CASE WHEN trade_day >= date '2024-05-28' THEN 0 ELSE 1 END)
    LIMIT 1
$$;

-- IB's Flex report of the same trades, with IB's signs: a sale's quantity and every commission negative.
INSERT INTO flex_trade (ib_trade_id, ib_order_id, con_id, symbol, asset_category, currency, trade_date, trade_time,
                        settle_date, buy_sell, quantity, trade_price, ib_commission, ib_commission_currency, net_cash,
                        transaction_type, fifo_pnl_realized)
SELECT (700000000 + row_number() OVER (ORDER BY typed.trade_date, typed.id))::text,
       (800000000 + row_number() OVER (ORDER BY typed.trade_date, typed.id))::text,
       coalesce(holding.con_id, (SELECT con_id FROM demo_manual_position_contract)),
       coalesce(holding.symbol, manual.symbol),
       'STK',
       'USD',
       typed.trade_date,
       typed.trade_date + time '10:30' + make_interval(mins => (typed.id % 40)::int),
       pg_temp.settlement_day(typed.trade_date),
       typed.side,
       CASE WHEN typed.side = 'SELL' THEN -typed.quantity ELSE typed.quantity END,
       typed.price,
       -typed.commission,
       'USD',
       CASE WHEN typed.side = 'SELL' THEN typed.quantity * typed.price ELSE -(typed.quantity * typed.price) END
           - typed.commission,
       'ExchTrade',
       NULL
FROM trade typed
LEFT JOIN holding                ON holding.id = typed.holding_id
LEFT JOIN manual_position manual ON manual.id = typed.manual_position_id;

INSERT INTO flex_report (from_date, to_date, downloaded_at, trade_count, cash_transaction_count, corporate_action_count)
SELECT date '2024-01-01', date '2026-11-20', demo_account.synced_at, (SELECT count(*) FROM flex_trade), 0, 0
FROM demo_account;

-- Each open holding's latest analysis: the four stocks' own, the others' from the scans (Claude was never told about a
-- portfolio). WMT and ZS were never analyzed.
CREATE TEMP TABLE demo_analysis AS
SELECT con_id, analyzed_at, model, result, input_tokens, output_tokens, tavily_credits, cost_usd, search_results,
       NULL::jsonb AS finnhub_stock
FROM demo_source.latest_analysis
UNION ALL
(SELECT DISTINCT ON (candidate.con_id) candidate.con_id, analysis.analyzed_at, analysis.model, analysis.result,
        analysis.input_tokens, analysis.output_tokens, analysis.tavily_credits, analysis.cost_usd,
        analysis.search_results, analysis.finnhub_stock
 FROM stock_analysis analysis
 JOIN scan_candidate candidate ON candidate.id = analysis.scan_candidate_id
 WHERE candidate.con_id IN (SELECT con_id FROM demo_stock WHERE symbol IN ('OKTA', 'TEAM', 'U'))
 ORDER BY candidate.con_id, analysis.analyzed_at DESC);

INSERT INTO stock_analysis (holding_id, analyzed_at, model, result, input_tokens, output_tokens, tavily_credits,
                            cost_usd, search_results)
SELECT holding.id, analysis.analyzed_at, analysis.model, analysis.result, analysis.input_tokens,
       analysis.output_tokens, analysis.tavily_credits, analysis.cost_usd, analysis.search_results
FROM demo_analysis analysis
JOIN holding ON holding.con_id = analysis.con_id AND holding.status = 'OPEN';

-- Finnhub's answer as the sync stores it for each holding in dollars; a report date already past is dropped, as
-- Finnhub's calendar on the sync's day wouldn't have it.
INSERT INTO stock (con_id, symbol, fetched_at, finnhub_data)
SELECT answer.con_id, answer.symbol, demo_account.synced_at,
       CASE WHEN (answer.finnhub_data #>> '{nextEarningsDate,date}')::date < demo_account.synced_at::date
            THEN jsonb_set(answer.finnhub_data, '{nextEarningsDate}', 'null'::jsonb)
            ELSE answer.finnhub_data END
FROM (SELECT con_id, symbol, finnhub_data FROM demo_source.finnhub_answer
      UNION ALL
      SELECT analysis.con_id, stock.symbol, analysis.finnhub_stock
      FROM demo_analysis analysis JOIN demo_stock stock ON stock.con_id = analysis.con_id
      WHERE analysis.finnhub_stock IS NOT NULL) answer
CROSS JOIN demo_account
JOIN holding ON holding.con_id = answer.con_id AND holding.status = 'OPEN';

INSERT INTO account_state (account, as_of, net_liquidation, total_cash_value)
SELECT demo_account.account,
       demo_account.synced_at,
       demo_account.cash + (SELECT sum(market_value) FROM holding WHERE status = 'OPEN'),
       demo_account.cash
FROM demo_account;

DROP SCHEMA demo_source CASCADE;
