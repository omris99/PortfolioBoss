package portfolioboss.exchangerates;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import portfolioboss.exceptions.ExchangeRatesUnavailableException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The call to the Bank of Israel against a fake server: {@code MockRestServiceServer} answers the client's requests
 * itself, so no test calls the Bank of Israel. The address is compared whole.
 */
class BankOfIsraelClientTest {

    private static final ExchangeRateDownloadPeriod WEEK_OF_YOM_KIPPUR =
            new ExchangeRateDownloadPeriod(LocalDate.of(2025, 9, 29), LocalDate.of(2025, 10, 3));
    private static final String WEEK_OF_YOM_KIPPUR_URL = "https://edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data"
            + "/dataflow/BOI.STATISTICS/EXR/1.0/RER_USD_ILS?startperiod=2025-09-29&endperiod=2025-10-03&format=csv";
    private static final String WEEK_OF_YOM_KIPPUR_ANSWER = """
            SERIES_CODE,FREQ,BASE_CURRENCY,COUNTER_CURRENCY,UNIT_MEASURE,DATA_TYPE,DATA_SOURCE,TIME_COLLECT,CONF_STATUS,\
            PUB_WEBSITE,UNIT_MULT,COMMENTS,TIME_PERIOD,OBS_VALUE,RELEASE_STATUS
            RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-09-29,3.323,YP
            RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-09-30,3.306,YP
            RER_USD_ILS,D,USD,ILS,ILS,OF00,BOI_MRKT,V,F,Y,0,,2025-10-03,3.315,YP
            """;
    /** What the Bank of Israel answers a 404 with: a page in Hebrew, "the site asked for was not found". */
    private static final String NOT_FOUND_PAGE = "<!DOCTYPE html><html dir=\"rtl\"><head><title>שגיאה</title></head>"
            + "<body>האתר המבוקש אינו נמצא</body></html>";

    private final RestClient.Builder restClientBuilder = RestClient.builder();
    private final MockRestServiceServer fakeBankOfIsrael = MockRestServiceServer.bindTo(restClientBuilder).build();
    private final BankOfIsraelClient bankOfIsraelClient = new BankOfIsraelClient(restClientBuilder);

    @Test
    void asksForTheCurrencysSeriesOfThePeriodAndReadsItsRates() {
        fakeBankOfIsrael.expect(requestTo(WEEK_OF_YOM_KIPPUR_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(WEEK_OF_YOM_KIPPUR_ANSWER, MediaType.parseMediaType("text/csv;charset=UTF-8")));

        assertThat(bankOfIsraelClient.exchangeRates("USD", WEEK_OF_YOM_KIPPUR)).containsExactly(
                new BankOfIsraelRate(LocalDate.of(2025, 9, 29), new BigDecimal("3.323")),
                new BankOfIsraelRate(LocalDate.of(2025, 9, 30), new BigDecimal("3.306")),
                new BankOfIsraelRate(LocalDate.of(2025, 10, 3), new BigDecimal("3.315")));
        fakeBankOfIsrael.verify();
    }

    /** A weekend: the Bank of Israel answers 404, not an empty CSV. */
    @Test
    void aFewDaysWithoutARateAreNoRate() {
        fakeBankOfIsrael.expect(requestTo(urlOf("2026-11-14", "2026-11-15")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body(NOT_FOUND_PAGE).contentType(MediaType.TEXT_HTML));

        assertThat(bankOfIsraelClient.exchangeRates("USD",
                new ExchangeRateDownloadPeriod(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 15)))).isEmpty();
    }

    /** Fifteen days without a single rate never happened (seven at most): the series is not there any more. */
    @Test
    void manyDaysWithoutARateFail() {
        fakeBankOfIsrael.expect(requestTo(urlOf("2026-10-01", "2026-10-15")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body(NOT_FOUND_PAGE).contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> bankOfIsraelClient.exchangeRates("USD",
                new ExchangeRateDownloadPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15))))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("the Bank of Israel has no USD rate in the 15 days of 2026-10-01 – 2026-10-15: has the "
                        + "series RER_USD_ILS been renamed?");
    }

    @Test
    void anHttpErrorFailsWithAShortMessageWithoutThePage() {
        fakeBankOfIsrael.expect(requestTo(WEEK_OF_YOM_KIPPUR_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body(NOT_FOUND_PAGE)
                        .contentType(MediaType.TEXT_HTML));

        assertThatThrownBy(() -> bankOfIsraelClient.exchangeRates("USD", WEEK_OF_YOM_KIPPUR))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("the Bank of Israel answered HTTP 500");
    }

    @Test
    void noAnswerFailsWithItsReason() {
        fakeBankOfIsrael.expect(requestTo(WEEK_OF_YOM_KIPPUR_URL))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> bankOfIsraelClient.exchangeRates("USD", WEEK_OF_YOM_KIPPUR))
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasMessage("no answer from the Bank of Israel (Read timed out)");
    }

    private String urlOf(String fromDate, String toDate) {
        return "https://edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0/RER_USD_ILS"
                + "?startperiod=" + fromDate + "&endperiod=" + toDate + "&format=csv";
    }
}
