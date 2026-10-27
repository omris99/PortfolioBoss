package portfolioboss.stocks;

/**
 * What Finnhub knows about a stock (RECOMMENDATIONS_TODO.md, decision 8): when it reports next, and four of the
 * company's figures — for the risk signs and the timing note of decision 5, and the drawer's "Company" part. Not its
 * price, name or exchange, which come from IB.
 *
 * @param nextEarningsDate {@code null} when Finnhub's calendar has no report in the next four months
 */
public record Stock(String symbol, EarningsDate nextEarningsDate, CompanyFacts companyFacts) {
}
