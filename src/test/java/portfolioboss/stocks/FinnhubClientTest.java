package portfolioboss.stocks;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Finnhub's two answers of RECOMMENDATIONS_TODO.md, decision 8, against a fake Finnhub: {@code MockRestServiceServer}
 * answers the client's requests itself, so nothing reaches the network. The answers are shortened copies of Finnhub's
 * real ones (AAPL and an unknown symbol, read on 2026-10-27), with fields the client doesn't use, to show they are
 * ignored. The addresses are compared whole, so the key is in none of them — only in the header.
 */
class FinnhubClientTest {

    private static final String TEST_KEY = "finnhub-test-key";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    /** As Finnhub sent it: not in date order, and the January report's hour not known yet. */
    private static final String APPLE_EARNINGS_CALENDAR = """
            {
              "earningsCalendar": [
                {
                  "symbol": "AAPL", "date": "2027-01-27", "hour": "", "quarter": 1, "year": 2027,
                  "epsEstimate": 2.9468, "epsActual": null, "revenueEstimate": 155961268080, "revenueActual": null
                },
                {
                  "symbol": "AAPL", "date": "2026-11-02", "hour": "amc", "quarter": 4, "year": 2026,
                  "epsEstimate": 2.0221, "epsActual": null, "revenueEstimate": 115496910249, "revenueActual": null
                }
              ]
            }
            """;

    /** Next to each figure used, Finnhub's annual or quarterly neighbour, which must not be taken instead. */
    private static final String APPLE_COMPANY_METRICS = """
            {
              "metric": {
                "revenueGrowthTTMYoy": 14.24,
                "revenueGrowthQuarterlyYoy": 16.36,
                "netProfitMarginTTM": 27.62,
                "netProfitMarginAnnual": 26.92,
                "totalDebt/totalEquityQuarterly": 0.7844,
                "totalDebt/totalEquityAnnual": 1.3547,
                "longTermDebt/equityQuarterly": 0.6635,
                "peTTM": 38.8212,
                "peAnnual": 44.6855
              },
              "metricType": "all",
              "series": {},
              "symbol": "AAPL"
            }
            """;

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer fakeFinnhub = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final FinnhubClient finnhubClient = new FinnhubClient(restClientBuilder, TEST_KEY);

    /** The earliest report in the calendar, and the four figures rather than their neighbours. */
    @Test
    void theStockIsTheEarliestReportAndTheFourFigures() {
        expectEarningsCalendar("AAPL", APPLE_EARNINGS_CALENDAR);
        expectCompanyMetrics("AAPL", APPLE_COMPANY_METRICS);

        Stock stock = finnhubClient.stock("AAPL", TODAY);

        fakeFinnhub.verify();
        assertThat(stock).isEqualTo(new Stock("AAPL",
                new EarningsDate(LocalDate.of(2026, 11, 2), "amc"),
                new CompanyFacts(14.24, 27.62, 0.7844, 38.8212)));
    }

    @Test
    void anHourNotKnownYetIsNull() {
        expectEarningsCalendar("AAPL", """
                {"earningsCalendar": [{"symbol": "AAPL", "date": "2027-01-27", "hour": ""}]}
                """);
        expectCompanyMetrics("AAPL", APPLE_COMPANY_METRICS);

        Stock stock = finnhubClient.stock("AAPL", TODAY);

        assertThat(stock.nextEarningsDate()).isEqualTo(new EarningsDate(LocalDate.of(2027, 1, 27), null));
    }

    /** A figure Finnhub doesn't give is {@code null}, so it raises no risk sign (decision 5). */
    @Test
    void aFigureFinnhubDoesNotGiveIsNull() {
        expectEarningsCalendar("AAPL", APPLE_EARNINGS_CALENDAR);
        expectCompanyMetrics("AAPL", """
                {"metric": {"peTTM": 38.8212}, "metricType": "all", "series": {}, "symbol": "AAPL"}
                """);

        Stock stock = finnhubClient.stock("AAPL", TODAY);

        assertThat(stock.companyFacts()).isEqualTo(new CompanyFacts(null, null, null, 38.8212));
    }

    /** Finnhub answers 200 for a symbol it doesn't know, with an empty calendar and no figures. */
    @Test
    void anUnknownSymbolHasNoEarningsDateAndNoFigures() {
        expectEarningsCalendar("NOSUCHSYMBOLX", """
                {"earningsCalendar": []}
                """);
        expectCompanyMetrics("NOSUCHSYMBOLX", """
                {"metric": {}, "metricType": "all", "series": {}, "symbol": "NOSUCHSYMBOLX"}
                """);

        Stock stock = finnhubClient.stock("NOSUCHSYMBOLX", TODAY);

        assertThat(stock).isEqualTo(new Stock("NOSUCHSYMBOLX", null, new CompanyFacts(null, null, null, null)));
    }

    /** A wrong key, or more than 60 calls in a minute: the call fails. */
    @Test
    void aRefusedCallFails() {
        fakeFinnhub.expect(requestTo(earningsCalendarUrlOf("AAPL"))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> finnhubClient.stock("AAPL", TODAY))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);
    }

    /** A key of nothing but spaces is no key: Finnhub's part is off. */
    @Test
    void theKeyIsSetOnlyWhenItHasText() {
        assertThat(finnhubClient.hasApiKey()).isTrue();
        assertThat(new FinnhubClient(RestClient.builder(), "  ").hasApiKey()).isFalse();
        assertThat(new FinnhubClient(RestClient.builder(), null).hasApiKey()).isFalse();
    }

    private void expectEarningsCalendar(String symbol, String answer) {
        fakeFinnhub.expect(requestTo(earningsCalendarUrlOf(symbol)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Finnhub-Token", TEST_KEY))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    private void expectCompanyMetrics(String symbol, String answer) {
        fakeFinnhub.expect(requestTo("https://finnhub.io/api/v1/stock/metric?symbol=" + symbol + "&metric=all"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Finnhub-Token", TEST_KEY))
                .andRespond(withSuccess(answer, MediaType.APPLICATION_JSON));
    }

    /** From today to four months ahead. */
    private String earningsCalendarUrlOf(String symbol) {
        return "https://finnhub.io/api/v1/calendar/earnings?symbol=" + symbol + "&from=2026-10-27&to=2027-02-27";
    }
}
