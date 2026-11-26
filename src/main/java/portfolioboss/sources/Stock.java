package portfolioboss.sources;

/**
 * What a source of company figures knows about a stock (RECOMMENDATIONS_TODO.md, decision 8; since
 * RECOMMENDATIONS_FINVIZ_TODO.md, decision 9, from Finviz first and Finnhub after it): when it reports next, and four of
 * the company's figures — for the risk signs and the timing note of decision 5, and the drawer's "Company" tab. Not its
 * price, name or exchange, which come from IB. Stored as JSON as it is ({@code stock.company_figures},
 * {@code stock_analysis.company_figures}), so a component may be added, never renamed.
 *
 * @param nextEarningsDate {@code null} when the source knows no report in the next four months
 * @param answeredBy       the source that answered, as the screens name it: "Finviz", "Finnhub"
 */
public record Stock(String symbol, EarningsDate nextEarningsDate, CompanyFacts companyFacts, String answeredBy) {

    /** Until 26.11.2026 Finnhub was the only source, and what it answered was stored without {@code answeredBy}. */
    public static final String SOURCE_OF_THE_ANSWERS_STORED_BEFORE = "Finnhub";

    /** An answer stored before {@code answeredBy} existed reads it as {@code null}: it was Finnhub's. */
    public Stock {
        if (answeredBy == null) {
            answeredBy = SOURCE_OF_THE_ANSWERS_STORED_BEFORE;
        }
    }
}
