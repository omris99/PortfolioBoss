package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.exchangerates.ExchangeRateDownloadPeriod;
import portfolioboss.exchangerates.ExchangeRates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which days a sync asks the Bank of Israel the rates of: only those really missing, never a stored one. The sync is on
 * Sunday 15 November 2026; the stored rates, made up, run from Tuesday 30.09.2025 to Friday 13.11.2026 with the days of
 * Yom Kippur 2025 (01.10 and 02.10) missing, as the Bank of Israel published none on them.
 */
class ExchangeRateDownloadScheduleTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate FIRST_STORED_RATE_DATE = LocalDate.of(2025, 9, 30);
    private static final LocalDate LAST_STORED_RATE_DATE = LocalDate.of(2026, 11, 13);

    @Test
    void withNothingStoredTheRatesAreAskedFromTwoWeeksBeforeTheEarliestDayToToday() {
        ExchangeRates noRates = new ExchangeRates("USD", new TreeMap<>());

        assertThat(periodsToDownload(noRates, LocalDate.of(2025, 3, 10), LocalDate.of(2026, 11, 12)))
                .containsExactly(new ExchangeRateDownloadPeriod(LocalDate.of(2025, 2, 24), TODAY));
    }

    @Test
    void nothingIsAskedWhenEveryDayHasItsRate() {
        assertThat(periodsToDownload(storedRates(), LocalDate.of(2025, 9, 30), LocalDate.of(2026, 11, 13))).isEmpty();
    }

    /** Without the stored stretch, this day would be asked for on every sync, forever: it never gets a rate of its own. */
    @Test
    void aHolidayInsideTheStoredRatesIsNotAskedForAgain() {
        assertThat(periodsToDownload(storedRates(), LocalDate.of(2025, 10, 2))).isEmpty();
    }

    /** On Monday 16.11, the day a sale of Friday 13.11 settles: the weekend is asked for too, and brings no rate. */
    @Test
    void aDayAfterTheLastStoredRateIsAskedForFromTheDayAfterItToToday() {
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalDate settlementDayOfFridaysSale = LocalDate.of(2026, 11, 16);

        assertThat(new ExchangeRateDownloadSchedule(storedRates(), Set.of(settlementDayOfFridaysSale), monday)
                .periodsToDownload())
                .containsExactly(new ExchangeRateDownloadPeriod(LocalDate.of(2026, 11, 14), monday));
    }

    /** A trade typed in with an old date: only the days before the stored ones are asked for, not the stored again. */
    @Test
    void aDayBeforeTheFirstStoredRateIsAskedForUpToTheDayBeforeIt() {
        assertThat(periodsToDownload(storedRates(), LocalDate.of(2025, 3, 10)))
                .containsExactly(new ExchangeRateDownloadPeriod(LocalDate.of(2025, 2, 24), LocalDate.of(2025, 9, 29)));
    }

    @Test
    void daysBeforeAndAfterTheStoredRatesAreTwoRequestsOldestFirst() {
        LocalDate monday = LocalDate.of(2026, 11, 16);

        assertThat(new ExchangeRateDownloadSchedule(storedRates(),
                Set.of(LocalDate.of(2025, 3, 10), LocalDate.of(2026, 11, 16)), monday).periodsToDownload())
                .containsExactly(
                        new ExchangeRateDownloadPeriod(LocalDate.of(2025, 2, 24), LocalDate.of(2025, 9, 29)),
                        new ExchangeRateDownloadPeriod(LocalDate.of(2026, 11, 14), monday));
    }

    /** A sale of Friday 13.11 settles on Monday 16.11: its rate can't be published before that day. */
    @Test
    void aSettlementDayAfterTodayIsNotAskedFor() {
        assertThat(periodsToDownload(storedRates(), LocalDate.of(2026, 11, 16))).isEmpty();
    }

    @Test
    void withoutAnySettlementDayNothingIsAskedFor() {
        assertThat(periodsToDownload(new ExchangeRates("USD", new TreeMap<>()))).isEmpty();
    }

    /** The periods asked for on Sunday 15.11, for these settlement days. */
    private List<ExchangeRateDownloadPeriod> periodsToDownload(ExchangeRates storedRates,
                                                             LocalDate... settlementDaysNeedingARate) {
        return new ExchangeRateDownloadSchedule(storedRates, Set.of(settlementDaysNeedingARate), TODAY)
                .periodsToDownload();
    }

    /**
     * The first stored rate — also the last before Yom Kippur 2025 —, the first after it, and the last stored rate: only
     * the stretch's ends and the days around the holiday count here, so the days between are left out.
     */
    private ExchangeRates storedRates() {
        TreeMap<LocalDate, BigDecimal> ratesByDate = new TreeMap<>();
        ratesByDate.put(FIRST_STORED_RATE_DATE, new BigDecimal("3.306"));
        ratesByDate.put(LocalDate.of(2025, 10, 3), new BigDecimal("3.315"));
        ratesByDate.put(LAST_STORED_RATE_DATE, new BigDecimal("3.199"));
        return new ExchangeRates("USD", ratesByDate);
    }
}
