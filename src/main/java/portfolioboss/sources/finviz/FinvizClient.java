package portfolioboss.sources.finviz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import portfolioboss.exceptions.FinvizUnavailableException;
import portfolioboss.utils.Utils;

import java.time.Duration;
import java.util.List;

/**
 * Finviz Elite's export of a screen as CSV (RECOMMENDATIONS_FINVIZ_TODO.md, decisions 3 and 9). Read only: it sends
 * Finviz the screen's filters, its order and the symbols asked about — nothing else about the portfolio (decision 7).
 * The token — {@code FINVIZ_TOKEN} in the git-ignored {@code config/local.env}, or an environment variable of the same
 * name, which wins — goes in the address, so no failure of this class carries the address: each one becomes a
 * {@link FinvizUnavailableException} with a message of its own. Without the token Finviz is off and the app works as
 * usual: callers ask {@link #hasToken()} first. The token itself is never printed.
 */
@Component
public class FinvizClient {

    /** The name the screens show beside its answer: "From Finviz, answered …". */
    public static final String SOURCE_NAME = "Finviz";

    private static final String TOKEN_KEY_NAME = "FINVIZ_TOKEN";
    /**
     * Where a screen is exported, and the name of the token's parameter: the export as third-party libraries call it.
     * Not seen before the subscription — session 0 checks both against the documentation of the Elite account, where the
     * blog of 7.10.2026 points to a newer path ({@code /api/v1/screener-export-csv}).
     */
    private static final String SCREEN_EXPORT_URL = "https://elite.finviz.com/export.ashx";
    private static final String TOKEN_PARAMETER = "auth";
    /** Finviz's "Custom" view: the columns are the ones {@link #COLUMNS} names. */
    private static final String CUSTOM_VIEW = "151";
    /**
     * The columns of every screen, by Finviz's numbers (decision 12, the user's): the plan's twenty, the PEG (9) and the
     * P/B (11) that two lists are sorted by (decision 13), then the debt to equity, the earnings date, the analysts' target
     * and their rating. {@code FinvizScreenerReader} finds each one by its name, so their order doesn't matter.
     */
    private static final String COLUMNS = "0,1,2,3,4,5,6,7,65,66,67,8,9,10,11,13,19,21,23,39,40,41,38,68,69,62";
    /** A screen of a few hundred stocks is one answer; a slower one fails. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    /** Finviz allows 10 requests in 5 seconds, and says how long to wait in {@code Retry-After} (5 seconds). */
    private static final Duration DEFAULT_WAIT_AFTER_TOO_MANY_REQUESTS = Duration.ofSeconds(5);
    private static final Duration LONGEST_WAIT_AFTER_TOO_MANY_REQUESTS = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String finvizToken;

    /**
     * The one Spring uses: calls with a time limit. {@code ${FINVIZ_TOKEN:}} is the setting of that name, or nothing when
     * there is none — without the colon a missing token would stop the whole app from starting.
     */
    @Autowired
    private FinvizClient(@Value("${FINVIZ_TOKEN:}") String finvizToken) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(CALL_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.finvizToken = Utils.trimmedOrNull(finvizToken);
    }

    /** For the tests: a builder bound to a fake server, so that no call reaches Finviz. */
    protected FinvizClient(RestClient.Builder restClientBuilder, String finvizToken) {
        this.restClient = restClientBuilder.build();
        this.finvizToken = Utils.trimmedOrNull(finvizToken);
    }

    /** {@code false} when the token is not set, or set to nothing but spaces. */
    public boolean hasToken() {
        return finvizToken != null;
    }

