package portfolioboss.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Decision 17 of AI_ANALYSIS_TODO.md: only results that name the stock go to Claude. */
class SearchResultsTest {

    /** AEVA in session 0: a search by symbol alone also returned results about other companies. */
    @Test
    void aResultAboutAnotherCompanyFallsOut() {
        SearchResult aboutAeva = result("Aeva (NYSE: AEVA) price target raised", "Analysts lifted their target.");
        SearchResult aboutFedex = result("FedEx stock forecast", "FDX analysts expect growth.");

        SearchResults filtered = new SearchResults(List.of(aboutAeva, aboutFedex), 3).mentioning("AEVA");

        assertThat(filtered.results()).containsExactly(aboutAeva);
    }

    @Test
    void theSymbolInsideALongerWordDoesNotCount() {
        SearchResults filtered = new SearchResults(List.of(result("AEVAX fund update", "The AEVAX fund rose.")), 1)
                .mentioning("AEVA");

        assertThat(filtered.results()).isEmpty();
    }

    @Test
    void theSymbolAsAWholeWordCountsInTheTitleOrTheTextInAnyCase() {
        List<SearchResult> mentioningResults = List.of(
                result("$AEVA jumps 12%", "Shares rose."),
                result("Lidar maker news", "Shares of AEVA fell on Monday."),
                result("Aeva Technologies wins a contract", "The deal is worth $50 million."));

        SearchResults filtered = new SearchResults(mentioningResults, 1).mentioning("AEVA");

        assertThat(filtered.results()).isEqualTo(mentioningResults);
    }

    @Test
    void theCreditsStayEvenWhenEveryResultFallsOut() {
        SearchResults filtered = new SearchResults(List.of(result("FedEx stock forecast", "FDX news.")), 2)
                .mentioning("AEVA");

        assertThat(filtered.results()).isEmpty();
        assertThat(filtered.credits()).isEqualTo(2);
    }

    private SearchResult result(String title, String content) {
        return new SearchResult(title, "https://example.com/" + title.length(), null, content);
    }
}
