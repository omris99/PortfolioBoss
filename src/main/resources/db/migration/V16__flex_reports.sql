-- IB's Flex reports, stored (CAPITAL_GAINS_TAX_TODO.md, session 1): every trade and cash transaction of the account
-- as IB's Flex Web Service reported it, for the capital gains tax — which Phoenix computes from every sale in the
-- account, day trades included, and with IB's own prices, commissions and settlement days. Apart from trade and holding
-- on purpose: those are what the user typed in for the long-term holdings, and nothing here changes them.
--
-- Each row is kept as IB wrote it, signs included: a sale's quantity is negative, a commission is negative, and a fee
-- IB cancelled comes back as a row of its own with a positive amount. The amounts are NUMERIC without a limit on their
-- digits, so that no value of IB's is ever rounded or refused (the tax is computed from them to the agora); IB's free
-- text is TEXT, as in V12, so that no length can fail a download. The account number is not stored.
--
-- A download replaces a row whose IB id is already stored, so reading the same days again adds nothing.

-- One row per execution: an order filled in six parts is six rows, with the same ib_order_id. trade_time is the time IB
-- wrote, New York's, without a time zone; settle_date is IB's SettleDateTarget, the day the money and the shares
-- actually move — the day whose exchange rate the tax uses.
CREATE TABLE flex_trade (
    id                     BIGSERIAL  PRIMARY KEY,
    ib_trade_id            TEXT       NOT NULL UNIQUE,
    ib_order_id            TEXT,
    con_id                 INTEGER    NOT NULL,
    symbol                 TEXT       NOT NULL,
    asset_category         TEXT       NOT NULL,   -- STK for a stock, CASH for a currency conversion
    currency               TEXT       NOT NULL,
    trade_date             DATE       NOT NULL,
    trade_time             TIMESTAMP  NOT NULL,
    settle_date            DATE       NOT NULL,
    buy_sell               TEXT       NOT NULL,
    quantity               NUMERIC    NOT NULL,
    trade_price            NUMERIC    NOT NULL,
    ib_commission          NUMERIC    NOT NULL,
    ib_commission_currency TEXT       NOT NULL,   -- not always the trade's: a conversion to HKD is charged in dollars
    net_cash               NUMERIC    NOT NULL,
    transaction_type       TEXT
);

-- One row per cash movement that is not a trade: dividends, tax withheld at source, Other Fees (IB's market data fees)
-- and their cancellations. con_id and symbol are NULL for a fee, which belongs to no stock.
CREATE TABLE flex_cash_transaction (
    id                 BIGSERIAL  PRIMARY KEY,
    ib_transaction_id  TEXT       NOT NULL UNIQUE,
    transaction_type   TEXT       NOT NULL,
    con_id             INTEGER,
    symbol             TEXT,
    currency           TEXT       NOT NULL,
    amount             NUMERIC    NOT NULL,
    transaction_time   TIMESTAMP  NOT NULL,
    settle_date        DATE       NOT NULL,
    description        TEXT
);

-- The log of the downloads, a row per report: the days it covered, as IB wrote them in the report itself, and how many
-- rows it brought. The latest to_date is how far the stored reports reach; a sync asks IB only for the days after it.
CREATE TABLE flex_report (
    id                      BIGSERIAL    PRIMARY KEY,
    from_date               DATE         NOT NULL,
    to_date                 DATE         NOT NULL,
    downloaded_at           TIMESTAMPTZ  NOT NULL,
    trade_count             INTEGER      NOT NULL,
    cash_transaction_count  INTEGER      NOT NULL
);
