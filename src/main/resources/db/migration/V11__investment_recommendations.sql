-- The recommendations the code picked (RECOMMENDATIONS_TODO.md, decisions 5 and 13): one row per full run of the
-- Recommendations page's Analyze, with the picked stocks in their order — what the measurement of decision 9 compares
-- with how the stocks did afterwards. picks is a JSON list of scan_candidate ids. A run that picked none is stored too,
-- with an empty list: "no recommendation this time" is an answer the measurement counts. Only the picks: why each was
-- picked is worked out again from its stored analysis. Every run is kept; the page shows the latest.
CREATE TABLE investment_recommendation (
    id              BIGSERIAL    PRIMARY KEY,
    scan_id         BIGINT       NOT NULL REFERENCES scan (id),
    recommended_at  TIMESTAMPTZ  NOT NULL,
    picks           JSONB        NOT NULL
);
CREATE INDEX investment_recommendation_latest ON investment_recommendation (recommended_at DESC);
