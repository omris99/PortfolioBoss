-- Several investors in one IB account (INVESTORS_TODO.md, session 1). Every other investor is entered explicitly — their
-- trades and their deposits — and the account owner gets whatever those don't explain, so the totals always equal IB's.

CREATE TABLE investor (
    id                BIGSERIAL    PRIMARY KEY,
    name              VARCHAR(60)  NOT NULL UNIQUE,
    is_account_owner  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- At most one account owner: the investor who gets whatever the others' entries don't explain.
CREATE UNIQUE INDEX investor_one_account_owner ON investor (is_account_owner) WHERE is_account_owner;
INSERT INTO investor (name, is_account_owner) VALUES ('Me', TRUE);

-- Every trade belongs to an investor — a manual position's too, since its trades are rows here as well. The ones entered
-- so far are the account owner's.
ALTER TABLE trade ADD COLUMN investor_id BIGINT REFERENCES investor (id);
UPDATE trade SET investor_id = (SELECT id FROM investor WHERE is_account_owner);
ALTER TABLE trade ALTER COLUMN investor_id SET NOT NULL;

-- Only for investors other than the account owner, whose cash comes from IB (checked by the API).
CREATE TABLE investor_cash_movement (
    id             BIGSERIAL     PRIMARY KEY,
    investor_id    BIGINT        NOT NULL REFERENCES investor (id),
    movement_date  DATE          NOT NULL,
    type           VARCHAR(10)   NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    amount         NUMERIC(20,6) NOT NULL CHECK (amount > 0),
    note           VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX investor_cash_movement_investor_date ON investor_cash_movement (investor_id, movement_date);
