package portfolioboss.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The four searches of one stock together: what they cost, and only the results that name it (decision 17). */
class StockSearchesTest {

    private static final SearchResult ABOUT_APPLE = result("Apple (AAPL) price target raised");
    private static final SearchResult ABOUT_FEDEX = result("FedEx (FDX) price target cut");

    @Test
    void theCreditsOfTheFourSearchesAddUp() {
        StockSearches searches = new StockSearches(searchOf(2), searchOf(1), searchOf(1), searchOf(1));

        assertThat(searches.credits()).isEqualTo(5);
    }

    /**
     * Each search keeps only the results about the stock, the addresses of the others (decision 22), and its credits,
     * which were spent either way.
     */
    @Test
    void mentioningKeepsInEverySearchOnlyTheResultsAboutTheStock() {
        StockSearches searches = new StockSearches(searchOf(2, ABOUT_APPLE, ABOUT_FEDEX), searchOf(1, ABOUT_FEDEX),
                searchOf(1, ABOUT_APPLE), searchOf(1, ABOUT_FEDEX, ABOUT_APPLE));

        StockSearches aboutApple = searches.mentioning("AAPL");

        List<String> fedexLeftOut = List.of(ABOUT_FEDEX.url());
        assertThat(aboutApple).isEqualTo(new StockSearches(
                new SearchResults(List.of(ABOUT_APPLE), 2, fedexLeftOut),
                new SearchResults(List.of(), 1, fedexLeftOut),
                new SearchResults(List.of(ABOUT_APPLE), 1, List.of()),
                new SearchResults(List.of(ABOUT_APPLE), 1, fedexLeftOut)));
        assertThat(aboutApple.credits()).isEqualTo(5);
    }

    private static SearchResults searchOf(int credits, SearchResult... results) {
        return new SearchResults(List.of(results), credits);
    }

    private static SearchResult result(String title) {
        return new SearchResult(title, "https://example.com/" + title.length(), "2026-10-09", title + ".");
    }
}
