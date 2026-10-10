package portfolioboss.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The four searches of AI_ANALYSIS_TODO.md, decisions 2, 19 and 21, against a fake Tavily: {@code MockRestServiceServer} answers
 * the client's requests itself, so nothing reaches the network or costs a credit. The answers are in the shape of
 * Tavily's documentation, with fields the client doesn't use, to show they are ignored.
 */
class TavilyClientTest {

    private static final String SEARCH_URL = "https://api.tavily.com/search";
    private static final String TEST_KEY = "tvly-test-key";

    private static final String ANALYST_ANSWER = """
            {
              "query": "AAPL stock forecast analyst consensus rating buy hold sell average price target",
              "results": [
                {
                  "url": "https://financhill.com/aapl",
                  "title": "Apple (AAPL) Stock Forecast",
                  "content": "48 analysts: 30 Buy, 16 Hold, 2 Sell. Average price target $328.22.",
                  "score": 0.91,
                  "published_date": "2026-10-05",
                  "id": "result-1"
                },
                {
                  "url": "https://stockanalysis.com/stocks/aapl/forecast/",
                  "title": "AAPL Stock Forecast & Price Target",
                  "content": "The average price target of 44 analysts is $328.09.",
                  "score": 0.85,
                  "id": "result-2"
                }
              ],
              "response_time": 1.67,
              "usage": { "credits": 2 },
              "request_id": "123e4567-e89b-12d3-a456-426614174111"
            }
            """;

    private static final String MARKETBEAT_ANSWER = """
            {
              "query": "AAPL analyst price target upgrade downgrade",
              "results": [
                {
                  "url": "https://www.marketbeat.com/instant-alerts/filing-apple-inc-aapl-shares-sold-2026-10-09",
                  "title": "Apple Inc. $AAPL Shares Sold by Some Fund",
                  "content": "Morgan Stanley set a $355.00 price objective on shares of Apple in a report on Thursday.",
                  "score": 0.88,
                  "published_date": "Fri, 09 Oct 2026 07:28:41 GMT"
                }
              ],
              "usage": { "credits": 1 }
            }
            """;

    private static final String NEWS_ANSWER = """
            {
              "query": "AAPL stock news",
              "results": [],
              "response_time": 0.8,
              "usage": { "credits": 1 }
            }
            """;

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer fakeTavily = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final TavilyClient tavilyClient = new TavilyClient(restClientBuilder, new AiKeys(TEST_KEY, null));

    @Test
    void theAnalystSearchAsksForForecastPagesOfTheLastMonth() {
        fakeTavily.expect(requestTo(SEARCH_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query")
                        .value("AAPL stock forecast analyst consensus rating buy hold sell average price target"))
                .andExpect(jsonPath("$.topic").value("general"))
                .andExpect(jsonPath("$.time_range").value("month"))
                .andExpect(jsonPath("$.search_depth").value("advanced"))
                .andExpect(jsonPath("$.chunks_per_source").value(3))
                .andExpect(jsonPath("$.max_results").value(5))
                .andExpect(jsonPath("$.include_published_date").value(true))
                .andExpect(jsonPath("$.include_usage").value(true))
                .andExpect(jsonPath("$.include_raw_content").doesNotExist())
                .andRespond(withSuccess(ANALYST_ANSWER, MediaType.APPLICATION_JSON));

        SearchResults searchResults = tavilyClient.searchAnalystForecasts("AAPL");

        fakeTavily.verify();
        assertThat(searchResults.credits()).isEqualTo(2);
        assertThat(searchResults.results()).containsExactly(
                new SearchResult("Apple (AAPL) Stock Forecast", "https://financhill.com/aapl", "2026-10-05",
                        "48 analysts: 30 Buy, 16 Hold, 2 Sell. Average price target $328.22."),
                new SearchResult("AAPL Stock Forecast & Price Target", "https://stockanalysis.com/stocks/aapl/forecast/",
                        null, "The average price target of 44 analysts is $328.09."));
    }

    /**
     * Decisions 19 and 21: MarketBeat's news articles of the last month, for 1 credit — new addresses, not a forecast
     * page stuck in the search engine's copy — and still only the symbol leaves the app.
     */
    @Test
    void theMarketBeatSearchAsksForItsArticlesOfTheLastMonth() {
        fakeTavily.expect(requestTo(SEARCH_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(jsonPath("$.query").value("AAPL analyst price target upgrade downgrade"))
                .andExpect(jsonPath("$.topic").value("news"))
                .andExpect(jsonPath("$.time_range").value("month"))
                .andExpect(jsonPath("$.search_depth").value("basic"))
                .andExpect(jsonPath("$.include_domains").value("marketbeat.com"))
                .andExpect(jsonPath("$.max_results").value(5))
                .andExpect(jsonPath("$.include_published_date").value(true))
                .andExpect(jsonPath("$.include_usage").value(true))
                .andExpect(jsonPath("$.include_raw_content").doesNotExist())
                .andRespond(withSuccess(MARKETBEAT_ANSWER, MediaType.APPLICATION_JSON));

        SearchResults searchResults = tavilyClient.searchMarketBeatAnalystActions("AAPL");

        fakeTavily.verify();
        assertThat(searchResults.credits()).isEqualTo(1);
        assertThat(searchResults.results()).extracting(SearchResult::url).containsExactly(
                "https://www.marketbeat.com/instant-alerts/filing-apple-inc-aapl-shares-sold-2026-10-09");
    }

    /** Decision 21: the week's news about analysts' actions, on any site, for 1 credit. */
    @Test
    void theLatestActionsSearchAsksForTheWeeksNewsOnAnySite() {
        fakeTavily.expect(requestTo(SEARCH_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(jsonPath("$.query").value("AAPL analyst price target upgrade downgrade"))
                .andExpect(jsonPath("$.topic").value("news"))
                .andExpect(jsonPath("$.time_range").value("week"))
                .andExpect(jsonPath("$.search_depth").value("basic"))
                .andExpect(jsonPath("$.include_domains").doesNotExist())
                .andExpect(jsonPath("$.max_results").value(5))
                .andExpect(jsonPath("$.include_published_date").value(true))
                .andRespond(withSuccess(NEWS_ANSWER, MediaType.APPLICATION_JSON));

        SearchResults searchResults = tavilyClient.searchLatestAnalystActions("AAPL");

        fakeTavily.verify();
        assertThat(searchResults.credits()).isEqualTo(1);
    }

    @Test
    void theNewsSearchAsksForTheWeeksNews() {
        fakeTavily.expect(requestTo(SEARCH_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_KEY))
                .andExpect(jsonPath("$.query").value("AAPL stock news"))
                .andExpect(jsonPath("$.topic").value("news"))
                .andExpect(jsonPath("$.time_range").value("week"))
                .andExpect(jsonPath("$.search_depth").value("basic"))
                .andExpect(jsonPath("$.chunks_per_source").doesNotExist())
                .andExpect(jsonPath("$.max_results").value(5))
                .andExpect(jsonPath("$.include_published_date").value(true))
                .andRespond(withSuccess(NEWS_ANSWER, MediaType.APPLICATION_JSON));

        SearchResults searchResults = tavilyClient.searchNews("AAPL");

        fakeTavily.verify();
        assertThat(searchResults.results()).isEmpty();
        assertThat(searchResults.credits()).isEqualTo(1);
    }

    /** A wrong key, an exhausted plan: the search fails, and the stock with it — {@code AnalysisService} says why. */
    @Test
    void aRefusedSearchFails() {
        fakeTavily.expect(requestTo(SEARCH_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> tavilyClient.searchNews("AAPL"))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }
}
