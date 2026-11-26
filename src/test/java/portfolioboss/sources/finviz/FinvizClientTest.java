package portfolioboss.sources.finviz;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import portfolioboss.exceptions.FinvizUnavailableException;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The export of a screen against a fake Finviz: {@code MockRestServiceServer} answers the client's requests itself, so
 * nothing reaches the network. The address and the token's parameter are the ones the client expects before the
 * subscription (RECOMMENDATIONS_FINVIZ_TODO.md, session 0 checks them). The token is in the address, so every failure is
 * checked not to carry it.
 */
class FinvizClientTest {

    private static final String TEST_TOKEN = "finviz-test-token";
    private static final String EXPORT_URL = "https://elite.finviz.com/export.ashx";
    private static final String A_SCREEN = "\"Ticker\"\n\"ABCD\"\n";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer fakeFinviz = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final FinvizClient finvizClient = new FinvizClient(restClientBuilder, TEST_TOKEN);

    @Test
    void asksForTheScreenWithItsFiltersOrderColumnsAndToken() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL + "?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("v", "151"))
                .andExpect(queryParam("f", "cap_smallover,fa_pe_o50"))
                .andExpect(queryParam("o", "-sales5years"))
                .andExpect(queryParam("c", "0,1,2,3,4,5,6,7,65,66,67,8,9,10,11,13,19,21,23,39,40,41,38,68,69,62"))
                .andExpect(queryParam("auth", TEST_TOKEN))
                .andRespond(withSuccess(A_SCREEN, MediaType.TEXT_PLAIN));

        String csv = finvizClient.screenAsCsv("cap_smallover,fa_pe_o50", "-sales5years", List.of());

        assertThat(csv).isEqualTo(A_SCREEN);
        fakeFinviz.verify();
    }

    /** A screen of the holdings: no filters and no order, only the symbols, as Finviz writes them. */
    @Test
    void theSymbolsGoInTheirOwnParameterAndNoFilterOrOrderIsSent() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL + "?")))
                .andExpect(queryParam("t", "ABCD,BRK-B"))
                .andExpect(hasNoParameter("f"))
                .andExpect(hasNoParameter("o"))
                .andRespond(withSuccess(A_SCREEN, MediaType.TEXT_PLAIN));

        finvizClient.screenAsCsv("", null, List.of("ABCD", "BRK-B"));

        fakeFinviz.verify();
    }

    /** A subscription that ended: Finviz refuses the token, and the message says what to check — without the token. */
    @Test
    void aRefusedTokenSaysSoWithoutTheToken() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> finvizClient.screenAsCsv("", null, List.of("ABCD")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz refused the token (HTTP 401): is the Elite subscription still active, and is "
                        + "FINVIZ_TOKEN the account's token?")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    /** Finviz's limit is 10 requests in 5 seconds: a refusal for it is tried once more, after the wait it asks for. */
    @Test
    void tooManyRequestsIsTriedOnceMoreAfterTheWaitFinvizAsksFor() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL))).andRespond(withSuccess(A_SCREEN, MediaType.TEXT_PLAIN));

        String csv = finvizClient.screenAsCsv("", null, List.of("ABCD"));

        assertThat(csv).isEqualTo(A_SCREEN);
        fakeFinviz.verify();
    }

    @Test
    void tooManyRequestsTwiceInARowFails() {
        fakeFinviz.expect(twice(), requestTo(startsWith(EXPORT_URL)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));

        assertThatThrownBy(() -> finvizClient.screenAsCsv("", null, List.of("ABCD")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz refused two requests in a row: its limit is 10 requests in 5 seconds (HTTP 429)");
        fakeFinviz.verify();
    }

    @Test
    void anotherErrorSaysItsStatusOnly() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>an error page</html>"));

        assertThatThrownBy(() -> finvizClient.screenAsCsv("", null, List.of("ABCD")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz answered HTTP 500");
    }

    /** Spring's own message would name the address, and the token with it. */
    @Test
    void noAnswerSaysWhyWithoutTheAddress() {
        fakeFinviz.expect(requestTo(startsWith(EXPORT_URL)))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> finvizClient.screenAsCsv("", null, List.of("ABCD")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("no answer from Finviz (Read timed out)")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    @Test
    void aTokenOfNothingButSpacesIsNoToken() {
        assertThat(new FinvizClient(RestClient.builder(), "   ").hasToken()).isFalse();
        assertThat(new FinvizClient(RestClient.builder(), null).hasToken()).isFalse();
        assertThat(finvizClient.hasToken()).isTrue();
    }

    private RequestMatcher hasNoParameter(String name) {
        return request -> assertThat(UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams())
                .doesNotContainKey(name);
    }
}
