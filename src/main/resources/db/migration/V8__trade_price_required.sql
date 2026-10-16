-- Every trade has a price from now on: the cash of each investor, the average cost and the realized P&L all need it, and
-- the API has refused a trade without one since this migration. On 2026-10-13 every one of the 40 trades stored had a
-- price, so nothing had to be filled in first.
ALTER TABLE trade ALTER COLUMN price SET NOT NULL;
