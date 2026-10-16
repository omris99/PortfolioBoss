-- The first schema. A migration that has run is never edited (Flyway checks its checksum): any later
-- change is a new file, V2__something.sql.

CREATE TABLE holding (
    id                 BIGSERIAL        PRIMARY KEY,
    account            VARCHAR(32)      NOT NULL,
    con_id             INTEGER          NOT NULL,   -- IB's stable contract id (a symbol can change)
    symbol             VARCHAR(32)      NOT NULL,
    sec_type           VARCHAR(16)      NOT NULL,
    currency           VARCHAR(8)       NOT NULL,
    -- Typed in by the user; the IB sync never overwrites it.
    sector             VARCHAR(60),
    -- From IB, refreshed on every connection. DOUBLE PRECISION because IB reports doubles.
    position           DOUBLE PRECISION NOT NULL DEFAULT 0,
    average_cost       DOUBLE PRECISION,
    market_price       DOUBLE PRECISION,
    market_value       DOUBLE PRECISION,
    unrealized_pnl     DOUBLE PRECISION,
    realized_pnl       DOUBLE PRECISION,
    status             VARCHAR(6)       NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    first_seen_at      TIMESTAMPTZ      NOT NULL,
    last_synced_at     TIMESTAMPTZ      NOT NULL,
    closed_detected_at TIMESTAMPTZ,
    UNIQUE (account, con_id)
);

-- Entered by hand, so NUMERIC: the sum of trades must be exact (fractional shares such as 10.5).
CREATE TABLE trade (
    id          BIGSERIAL     PRIMARY KEY,
    holding_id  BIGINT        NOT NULL REFERENCES holding (id),
    trade_date  DATE          NOT NULL,
    side        VARCHAR(4)    NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity    NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    price       NUMERIC(20,6) CHECK (price >= 0),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX trade_holding_date ON trade (holding_id, trade_date);

-- One row per account, replaced on every sync (NAV history is a separate feature).
CREATE TABLE account_state (
    account           VARCHAR(32)      PRIMARY KEY,
    as_of             TIMESTAMPTZ      NOT NULL,
    net_liquidation   DOUBLE PRECISION,
    total_cash_value  DOUBLE PRECISION
);
