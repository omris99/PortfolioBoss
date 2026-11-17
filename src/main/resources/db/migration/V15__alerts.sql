-- The alerts (MY_HOLDINGS_TODO.md, session 5ב, decisions 14–17): after every sync, and after the settings are saved, the
-- conditions true now are compared with the alerts still open. A condition true now with no open alert becomes a new
-- row; an open alert whose condition is no longer true gets its ended_at. A condition that comes back later is a new row.
--
-- subject tells two alerts of the same type on the same stock apart — an analyst's cut (the firm and the headline's
-- time), a report (its date), which of the holding's warnings —, NULL when the type and the stock are enough. message is
-- written when the condition became true, and says what was true then. seen_at is set when the bell is opened.
CREATE TABLE alert (
    id          BIGSERIAL    PRIMARY KEY,
    alert_type  VARCHAR(40)  NOT NULL,
    con_id      INTEGER      NOT NULL,
    symbol      VARCHAR(32)  NOT NULL,
    subject     TEXT,
    message     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ,
    seen_at     TIMESTAMPTZ
);

-- At most one open alert per condition, even if the code had a bug; the same index finds the open alerts. NULLS NOT
-- DISTINCT: two open alerts with no subject are the same condition (by default PostgreSQL counts two NULLs as different).
CREATE UNIQUE INDEX alert_one_open_per_condition ON alert (alert_type, con_id, subject) NULLS NOT DISTINCT
    WHERE ended_at IS NULL;

-- The settings drawer: one row per alert type the user saved; a type without a row has its defaults (on, and the number
-- AlertType gives it). threshold is the type's number — days or percent — and NULL for a type that has none.
CREATE TABLE alert_setting (
    alert_type  VARCHAR(40)   PRIMARY KEY,
    enabled     BOOLEAN       NOT NULL,
    threshold   NUMERIC(5,2)
);
