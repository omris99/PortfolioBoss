package portfolioboss.ai;

import java.util.List;

/** One Tavily search: its results and the credits it cost. */
public record SearchResults(List<SearchResult> results, int credits) {

    /**
     * Only the results that name the stock (AI_ANALYSIS_TODO.md, decision 17): a cheap guard against another company's
     * ratings ending up under this one, and fewer tokens. The credits stay — they were spent either way.
     */
    public SearchResults mentioning(String symbol) {
        List<SearchResult> mentioningResults = results.stream()
                .filter(result -> result.mentions(symbol))
                .toList();
        return new SearchResults(mentioningResults, credits);
    }
}
