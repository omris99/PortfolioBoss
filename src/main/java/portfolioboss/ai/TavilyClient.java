package portfolioboss.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The two Tavily searches of AI_ANALYSIS_TODO.md, decision 2, for one stock. Tavily is a search engine made for
 * language models: for each result it returns the title, the address, the date and the parts of the page that match
 * the search. Only the symbol leaves the app (decision 8). Plain HTTP through Spring's {@code RestClient}, no SDK.
 */
@Component
public class TavilyClient {

    private static final String SEARCH_URL = "https://api.tavily.com/search";
    /** How long one search may take; a slower one fails, and the stock with it. */
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESULTS = 5;

    private final RestClient restClient;
    private final AiKeys aiKeys;

    /** The one Spring uses: searches with a time limit. */
    @Autowired
    private TavilyClient(AiKeys aiKeys) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(SEARCH_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.aiKeys = aiKeys;
    }

    /** For the tests: a builder bound to a fake server, so that no search reaches Tavily. */
    protected TavilyClient(RestClient.Builder restClientBuilder, AiKeys aiKeys) {
        this.restClient = restClientBuilder.build();
        this.aiKeys = aiKeys;
    }

    /**
     * Forecast pages with the consensus, the average target and the latest analyst moves (MarketBeat, Financhill,
     * stockanalysis…). {@code general} rather than {@code finance}, which returned Yahoo pages without ratings in
     * session 0; {@code advanced} returns the 3 best-matching parts of each page, for 2 credits.
     */
    public SearchResults searchAnalystForecasts(String symbol) {
        return search(Map.of(
                "query", symbol + " stock forecast analyst consensus rating buy hold sell average price target",
                "topic", "general",
                "time_range", "month",
                "search_depth", "advanced",
                "chunks_per_source", 3,
                "max_results", MAX_RESULTS,
                "include_published_date", true,
                "include_usage", true));
    }

    /** The week's news, 1 credit. */
    public SearchResults searchNews(String symbol) {
        return search(Map.of(
                "query", symbol + " stock news",
                "topic", "news",
                "time_range", "week",
                "search_depth", "basic",
                "max_results", MAX_RESULTS,
                "include_published_date", true,
                "include_usage", true));
    }

    /** The parameters are Tavily's own names; {@code include_usage} makes it say what the search cost. */
    private SearchResults search(Map<String, Object> searchParameters) {
        TavilyResponse response = restClient.post()
                .uri(SEARCH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(aiKeys.tavilyApiKey()))
                .body(searchParameters)
                .retrieve()
                .body(TavilyResponse.class);
        return response.toSearchResults();
    }

    /** The part of Tavily's answer that is used. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyResponse(List<SearchResult> results, TavilyUsage usage) {

        private SearchResults toSearchResults() {
            List<SearchResult> searchResults = results == null ? List.of() : results;
            int credits = usage == null ? 0 : usage.credits();
            return new SearchResults(searchResults, credits);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TavilyUsage(int credits) {
    }
}
