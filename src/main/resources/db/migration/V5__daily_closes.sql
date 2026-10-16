-- A year of daily closing prices from IB for each holding and for SPY, the momentum benchmark (AI_ANALYSIS_TODO.md,
-- session 1). A contract's rows are replaced in full on every sync that read its closes: IB adjusts past prices for
-- splits, so nothing old is kept. IB figures, so DOUBLE PRECISION. Keyed by IB's contract id, not by holding: SPY is
-- read whether or not the account holds it.
CREATE TABLE daily_close (
    id           BIGSERIAL         PRIMARY KEY,
    con_id       INTEGER           NOT NULL,
    bar_date     DATE              NOT NULL,
    close_price  DOUBLE PRECISION  NOT NULL,
    UNIQUE (con_id, bar_date)
);
