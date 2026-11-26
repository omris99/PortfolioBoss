-- The Recommendations page's three lists from Finviz (RECOMMENDATIONS_FINVIZ_TODO.md, session 2): fast growth, slow
-- growth and value, each one of Finviz's screens, downloaded with the sync once a trading day. Every download is kept:
-- the measurement compares what each list held with how those stocks did afterwards. closes_read_at is when the daily
-- closes of the download's stocks were last read from IB — once a trading day, while Finviz is off too (decision 10);
-- NULL until the first reading.
CREATE TABLE stock_lists_download (
    id              BIGSERIAL    PRIMARY KEY,
    source          TEXT         NOT NULL,
    downloaded_at   TIMESTAMPTZ  NOT NULL,
    closes_read_at  TIMESTAMPTZ
);

-- One row per stock of each list of a download, in the source's order; the app sorts each list itself, from the figures
-- (RECOMMENDATIONS_FINVIZ_TODO.md, decision 13), so no order is stored. symbol as IB writes it ("BRK B" where Finviz
-- writes "BRK-B"); con_id the IB contract found for the symbol — NULL until TWS finds it, and taken from an earlier
-- download when the symbol was found before; figures the source's figures of the stock
-- (model.recommendations.ScreenedStock), as JSON. A stock can be in two lists.
CREATE TABLE listed_stock (
    id                   BIGSERIAL  PRIMARY KEY,
    download_id          BIGINT     NOT NULL REFERENCES stock_lists_download (id),
    recommendation_list  TEXT       NOT NULL,
    symbol               TEXT       NOT NULL,
    con_id               INTEGER,
    figures              JSONB      NOT NULL,
    UNIQUE (download_id, recommendation_list, symbol)
);

-- The contract of a symbol found in an earlier download is looked up by its symbol.
CREATE INDEX listed_stock_symbol ON listed_stock (symbol);

-- A company's figures came only from Finnhub until RECOMMENDATIONS_FINVIZ_TODO.md, decision 9: since then from Finviz
-- first, and the columns are named for what they hold. Each value says which source answered (answeredBy).
ALTER TABLE stock RENAME COLUMN finnhub_data TO company_figures;
ALTER TABLE stock_analysis RENAME COLUMN finnhub_stock TO company_figures;
