-- One row per holding per analysis run (AI_ANALYSIS_TODO.md, session 2); the UI shows the latest, and the rest are
-- kept for later. The result is Claude's JSON as validated against the schema (calculation.StockAnalysisResult): the
-- schema may still change after the first runs, and a JSONB column takes that without a migration. What is decided
-- from it — the consensus shown, the dot — is worked out on every read, never stored. The cost columns are what the
-- run spent on this holding: Claude's tokens and Tavily's credits, and the dollars of Claude's tokens.
CREATE TABLE stock_analysis (
    id              BIGSERIAL      PRIMARY KEY,
    holding_id      BIGINT         NOT NULL REFERENCES holding (id),
    analyzed_at     TIMESTAMPTZ    NOT NULL,
    model           VARCHAR(60)    NOT NULL,
    result          JSONB          NOT NULL,
    input_tokens    INTEGER        NOT NULL,
    output_tokens   INTEGER        NOT NULL,
    tavily_credits  INTEGER        NOT NULL,
    cost_usd        NUMERIC(10,6)  NOT NULL
);
CREATE INDEX stock_analysis_holding_latest ON stock_analysis (holding_id, analyzed_at DESC);
