-- The finer of IB's two words for a holding's company (MY_HOLDINGS_TODO.md, decision 11): "Semiconductors" under the
-- sector "Technology". IB names the two the other way round from the market — the broad one industry, this one
-- category, as scan_candidate keeps them —, and the holding follows the market: sector is IB's industry, industry is
-- IB's category. Written by the sync from the contract details, like the sector, never typed by hand; NULL until the
-- first sync that reads them, and when IB gives none.
ALTER TABLE holding ADD COLUMN industry VARCHAR(60);
