package portfolioboss.exchangerates;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which stored rate a settlement day takes (CAPITAL_GAINS_TAX_TODO.md, decision 1 and session 2.2), on the dollar's real
 * rates around Yom Kippur 2025 — public: the Bank of Israel published none on Wednesday 01.10 and Thursday 02.10.
 */
class ExchangeRatesTest {

    private static final ExchangeRates RATES_AROUND_YOM_KIPPUR = new ExchangeRates("USD", new TreeMap<>(Map.of(
            LocalDate.of(2025, 9, 26), new BigDecimal("3.366"),
            LocalDate.of(2025, 9, 29), new BigDecimal("3.323"),
            LocalDate.of(2025, 9, 30), new BigDecimal("3.306"),
            LocalDate.of(2025, 10, 3), new BigDecimal("3.315"))));

    @Test
    void aDayWithItsOwnRateTakesIt() {
        assertThat(RATES_AROUND_YOM_KIPPUR.rateForSettlementDay(LocalDate.of(2025, 9, 29)))
                .contains(new BigDecimal("3.323"));
    }

    /** The one case seen in Phoenix's statements (December 2025) took the last rate published before the day. */
    @Test
    void aDayTheBankOfIsraelPublishedNoRateOnTakesTheLastRateBeforeIt() {
        assertThat(RATES_AROUND_YOM_KIPPUR.rateForSettlementDay(LocalDate.of(2025, 10, 2)))
                .contains(new BigDecimal("3.306"));
    }

    @Test
    void aSundayTakesFridaysRate() {
        assertThat(RATES_AROUND_YOM_KIPPUR.rateForSettlementDay(LocalDate.of(2025, 9, 28)))
                .contains(new BigDecimal("3.366"));
    }

    /** Monday 06.10's rate may not be published yet, or the day may be a holiday — which only a later rate would tell. */
    @Test
    void aDayAfterTheLastStoredRateHasNoRateYet() {
        assertThat(RATES_AROUND_YOM_KIPPUR.rateForSettlementDay(LocalDate.of(2025, 10, 6))).isEmpty();
        assertThat(RATES_AROUND_YOM_KIPPUR.knowsRateForSettlementDay(LocalDate.of(2025, 10, 6))).isFalse();
    }

    @Test
    void aDayBeforeTheFirstStoredRateHasNoRate() {
        assertThat(RATES_AROUND_YOM_KIPPUR.rateForSettlementDay(LocalDate.of(2025, 9, 25))).isEmpty();
    }

    @Test
    void withNoRateStoredNoDayHasARate() {
        ExchangeRates noRates = new ExchangeRates("USD", new TreeMap<>());

        assertThat(noRates.rateForSettlementDay(LocalDate.of(2025, 9, 29))).isEmpty();
        assertThat(noRates.firstRateDate()).isEmpty();
        assertThat(noRates.lastRateDate()).isEmpty();
    }
}
