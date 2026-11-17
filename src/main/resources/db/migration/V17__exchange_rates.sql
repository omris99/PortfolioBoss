-- Bank of Israel's representative exchange rates (CAPITAL_GAINS_TAX_TODO.md, session 2): what one unit of a currency was
-- worth in shekels on each day the Bank of Israel published a rate. The capital gains tax converts every trade at the rate
-- of its settlement day (decision 1), as Phoenix does.
--
-- The stored rates of a currency are always one unbroken stretch of the Bank of Israel's series: a download only adds the
-- days before the first stored rate or after the last one. So a day inside the stretch without a row is a day the Bank of
-- Israel published no rate on — an Israeli or an international holiday —, and it takes the last rate before it. A rate,
-- once published, never changes, so a stored row is never replaced.
--
-- A generated id rather than a key of (currency, rate_date): JPA would need a separate key class for that; the UNIQUE
-- still keeps one rate per currency and day. The rates come with up to 3 decimals ("3.306").
CREATE TABLE exchange_rate (
    id         BIGSERIAL      PRIMARY KEY,
    currency   VARCHAR(3)     NOT NULL,   -- the currency's code, "USD"; the rate is in shekels for one unit of it
    rate_date  DATE           NOT NULL,
    rate       NUMERIC(12,6)  NOT NULL,
    UNIQUE (currency, rate_date)
);
