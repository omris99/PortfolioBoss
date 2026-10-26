-- The scans for hot stocks (RECOMMENDATIONS_TODO.md, session 1): ./run.sh scans once a trading day, and every scan is
-- kept — the measurement of decision 9 compares what each scan found with how those stocks did afterwards. scan_codes
-- are the scans that answered, as a JSON list.
CREATE TABLE scan (
    id          BIGSERIAL    PRIMARY KEY,
    scanned_at  TIMESTAMPTZ  NOT NULL,
    scan_codes  JSONB        NOT NULL
);

-- One row per stock a scan kept (on NYSE or NASDAQ), in IB's words. rank_by_scan_code is where each scan that found the
-- stock placed it, 0 the top — a stock can be in both scans. close_at_scan and close_date are the stock's last daily
-- close read in the same run, the price the measurement starts from (NULL when its closes did not come); the closes
-- themselves are in daily_close, by con_id, like SPY's. IB figures, so DOUBLE PRECISION.
CREATE TABLE scan_candidate (
    id                 BIGSERIAL         PRIMARY KEY,
    scan_id            BIGINT            NOT NULL REFERENCES scan (id),
    con_id             INTEGER           NOT NULL,
    symbol             VARCHAR(32)       NOT NULL,
    company_name       VARCHAR(200),
    exchange           VARCHAR(16)       NOT NULL,
    industry           VARCHAR(60),
    category           VARCHAR(60),
    rank_by_scan_code  JSONB             NOT NULL,
    close_at_scan      DOUBLE PRECISION,
    close_date         DATE,
    UNIQUE (scan_id, con_id)
);
