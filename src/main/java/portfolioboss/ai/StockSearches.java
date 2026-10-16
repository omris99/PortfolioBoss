package portfolioboss.ai;

/**
 * The four Tavily searches of one stock (AI_ANALYSIS_TODO.md, decisions 2, 19 and 21), which go to Claude together.
 *
 * @param analystForecasts  forecast pages: the consensus and the targets of several sources
 * @param marketBeatActions MarketBeat's articles of the last month, listing the analysts' recent reports
 * @param latestActions     the week's news about analysts' actions, on any site
 * @param news              the week's news
 */
public record StockSearches(SearchResults analystForecasts, SearchResults marketBeatActions,
                            SearchResults latestActions, SearchResults news) {

    /** What the four searches cost together. */
    public int credits() {
        return analystForecasts.credits() + marketBeatActions.credits() + latestActions.credits() + news.credits();
    }

    /**
     * Only the results that name the stock, in each search (decision 17) — what Claude reads, and what is stored with
     * its answer (decision 22). The credits stay: they were spent either way.
     */
    public StockSearches mentioning(String symbol) {
        return new StockSearches(analystForecasts.mentioning(symbol), marketBeatActions.mentioning(symbol),
                latestActions.mentioning(symbol), news.mentioning(symbol));
    }
}
