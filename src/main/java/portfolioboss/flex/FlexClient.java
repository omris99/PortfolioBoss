package portfolioboss.flex;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import portfolioboss.exceptions.FlexReportUnavailableException;
import portfolioboss.utils.Utils;

import java.time.Duration;
import java.time.format.DateTimeFormatter;

/**
 * IB's Flex Web Service (CAPITAL_GAINS_TAX_TODO.md, decision 13): a report of the account's trades and cash
 * transactions for a period, in two calls — {@code SendRequest} asks IB to prepare the report and answers with a
 * reference code, {@code GetStatement} fetches it by that code once it is ready. Read only: the service gives reports
 * and nothing else. Plain HTTPS through Spring's {@code RestClient}, like {@code stocks.FinnhubClient}.
 *
 * <p>The token and the query's number — {@code FLEX_TOKEN} and {@code FLEX_QUERY_ID} in the git-ignored
 * {@code config/local.env}, or environment variables of the same names, which win — are all that leaves the app, with
 * the two dates of the period. IB takes the token only in the address ({@code t=}), and Spring's errors print the address
 * they failed on, so no error of this class carries it: {@link #callFlexWebService} turns every failure into a message
 * of its own. Without the keys no Flex report is downloaded, and the rest of the app works as usual.
 */
@Component
public class FlexClient {

    private static final String TOKEN_KEY_NAME = "FLEX_TOKEN";
    private static final String QUERY_ID_KEY_NAME = "FLEX_QUERY_ID";
    /** {@code fd} and {@code td} take the place of the query's own period; {@code v=3} is the service's version. */
    private static final String SEND_REQUEST_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement"
            + "/FlexWebService/SendRequest?t={token}&q={queryId}&fd={fromDate}&td={toDate}&v=3";
    private static final String GET_STATEMENT_URL = "https://ndcdyn.interactivebrokers.com/AccountManagement"
            + "/FlexWebService/GetStatement?t={token}&q={referenceCode}&v=3";
    private static final DateTimeFormatter IB_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    /** How long one call may take; a year's report is a few hundred kilobytes. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    /**
     * How long to give IB to prepare the report before each try to fetch it. Ten seconds keep the tries at six a
     * minute, under IB's limit of ten requests a minute per token.
     */
    private static final Duration WAIT_BEFORE_EACH_FETCH = Duration.ofSeconds(10);
    /** Twelve tries of ten seconds: two minutes for IB to prepare a report. */
    private static final int MAX_FETCH_TRIES = 12;

    private final RestClient restClient;
    private final String flexToken;
    private final String flexQueryId;
    private final Duration waitBeforeEachFetch;
    private final FlexReportReader flexReportReader = new FlexReportReader();

    /**
     * The one Spring uses. {@code ${FLEX_TOKEN:}} is the setting of that name, or nothing when there is none — without
     * the colon a missing key would stop the whole app from starting.
     */
    @Autowired
    private FlexClient(@Value("${FLEX_TOKEN:}") String flexToken, @Value("${FLEX_QUERY_ID:}") String flexQueryId) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(CALL_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.flexToken = Utils.trimmedOrNull(flexToken);
        this.flexQueryId = Utils.trimmedOrNull(flexQueryId);
        this.waitBeforeEachFetch = WAIT_BEFORE_EACH_FETCH;
    }

    /** For the tests: a builder bound to a fake server, so that no call reaches IB, and the wait before each fetch. */
    protected FlexClient(RestClient.Builder restClientBuilder, String flexToken, String flexQueryId,
                         Duration waitBeforeEachFetch) {
        this.restClient = restClientBuilder.build();
        this.flexToken = Utils.trimmedOrNull(flexToken);
        this.flexQueryId = Utils.trimmedOrNull(flexQueryId);
        this.waitBeforeEachFetch = waitBeforeEachFetch;
    }

    /** {@code false} when either key is not set, or set to nothing but spaces. */
    public boolean hasKeys() {
        return missingKeyName() == null;
    }