    /**
     * One screen as Finviz exports it, in its custom view with {@link #COLUMNS}. A refusal because of Finviz's limit of
     * requests is tried once more, after the wait Finviz asks for.
     *
     * @param filters as Finviz writes them in a screen's address ("cap_smallover,fa_pe_o50"); empty for none
     * @param order   as Finviz writes it ("-sales5years": highest first); {@code null} for Finviz's own order
     * @param symbols only these stocks, as Finviz writes them ("BRK-B"); empty for every stock the filters pass
     * @throws FinvizUnavailableException when Finviz refuses, doesn't answer, or answers with an error
     */
    public String screenAsCsv(String filters, String order, List<String> symbols) {
        try {
            return callFinviz(filters, order, symbols);
        } catch (RestClientResponseException e) {
            if (!isTooManyRequests(e)) {
                throw failureOf(e);
            }
            waitBeforeTryingAgain(waitAskedForIn(e));
        }
        try {
            return callFinviz(filters, order, symbols);
        } catch (RestClientResponseException e) {
            if (isTooManyRequests(e)) {
                throw new FinvizUnavailableException(
                        "Finviz refused two requests in a row: its limit is 10 requests in 5 seconds (HTTP 429)");
            }
            throw failureOf(e);
        }
    }

    /**
     * One call. An answer with an error status is passed on as Spring's exception, for {@link #screenAsCsv} to look at —
     * its message is the status and the answer's body, without the address. Any other failure becomes a message of its
     * own: Spring's messages name the address, and the token is part of it.
     */
    private String callFinviz(String filters, String order, List<String> symbols) {
        try {
            return restClient.get()
                    .uri(SCREEN_EXPORT_URL, uriBuilder -> {
                        uriBuilder.queryParam("v", CUSTOM_VIEW);
                        if (!filters.isEmpty()) {
                            uriBuilder.queryParam("f", filters);
                        }
                        if (order != null) {
                            uriBuilder.queryParam("o", order);
                        }
                        if (!symbols.isEmpty()) {
                            uriBuilder.queryParam("t", String.join(",", symbols));
                        }
                        uriBuilder.queryParam("c", COLUMNS);
                        uriBuilder.queryParam(TOKEN_PARAMETER, finvizToken);
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw e;
        } catch (RestClientException e) {
            throw new FinvizUnavailableException("no answer from Finviz" + reasonWithoutTheAddress(e));
        }
    }

    private boolean isTooManyRequests(RestClientResponseException answer) {
        return answer.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    /** The status alone: the answer's body may be a whole page, and is never printed. */
    private FinvizUnavailableException failureOf(RestClientResponseException answer) {
        int status = answer.getStatusCode().value();
        if (status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value()) {
            return new FinvizUnavailableException("Finviz refused the token (HTTP " + status
                    + "): is the Elite subscription still active, and is " + TOKEN_KEY_NAME + " the account's token?");
        }
        return new FinvizUnavailableException("Finviz answered HTTP " + status);
    }

    /** {@code Retry-After} in seconds, as Finviz sends it; 5 seconds without one, and never more than 10. */
    private Duration waitAskedForIn(RestClientResponseException answer) {
        HttpHeaders headers = answer.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return DEFAULT_WAIT_AFTER_TOO_MANY_REQUESTS;
        }
        try {
            Duration waitAskedFor = Duration.ofSeconds(Long.parseLong(retryAfter.trim()));
            return waitAskedFor.compareTo(LONGEST_WAIT_AFTER_TOO_MANY_REQUESTS) > 0
                    ? LONGEST_WAIT_AFTER_TOO_MANY_REQUESTS
                    : waitAskedFor;
        } catch (NumberFormatException e) {
            return DEFAULT_WAIT_AFTER_TOO_MANY_REQUESTS;
        }
    }

    private void waitBeforeTryingAgain(Duration wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FinvizUnavailableException("stopped while waiting to ask Finviz again");
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
        return " (" + cause.getMessage().replace(finvizToken, "<" + TOKEN_KEY_NAME + ">") + ")";
    }

    /** Spring calls this once the app is up, after the TWS sync and the UI's start. */
    @EventListener(ApplicationReadyEvent.class)
    private void printStatus() {
        if (!hasToken()) {
            System.out.println("[finviz] Finviz off: " + TOKEN_KEY_NAME + " is not set (config/local.env)");
        }
    }
}
