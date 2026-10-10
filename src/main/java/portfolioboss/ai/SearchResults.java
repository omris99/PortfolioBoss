package portfolioboss.ai;

import java.util.List;
import java.util.stream.Stream;

/**
 * One Tavily search: its results, the credits it cost, and the addresses of the results the symbol filter left out
 * (AI_ANALYSIS_TODO.md, decision 22) — stored with the analysis, never sent to Claude, so that an empty search can be
 * told apart from one whose results were all about other companies.
 *
 * @param droppedUrls empty for a search as Tavily answered it, and in analyses stored before 2026-10-10
 */
public record SearchResults(List<SearchResult> results, int credits, List<String> droppedUrls) {

    /** A search as Tavily answered it: nothing left out yet. */
    public SearchResults(List<SearchResult> results, int credits) {
        this(results, credits, List.of());
    }

    /** An analysis stored before the addresses were kept has no {@code droppedUrls}: read as none. */
    public SearchResults {
        droppedUrls = droppedUrls == null ? List.of() : droppedUrls;
    }

    /**
     * Only the results that name the stock (AI_ANALYSIS_TODO.md, decision 17): a cheap guard against another company's
     * ratings ending up under this one, and fewer tokens. The addresses of the others are kept, and the credits stay —
     * they were spent either way.
     */
    public SearchResults mentioning(String symbol) {
        List<SearchResult> mentioningResults = results.stream()
                .filter(result -> result.mentions(symbol))
                .toList();
        List<String> newlyDroppedUrls = results.stream()
                .filter(result -> !result.mentions(symbol))
                .map(SearchResult::url)
                .toList();
        return new SearchResults(mentioningResults, credits,
                Stream.concat(droppedUrls.stream(), newlyDroppedUrls.stream()).toList());
    }
}