    /**
     * The report of these days: asked for, then fetched once IB has prepared it.
     *
     * @throws FlexReportUnavailableException when IB refused it, it wasn't ready in two minutes, no answer came, or the
     *                                        answer couldn't be read
     * @throws InterruptedException           when the thread is asked to stop while waiting for the report
     */
    public FlexReport downloadReport(FlexReportPeriod requestedPeriod) throws InterruptedException {
        String referenceCode = requestReport(requestedPeriod);
        return fetchReportOnceReady(referenceCode);
    }

    /** {@code SendRequest}: IB starts preparing the report and answers with the code to fetch it by. */
    private String requestReport(FlexReportPeriod requestedPeriod) {
        byte[] answerXml = callFlexWebService(SEND_REQUEST_URL, flexToken, flexQueryId,
                IB_DATE_FORMAT.format(requestedPeriod.fromDate()), IB_DATE_FORMAT.format(requestedPeriod.toDate()));
        FlexReportRequestStatus requestStatus = flexReportReader.readRequestStatus(answerXml);
        if (!requestStatus.wasAccepted()) {
            throw new FlexReportUnavailableException("IB refused the request: " + requestStatus.errorCodeAndMessage());
        }
        if (requestStatus.referenceCode() == null) {
            throw new FlexReportUnavailableException("IB accepted the request without a code to fetch the report by");
        }
        return requestStatus.referenceCode();
    }

    /**
     * {@code GetStatement}, after a wait, until the answer is the report. While IB answers "please try again shortly" —
     * the report is still being prepared, too many requests came at once — it is tried again, up to two minutes; any
     * other answer is a refusal and ends it at once.
     */
    private FlexReport fetchReportOnceReady(String referenceCode) throws InterruptedException {
        FlexReportRequestStatus lastRequestStatus = null;
        for (int fetchTry = 1; fetchTry <= MAX_FETCH_TRIES; fetchTry++) {
            Thread.sleep(waitBeforeEachFetch);
            byte[] answerXml = callFlexWebService(GET_STATEMENT_URL, flexToken, referenceCode);
            if (flexReportReader.isReport(answerXml)) {
                return flexReportReader.readReport(answerXml);
            }
            lastRequestStatus = flexReportReader.readRequestStatus(answerXml);
            if (!lastRequestStatus.saysTryAgainShortly()) {
                throw new FlexReportUnavailableException("IB refused the report: "
                        + lastRequestStatus.errorCodeAndMessage());
            }
        }
        throw new FlexReportUnavailableException("the report was still not ready after " + MAX_FETCH_TRIES
                + " tries: " + lastRequestStatus.errorCodeAndMessage());
    }

    /**
     * One call to the service, its answer as the bytes IB sent — the XML reader finds their encoding in the XML itself.
     * A failure becomes a {@link FlexReportUnavailableException} without the address: Spring's own messages name it, and
     * the token is part of it.
     */
    private byte[] callFlexWebService(String urlTemplate, Object... urlVariables) {
        try {
            return restClient.get()
                    .uri(urlTemplate, urlVariables)
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw new FlexReportUnavailableException("IB's Flex Web Service answered HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new FlexReportUnavailableException("no answer from IB's Flex Web Service"
                    + reasonWithoutTheAddress(e));
        }
    }

    /**
     * Why the call failed — "Read timed out", "Connection refused" —, from the cause Spring wrapped, whose message has no
     * address; the token is blanked out all the same, in case a cause ever quotes it.
     */
    private String reasonWithoutTheAddress(RestClientException failure) {
        Throwable cause = failure.getMostSpecificCause();
        if (cause == failure || cause.getMessage() == null) {
            return "";
        }
        return " (" + cause.getMessage().replace(flexToken, "<FLEX_TOKEN>") + ")";
    }

    /** The first key not set, or {@code null} when both are. */
    private String missingKeyName() {
        if (flexToken == null) {
            return TOKEN_KEY_NAME;
        }
        if (flexQueryId == null) {
            return QUERY_ID_KEY_NAME;
        }
        return null;
    }

    /** Spring calls this once the app is up, after the TWS sync and the UI's start. */
    @EventListener(ApplicationReadyEvent.class)
    private void printStatus() {
        if (!hasKeys()) {
            System.out.println("[tax] Flex off: " + missingKeyName() + " is not set (config/local.env)");
        }
    }
}
