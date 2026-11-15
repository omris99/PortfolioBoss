package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import portfolioboss.exceptions.FlexReportUnavailableException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The two calls of IB's Flex Web Service against a fake service: {@code MockRestServiceServer} answers the client's
 * requests itself, so no test calls IB. The client waits nothing between its tries here. The addresses are compared
 * whole — the token is in them, as IB wants it —, and every failure's message is checked for not holding it.
 */
class FlexClientTest {

    private static final String TEST_TOKEN = "flex-test-token";
    private static final String TEST_QUERY_ID = "123456";
    private static final String REFERENCE_CODE = "9876543210";
    private static final FlexReportPeriod REQUESTED_PERIOD =
            new FlexReportPeriod(LocalDate.of(2025, 11, 14), LocalDate.of(2026, 11, 13));
    private static final String SEND_REQUEST_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement"
            + "/FlexWebService/SendRequest?t=flex-test-token&q=123456&fd=20251114&td=20261113&v=3";
    private static final String GET_STATEMENT_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement"
            + "/FlexWebService/GetStatement?t=flex-test-token&q=9876543210&v=3";
    private static final String REPORT = """
            <FlexQueryResponse queryName="Cash and Trades" type="AF"><FlexStatements count="1">
            <FlexStatement fromDate="20251114" toDate="20261113"><Trades /><CashTransactions /></FlexStatement>
            </FlexStatements></FlexQueryResponse>
            """;

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer fakeFlexWebService = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final FlexClient flexClient = new FlexClient(restClientBuilder, TEST_TOKEN, TEST_QUERY_ID, Duration.ZERO);

    @Test
    void theReportIsFetchedOnceIbHasPreparedIt() throws InterruptedException {
        expectAcceptedRequest();
        expectFetchAnswering(statusOf("Warn", 1019, "Statement generation in progress. Please try again shortly."));
        expectFetchAnswering(REPORT);

        FlexReport report = flexClient.downloadReport(REQUESTED_PERIOD);

        fakeFlexWebService.verify();
        assertThat(report.period()).isEqualTo(REQUESTED_PERIOD);
    }

    /** IB allows ten requests a minute per token; one more is "try again shortly", not a refusal. */
    @Test
    void tooManyRequestsIsTriedAgain() throws InterruptedException {
        expectAcceptedRequest();
        expectFetchAnswering(statusOf("Warn", 1018,
                "Too many requests have been made from this token. Please try again shortly."));
        expectFetchAnswering(REPORT);

        flexClient.downloadReport(REQUESTED_PERIOD);

        fakeFlexWebService.verify();
    }

    /** No fetch follows a refused request: the fake service would fail the test on a request it doesn't expect. */
    @Test
    void aRefusedRequestFailsAtOnceWithIbsWords() {
        fakeFlexWebService.expect(requestTo(SEND_REQUEST_URL))
                .andRespond(withSuccess(statusOf("Fail", 1012, "Token has expired."), MediaType.TEXT_XML));

        assertThatThrownBy(() -> flexClient.downloadReport(REQUESTED_PERIOD))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("IB refused the request: 1012 Token has expired.");
        fakeFlexWebService.verify();
    }

    @Test
    void aRefusedReportIsNotTriedAgain() {
        expectAcceptedRequest();
        expectFetchAnswering(statusOf("Fail", 1014, "Query is invalid."));

        assertThatThrownBy(() -> flexClient.downloadReport(REQUESTED_PERIOD))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("IB refused the report: 1014 Query is invalid.");
        fakeFlexWebService.verify();
    }

    @Test
    void aReportStillNotReadyAfterEveryTryFails() {
        expectAcceptedRequest();
        fakeFlexWebService.expect(times(12), requestTo(GET_STATEMENT_URL))
                .andRespond(withSuccess(statusOf("Warn", 1019,
                        "Statement generation in progress. Please try again shortly."), MediaType.TEXT_XML));

        assertThatThrownBy(() -> flexClient.downloadReport(REQUESTED_PERIOD))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessageStartingWith("the report was still not ready after 12 tries: 1019");
        fakeFlexWebService.verify();
    }

    /** Spring's own message would name the address, and with it the token; the client's names only the cause. */
    @Test
    void noAnswerFailsWithoutTheTokenInTheMessage() {
        fakeFlexWebService.expect(requestTo(SEND_REQUEST_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> flexClient.downloadReport(REQUESTED_PERIOD))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("no answer from IB's Flex Web Service (Read timed out)")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    @Test
    void anHttpErrorFailsWithoutTheTokenInTheMessage() {
        fakeFlexWebService.expect(requestTo(SEND_REQUEST_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> flexClient.downloadReport(REQUESTED_PERIOD))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("IB's Flex Web Service answered HTTP 503")
                .hasMessageNotContaining(TEST_TOKEN);
    }

    /** A key of nothing but spaces is no key, and both are needed. */
    @Test
    void theKeysAreSetOnlyWhenBothHaveText() {
        assertThat(flexClient.hasKeys()).isTrue();
        assertThat(new FlexClient(RestClient.builder(), "  ", TEST_QUERY_ID, Duration.ZERO).hasKeys()).isFalse();
        assertThat(new FlexClient(RestClient.builder(), TEST_TOKEN, null, Duration.ZERO).hasKeys()).isFalse();
    }

    private void expectAcceptedRequest() {
        fakeFlexWebService.expect(requestTo(SEND_REQUEST_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(acceptedRequestStatus(), MediaType.TEXT_XML));
    }

    private String acceptedRequestStatus() {
        return """
                <FlexStatementResponse timestamp="15 November, 2026 08:30 AM EST">
                <Status>Success</Status>
                <ReferenceCode>%s</ReferenceCode>
                <Url>https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement</Url>
                </FlexStatementResponse>
                """.formatted(REFERENCE_CODE);
    }

    private void expectFetchAnswering(String answerXml) {
        fakeFlexWebService.expect(requestTo(GET_STATEMENT_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(answerXml, MediaType.TEXT_XML));
    }

    private String statusOf(String status, int errorCode, String errorMessage) {
        return """
                <FlexStatementResponse timestamp="15 November, 2026 08:30 AM EST">
                <Status>%s</Status>
                <ErrorCode>%d</ErrorCode>
                <ErrorMessage>%s</ErrorMessage>
                </FlexStatementResponse>
                """.formatted(status, errorCode, errorMessage);
    }
}
