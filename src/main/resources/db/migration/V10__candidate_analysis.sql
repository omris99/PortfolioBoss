-- A scanned stock's analysis (RECOMMENDATIONS_TODO.md, session 3): the same four searches and the same call to Claude
-- as a holding's, kept in the same table. A row is a holding's or a scanned stock's — exactly one of the two ids, like
-- trade's holding_id / manual_position_id. A scanned stock is one row of one scan (scan_candidate), so that the
-- measurement knows which scan it was analyzed from; the page finds a stock's latest analysis by its con_id, from any
-- scan (decision 12). finnhub_stock is what Finnhub answered about the stock (stocks.Stock: the next earnings date and
-- four of the company's figures) — NULL when it didn't answer, and for a holding's analysis, which doesn't ask Finnhub.
ALTER TABLE stock_analysis ALTER COLUMN holding_id DROP NOT NULL;
ALTER TABLE stock_analysis ADD COLUMN scan_candidate_id BIGINT REFERENCES scan_candidate (id);
ALTER TABLE stock_analysis ADD CHECK (num_nonnulls(holding_id, scan_candidate_id) = 1);
ALTER TABLE stock_analysis ADD COLUMN finnhub_stock JSONB;
CREATE INDEX stock_analysis_scan_candidate_latest ON stock_analysis (scan_candidate_id, analyzed_at DESC);
