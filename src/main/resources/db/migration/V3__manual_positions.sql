-- Manual positions with trades of their own (CLOSED_POSITIONS_TODO.md, session 4): a position PortfolioBoss never saw
-- as a holding — sold before the first sync — with any number of buys and sells, instead of V2's one row per round
-- trip. Its trades live in `trade`, next to the holdings' trades, so they are computed and corrected the same way.

CREATE TABLE manual_position (
    id          BIGSERIAL     PRIMARY KEY,
    symbol      VARCHAR(32)   NOT NULL,
    currency    VARCHAR(8)    NOT NULL,
    sector      VARCHAR(60),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A trade belongs to a holding or to a manual position, never both. Deleting a manual position deletes its trades.
ALTER TABLE trade ALTER COLUMN holding_id DROP NOT NULL;
ALTER TABLE trade ADD COLUMN manual_position_id BIGINT REFERENCES manual_position (id) ON DELETE CASCADE;
ALTER TABLE trade ADD CHECK (num_nonnulls(holding_id, manual_position_id) = 1);
CREATE INDEX trade_manual_position_date ON trade (manual_position_id, trade_date);

-- Every round trip entered in V2's table becomes a manual position with the same id, one buy and one sell. Its one
-- commission is split between the two orders without losing a cent.
INSERT INTO manual_position (id, symbol, currency, sector, note, created_at)
    SELECT id, symbol, currency, sector, note, created_at FROM manual_closed_position;
SELECT setval(pg_get_serial_sequence('manual_position', 'id'),
              (SELECT coalesce(max(id), 0) + 1 FROM manual_position), false);
INSERT INTO trade (manual_position_id, trade_date, side, quantity, price, commission)
    SELECT id, buy_date, 'BUY', quantity, buy_price, ROUND(commission / 2, 2) FROM manual_closed_position;
INSERT INTO trade (manual_position_id, trade_date, side, quantity, price, commission)
    SELECT id, sell_date, 'SELL', quantity, sell_price, commission - ROUND(commission / 2, 2) FROM manual_closed_position;

DROP TABLE manual_closed_position;
