-- Commissions, and the closed positions entered by hand (CLOSED_POSITIONS_TODO.md, session 2).

-- What the broker charged for the trade. A trade entered without one gets the default for one order,
-- 1 cent a share with a $5 minimum (Utils.calculateOrderCommission), so the column is never empty: the
-- trades entered before this column existed get that same default here.
ALTER TABLE trade ADD COLUMN commission NUMERIC(20,6) CHECK (commission >= 0);
UPDATE trade SET commission = ROUND(GREATEST(5, quantity * 0.01), 2);
ALTER TABLE trade ALTER COLUMN commission SET NOT NULL;

-- A position bought and sold back to zero that PortfolioBoss never saw as a holding (sold before the first
-- sync): one row is the whole round trip, one buy and one sell. Separate from `holding` on purpose: only the
-- sync creates holdings. `commission` is the total of both orders.
CREATE TABLE manual_closed_position (
    id          BIGSERIAL     PRIMARY KEY,
    symbol      VARCHAR(32)   NOT NULL,
    currency    VARCHAR(8)    NOT NULL,
    sector      VARCHAR(60),
    quantity    NUMERIC(20,6) NOT NULL CHECK (quantity > 0),
    buy_date    DATE          NOT NULL,
    buy_price   NUMERIC(20,6) NOT NULL CHECK (buy_price >= 0),
    sell_date   DATE          NOT NULL,
    sell_price  NUMERIC(20,6) NOT NULL CHECK (sell_price >= 0),
    commission  NUMERIC(20,6) NOT NULL CHECK (commission >= 0),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (sell_date >= buy_date)
);
