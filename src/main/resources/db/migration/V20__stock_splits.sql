-- The splits of stocks (STOCK_SPLITS_TODO.md, session 1), found by the sync in the Corporate Actions section of IB's Flex
-- reports (flex_corporate_action, V18) and stored only when IB's words and the shares agree (decision 3). Each split once,
-- by IB's actionID (decision 2): the Flex reports bring the same rows again on every sync.
--
-- "SPLIT 4 FOR 1" is shares_after_the_split 4 for shares_before_the_split 1: two whole numbers, not a ratio, since 1 for 3
-- has no exact decimal. con_id_before_the_split and con_id_after_the_split are the same for a split in the same
-- contract, and differ for a reverse split that moved the stock to a new contract. split_time is IB's, New York's time.
--
-- The trades typed in stay as typed: a holding's trades held through a split are translated into the shares of after it
-- on every read (decision 7), and so are the account's Flex trades for the tax.
CREATE TABLE stock_split (
    id                       BIGSERIAL    PRIMARY KEY,
    ib_action_id             TEXT         NOT NULL UNIQUE,
    symbol                   TEXT         NOT NULL,
    con_id_before_the_split  INTEGER      NOT NULL,
    con_id_after_the_split   INTEGER      NOT NULL,
    split_time               TIMESTAMP    NOT NULL,
    shares_before_the_split  INTEGER      NOT NULL CHECK (shares_before_the_split > 0),
    shares_after_the_split   INTEGER      NOT NULL CHECK (shares_after_the_split > 0),
    stored_at                TIMESTAMPTZ  NOT NULL
);
