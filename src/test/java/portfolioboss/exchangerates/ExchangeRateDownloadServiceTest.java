package portfolioboss.exchangerates;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portfolioboss.exceptions.ExchangeRatesUnavailableException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A sync's download of the dollar rates still missing, against the real PostgreSQL test database, with
 * {@link BankOfIsraelClient} replaced by a stand-in ({@code @MockitoBean}): no test calls the Bank of Israel. The sync is
 * on Monday 16 November 2026, the settlement day of a sale of Friday the 13th.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ExchangeRateDownloadService.class)
class ExchangeRateDownloadServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);
    private static final BankOfIsraelRate RATE_OF_THURSDAY =
            new BankOfIsraelRate(LocalDate.of(2026, 11, 12), new BigDecimal("3.191"));
    private static final BankOfIsraelRate RATE_OF_FRIDAY =
            new BankOfIsraelRate(LocalDate.of(2026, 11, 13), new BigDecimal("3.199"));
    private static final BankOfIsraelRate RATE_OF_MONDAY = new BankOfIsraelRate(TODAY, new BigDecimal("3.204"));

    @Autowired
    private ExchangeRateDownloadService exchangeRateDownloadService;

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private BankOfIsraelClient bankOfIsraelClient;

    @Test
    void theFirstDownloadStoresTheRatesTheBankOfIsraelPublished() {
        ExchangeRateDownloadPeriod expectedPeriod =
                new ExchangeRateDownloadPeriod(LocalDate.of(2026, 10, 29), TODAY);
        given(bankOfIsraelClient.exchangeRates("USD", expectedPeriod))
                .willReturn(List.of(RATE_OF_THURSDAY, RATE_OF_FRIDAY, RATE_OF_MONDAY));

        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(LocalDate.of(2026, 11, 12), TODAY), TODAY);

        assertThat(countOf("select count(*) from exchange_rate where currency = 'USD'")).isEqualTo(3);
        assertThat(exchangeRateRepository.storedRatesOf("USD").rateForSettlementDay(TODAY))
                .hasValueSatisfying(rate -> assertThat(rate).isEqualByComparingTo("3.204"));
    }

    /** Thursday's and Friday's rates are stored: only the days after Friday are asked for. */
    @Test
    void onlyTheDaysAfterTheStoredRatesAreAskedFor() {
        storeRates(RATE_OF_THURSDAY, RATE_OF_FRIDAY);
        ExchangeRateDownloadPeriod daysAfterFriday = new ExchangeRateDownloadPeriod(LocalDate.of(2026, 11, 14), TODAY);
        given(bankOfIsraelClient.exchangeRates("USD", daysAfterFriday)).willReturn(List.of(RATE_OF_MONDAY));

        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(LocalDate.of(2026, 11, 12), TODAY), TODAY);

        verify(bankOfIsraelClient).exchangeRates("USD", daysAfterFriday);
        assertThat(countOf("select count(*) from exchange_rate where currency = 'USD'")).isEqualTo(3);
    }

    @Test
    void nothingIsAskedWhenEveryDayHasItsRate() {
        storeRates(RATE_OF_THURSDAY, RATE_OF_FRIDAY, RATE_OF_MONDAY);

        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(LocalDate.of(2026, 11, 12), TODAY), TODAY);

        verify(bankOfIsraelClient, never()).exchangeRates(anyString(), any());
    }

    /** Monday's rate is published in the afternoon: a morning sync gets nothing, and the next sync asks again. */
    @Test
    void aRateNotPublishedYetStoresNothingAndIsAskedForAgain() {
        storeRates(RATE_OF_FRIDAY);
        ExchangeRateDownloadPeriod daysAfterFriday = new ExchangeRateDownloadPeriod(LocalDate.of(2026, 11, 14), TODAY);
        given(bankOfIsraelClient.exchangeRates("USD", daysAfterFriday)).willReturn(List.of());

        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(TODAY), TODAY);
        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(TODAY), TODAY);

        verify(bankOfIsraelClient, times(2)).exchangeRates("USD", daysAfterFriday);
        assertThat(countOf("select count(*) from exchange_rate where currency = 'USD'")).isEqualTo(1);
    }

    /** The sync goes on; the next one asks again for what is still missing. */
    @Test
    void aFailureIsPrintedNotThrownAndStoresNothing() {
        given(bankOfIsraelClient.exchangeRates(anyString(), any()))
                .willThrow(new ExchangeRatesUnavailableException("no answer from the Bank of Israel (Read timed out)"));

        exchangeRateDownloadService.downloadMissingRates("USD", Set.of(TODAY), TODAY);

        assertThat(countOf("select count(*) from exchange_rate")).isZero();
    }

    private void storeRates(BankOfIsraelRate... publishedRates) {
        for (BankOfIsraelRate publishedRate : publishedRates) {
            entityManager.persist(new ExchangeRateEntity("USD", publishedRate));
        }
        entityManager.flush();
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
