-- The confirmations of the capital gains tax (CAPITAL_GAINS_TAX_TODO.md, session 4, decision 7): what Phoenix recorded
-- for one of the account's settlement days, as the user confirmed it on the Taxes page, after Phoenix recorded it. The
-- calculated amount is not stored — it is derived on every read; this is only what the user entered.
--
-- One confirmation for a day: Phoenix nets every sale of a day into one entry (decision 4). tax_amount is in shekels, to
-- the agora: positive — tax to pay ("מס לשלם") —, negative — a tax shield ("מגן מס"). It is final: it counts in the year's
-- balance in place of the calculated amount. confirmed_at is when it was confirmed, or last corrected.
CREATE TABLE tax_confirmation (
    id              BIGSERIAL      PRIMARY KEY,
    settlement_day  DATE           NOT NULL UNIQUE,
    tax_amount      NUMERIC(20,2)  NOT NULL,
    confirmed_at    TIMESTAMPTZ    NOT NULL,
    note            VARCHAR(500)
);
