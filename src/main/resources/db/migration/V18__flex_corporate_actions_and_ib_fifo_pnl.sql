-- The corporate actions of IB's Flex reports, and IB's own FIFO gain of each execution (CAPITAL_GAINS_TAX_TODO.md,
-- session 3.0, decision 15). The Trades section shows no split, reverse split or change of contract: a stock bought
-- under one contract and sold after a reverse split under another has its buys and its sale in two contracts, and the
-- tax's FIFO can't follow it without the action. The Corporate Actions section was added to the query for it.
--
-- As in V16: each row as IB wrote it, signs included, NUMERIC amounts without a limit on their digits, IB's text TEXT,
-- no account number; a download replaces a row whose IB id is already stored. The rows are read only to compute; the
-- tax restates the trades before a split in the shares of after it in memory, never here.

-- One row per row of an action: a reverse split of 1 for 5 is two rows sharing ib_action_id and action_time — the old
-- contract's shares leaving (a negative quantity, the symbol with ".OLD") and the new contract's coming in.
CREATE TABLE flex_corporate_action (
    id                 BIGSERIAL  PRIMARY KEY,
    ib_transaction_id  TEXT       NOT NULL UNIQUE,
    ib_action_id       TEXT       NOT NULL,
    action_type        TEXT       NOT NULL,   -- IB's type: RS for a reverse split, FS for a split, …
    con_id             INTEGER    NOT NULL,
    symbol             TEXT       NOT NULL,
    asset_category     TEXT       NOT NULL,
    action_time        TIMESTAMP  NOT NULL,   -- New York's time, as IB writes it, like flex_trade.trade_time
    quantity           NUMERIC    NOT NULL,
    proceeds           NUMERIC    NOT NULL,
    amount             NUMERIC    NOT NULL,
    description        TEXT
);

-- How many corporate actions each download brought, beside its trades and cash transactions; 0 for the reports stored
-- before the section was in the query.
ALTER TABLE flex_report ADD COLUMN corporate_action_count INTEGER NOT NULL DEFAULT 0;

-- IB's FIFO gain of the execution, in the trade's currency with the commissions (0 for a buy): what the tax's FIFO is
-- checked against, sale by sale. NULL for the rows stored before the field was in the query, until a download of their
-- days replaces them.
ALTER TABLE flex_trade ADD COLUMN fifo_pnl_realized NUMERIC;
