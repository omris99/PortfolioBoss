-- The analysts' price targets read from Dow Jones's headlines with every scan (RECOMMENDATIONS_TODO.md, decision 14): one
-- row per headline, keyed by IB's contract id like daily_close. A scan replaces in full the rows of every stock whose
-- headlines it read, and leaves the others. A target is in the price terms of its own day — after a split an older one
-- is several times too high —, which is why the code takes the median of the firms' latest targets. IB figures, so
-- DOUBLE PRECISION; the firm and the headline are Dow Jones's text, TEXT so that no length can fail a scan.
CREATE TABLE analyst_price_target (
    id                     BIGSERIAL         PRIMARY KEY,
    con_id                 INTEGER           NOT NULL,
    published_at           TIMESTAMP         NOT NULL,
    firm                   TEXT              NOT NULL,
    price_target           DOUBLE PRECISION  NOT NULL,
    previous_price_target  DOUBLE PRECISION,
    headline               TEXT              NOT NULL
);

CREATE INDEX analyst_price_target_con_id ON analyst_price_target (con_id);
