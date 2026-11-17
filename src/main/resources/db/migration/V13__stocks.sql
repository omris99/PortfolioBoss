-- What Finnhub answered about each open holding in USD, read on every connection (MY_HOLDINGS_TODO.md, session 2,
-- decision 10). finnhub_data is the stocks.Stock record as it is: the next earnings date and four of the company's
-- figures, for the risk signs and the timing note. One row per contract, keyed by IB's contract id like daily_close; a
-- sync replaces the row of every contract Finnhub answered for, and a call that fails leaves the row stored before.
CREATE TABLE stock (
    id            BIGSERIAL    PRIMARY KEY,
    con_id        INTEGER      NOT NULL UNIQUE,
    symbol        VARCHAR(32)  NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL,
    finnhub_data  JSONB        NOT NULL
);
