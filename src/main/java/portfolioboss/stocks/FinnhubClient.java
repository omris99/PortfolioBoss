package portfolioboss.stocks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import portfolioboss.utils.Utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;

/**
 * Finnhub (RECOMMENDATIONS_TODO.md, decision 8): a stock's next earnings report and four of the company's figures, both
 * on the free plan (60 calls a minute; a stock is 2). Only the symbol leaves the app. Plain HTTP through Spring's
 * {@code RestClient}, like {@code ai.TavilyClient}. The key — {@code FINNHUB_API_KEY} in the git-ignored
 * {@code config/local.env}, or an environment variable of the same name, which wins — goes in a header, not in the
 * address, so it is never part of an address an error prints. Without the key Finnhub's part is off and the rest of the
 * app works as usual: callers ask {@link #hasApiKey()} first. The key itself is never printed.
 */
@Component
public class FinnhubClient {

    private static final String KEY_NAME = "FINNHUB_API_KEY";
    private static final String KEY_HEADER = "X-Finnhub-Token";
    private static final String EARNINGS_CALENDAR_URL =
            "https://finnhub.io/api/v1/calendar/earnings?symbol={symbol}&from={from}&to={to}";
    private static final String COMPANY_METRICS_URL =
            "https://finnhub.io/api/v1/stock/metric?symbol={symbol}&metric=all";
    /** How long one call may take; a slower one fails. Finnhub answered in under a second in the experiment. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);
    /**
     * How far ahead the next report is looked for: a company reports every quarter, so a report made yesterday is
     * followed by the next one about three months later — four leave room for a late one.
     */
    private static final Period EARNINGS_LOOKAHEAD = Period.ofMonths(4);

    private final RestClient restClient;
    private final String finnhubApiKey;

    /**
     * The one Spring uses: calls with a time limit. {@code ${FINNHUB_API_KEY:}} is the setting of that name, or nothing
     * when there is none — without the colon a missing key would stop the whole app from starting.
     */
    @Autowired
    private FinnhubClient(@Value("${FINNHUB_API_KEY:}") String finnhubApiKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(CALL_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.finnhubApiKey = Utils.trimmedOrNull(finnhubApiKey);
    }

    /** For the tests: a builder bound to a fake server, so that no call reaches Finnhub. */
    protected FinnhubClient(RestClient.Builder restClientBuilder, String finnhubApiKey) {
        this.restClient = restClientBuilder.build();
        this.finnhubApiKey = Utils.trimmedOrNull(finnhubApiKey);
    }

    /** {@code false} when the key is not set, or set to nothing but spaces. */
    public boolean hasApiKey() {
        return finnhubApiKey != null;
    }

    /**
     * What Finnhub knows about the stock, in two calls: the earnings calendar and the company's figures. {@code today}
     * comes from the caller, so the answer doesn't depend on the clock.
     */
    public Stock stock(String symbol, LocalDate today) {
        return new Stock(symbol, nextEarningsDate(symbol, today), companyFacts(symbol));
    }

    /** The first report from {@code today} on, or {@code null} when there is none in the next four months. */
    private EarningsDate nextEarningsDate(String symbol, LocalDate today) {
        EarningsCalendarResponse response = restClient.get()
                .uri(EARNINGS_CALENDAR_URL, symbol, today, today.plus(EARNINGS_LOOKAHEAD))
                .header(KEY_HEADER, finnhubApiKey)
                .retrieve()
                .body(EarningsCalendarResponse.class);
        return response.earliestReport();
    }

    private CompanyFacts companyFacts(String symbol) {
        CompanyMetricsResponse response = restClient.get()
                .uri(COMPANY_METRICS_URL, symbol)
                .header(KEY_HEADER, finnhubApiKey)
                .retrieve()
                .body(CompanyMetricsResponse.class);
        return response.toCompanyFacts();
    }

    /** Spring calls this once the app is up, after the TWS sync and the UI's start. */
    @EventListener(ApplicationReadyEvent.class)
    private void printStatus() {
        if (!hasApiKey()) {
            System.out.println("[stocks] Finnhub off: " + KEY_NAME + " is not set (config/local.env)");
        }
    }

    /** The part of Finnhub's calendar answer that is used. An unknown symbol has an empty calendar. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EarningsCalendarResponse(List<EarningsDate> earningsCalendar) {

        /** The calendar is not in date order: AAPL's January report came before its November one (2026-10-27). */
        private EarningsDate earliestReport() {
            if (earningsCalendar == null) {
                return null;
            }
            return earningsCalendar.stream()
                    .min(Comparator.comparing(EarningsDate::date))
                    .orElse(null);
        }
    }

    /** {@code metric} holds 133 figures for a known stock, and is empty, {@code {}}, for an unknown symbol. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompanyMetricsResponse(CompanyMetrics metric) {

        private CompanyFacts toCompanyFacts() {
            if (metric == null) {
                return new CompanyFacts(null, null, null, null);
            }
            return metric.toCompanyFacts();
        }
    }

    /**
     * The four figures that are used, under Finnhub's names: "TTM" is the last 12 months, "Yoy" against the year before.
     * A record component can't have a "/" in its name, so the debt's Finnhub name is given in {@code @JsonProperty}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CompanyMetrics(
            Double revenueGrowthTTMYoy,
            Double netProfitMarginTTM,
            @JsonProperty("totalDebt/totalEquityQuarterly") Double totalDebtToTotalEquityQuarterly,
            Double peTTM) {

        private CompanyFacts toCompanyFacts() {
            return new CompanyFacts(revenueGrowthTTMYoy, netProfitMarginTTM, totalDebtToTotalEquityQuarterly, peTTM);
        }
    }
}
