package portfolioboss.exchangerates;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import portfolioboss.exceptions.ExchangeRatesUnavailableException;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The Bank of Israel's representative exchange rates (CAPITAL_GAINS_TAX_TODO.md, decision 10), from its public SDMX
 * service: a currency's rates of a period, in one call, as CSV. Only the currency's code and the two dates leave the app
 * — public data, no key, nothing about the portfolio. Plain HTTPS through Spring's {@code RestClient}, like
 * {@code stocks.FinnhubClient}.
 *
 * <p>A period in which the Bank of Israel published no rate — a weekend, a holiday — is answered with HTTP 404 and an
 * HTML page, not an empty CSV; and so is a series that doesn't exist (there is none for the Hong Kong dollar). So a 404
 * reads as "no rate in these days" — unless the period is longer than any stretch without a rate ever was, which says
 * the series is not there any more.
 */
@Component
public class BankOfIsraelClient {

    /** {@code RER_USD_ILS} is the dollar's series: the representative rate, in shekels for one dollar. */
    private static final String RATES_URL = "https://edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow"
            + "/BOI.STATISTICS/EXR/1.0/RER_{currency}_ILS?startperiod={fromDate}&endperiod={toDate}&format=csv";
    /** How long one call may take; the dollar's rates of 2020–2026 came in one second (100 kilobytes). */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    /** A period of more days than this without a single rate is no holiday: twice the longest stretch ever seen. */
    private static final int MAX_DAYS_WITHOUT_ANY_RATE = 2 * BankOfIsraelRate.LONGEST_GAP_BETWEEN_RATES_IN_DAYS;

    private final RestClient restClient;
    private final BankOfIsraelRatesReader ratesReader = new BankOfIsraelRatesReader();

    /** The one Spring uses: calls with a time limit. */
    @Autowired
    private BankOfIsraelClient() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(CALL_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** For the tests: a builder bound to a fake server, so that no call reaches the Bank of Israel. */
    protected BankOfIsraelClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /**
     * The rates the Bank of Israel published for the currency in these days, in date order; empty when it published
     * none in them (a weekend, a holiday, today's rate not out yet).
     *
     * @param currency the currency's code, "USD"
     * @throws ExchangeRatesUnavailableException when no answer came, the answer was an HTTP error, a period of more than
     *                                           two weeks had no rate at all, or the answer couldn't be read
     */
    protected List<BankOfIsraelRate> exchangeRates(String currency, ExchangeRateDownloadPeriod period) {
        String csvText;
        try {
            csvText = restClient.get()
                    .uri(RATES_URL, currency, period.fromDate(), period.toDate())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return noRatesPublishedIn(currency, period);
        } catch (RestClientResponseException e) {
            // Not Spring's message: it would hold the HTML page the Bank of Israel answers an error with.
            throw new ExchangeRatesUnavailableException("the Bank of Israel answered HTTP " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new ExchangeRatesUnavailableException("no answer from the Bank of Israel" + reasonOf(e));
        }
        return ratesReader.readRates(Objects.requireNonNullElse(csvText, ""));
    }

    /** The Bank of Israel's 404: a few days without a rate are holidays; many days mean the series is gone. */
    private List<BankOfIsraelRate> noRatesPublishedIn(String currency, ExchangeRateDownloadPeriod period) {
        if (period.dayCount() > MAX_DAYS_WITHOUT_ANY_RATE) {
            throw new ExchangeRatesUnavailableException("the Bank of Israel has no " + currency + " rate in the "
                    + period.dayCount() + " days of " + period + ": has the series RER_" + currency
                    + "_ILS been renamed?");
        }
        return List.of();
    }

    /** Why the call failed — "Read timed out", "Connection refused" —, from the cause Spring wrapped. */
    private String reasonOf(RestClientException failure) {
        Throwable cause = failure.getMostSpecificCause();
        if (cause == failure || cause.getMessage() == null) {
            return "";
        }
        return " (" + cause.getMessage() + ")";
    }
}
