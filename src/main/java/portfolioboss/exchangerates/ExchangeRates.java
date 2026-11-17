package portfolioboss.exchangerates;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One currency's stored representative rates, and which rate a settlement day takes (CAPITAL_GAINS_TAX_TODO.md,
 * decision 1 and session 2.2): the rate of the day itself, or — on a day the Bank of Israel published none, an Israeli or
 * an international holiday — the last rate published before it.
 *
 * <p>The stored rates are one unbroken stretch of the Bank of Israel's series ({@code ExchangeRateDownloadSchedule} only
 * ever adds days before or after it), so a day inside the stretch without a rate is known to be a day without one. A day
 * after the last stored rate is not known yet: its rate may not be published yet (the Bank of Israel publishes in the
 * afternoon), and a holiday there can be told only once the next rate is out. A day before the first stored rate is not
 * known either.
 *
 * @param currency    the currency's code, "USD"
 * @param ratesByDate shekels for one unit of the currency, by day, in date order — Java's {@link TreeMap}, which finds
 *                    "the day itself, or the nearest day before it" in one call ({@code floorEntry})
 */
public record ExchangeRates(String currency, NavigableMap<LocalDate, BigDecimal> ratesByDate) {

    public ExchangeRates {
        ratesByDate = Collections.unmodifiableNavigableMap(new TreeMap<>(ratesByDate));
    }

    /**
     * The rate the settlement day takes: its own, or the last one before it inside the stored stretch; empty when the
     * stored rates don't reach the day — after the last stored rate, before the first, or none stored at all.
     */
    public Optional<BigDecimal> rateForSettlementDay(LocalDate settlementDay) {
        if (ratesByDate.isEmpty() || settlementDay.isAfter(ratesByDate.lastKey())) {
            return Optional.empty();
        }
        Map.Entry<LocalDate, BigDecimal> rateOfTheDayOrTheLastBefore = ratesByDate.floorEntry(settlementDay);
        if (rateOfTheDayOrTheLastBefore == null) {
            return Optional.empty();
        }
        return Optional.of(rateOfTheDayOrTheLastBefore.getValue());
    }

    public boolean knowsRateForSettlementDay(LocalDate settlementDay) {
        return rateForSettlementDay(settlementDay).isPresent();
    }

    /** The day of the first stored rate; empty when none is stored. */
    public Optional<LocalDate> firstRateDate() {
        return ratesByDate.isEmpty() ? Optional.empty() : Optional.of(ratesByDate.firstKey());
    }

    /** The day of the last stored rate; empty when none is stored. */
    public Optional<LocalDate> lastRateDate() {
        return ratesByDate.isEmpty() ? Optional.empty() : Optional.of(ratesByDate.lastKey());
    }
}
