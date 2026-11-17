package portfolioboss.ib;

/**
 * What IB's contract details say about a stock that a scan found — a scan's row carries neither its exchange nor its
 * sector. In IB's own words, as it sends them (it may send an empty text). Not to be confused with {@code stocks.Stock},
 * what Finnhub knows about a stock, asked only on Analyze.
 *
 * @param exchange    the primary exchange: {@code NYSE}, {@code NASDAQ}, {@code ARCA}…
 * @param companyName e.g. "APPLE INC"
 * @param industry    the broad sector, e.g. "Technology"
 * @param category    within it, e.g. "Software"
 */
public record StockContractDetails(int conId, String exchange, String companyName, String industry, String category) {
}
