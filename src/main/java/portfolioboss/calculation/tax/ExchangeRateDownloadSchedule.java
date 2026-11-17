package portfolioboss.calculation.tax;

import portfolioboss.exchangerates.BankOfIsraelRate;
import portfolioboss.exchangerates.ExchangeRateDownloadPeriod;
import portfolioboss.exchangerates.ExchangeRates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Which days a sync asks the Bank of Israel the rates of (CAPITAL_GAINS_TAX_TODO.md, session 2): only days whose rate is
 * really missing, never a day already stored. A settlement day up to today is missing when the stored rates don't reach
 * it ({@link ExchangeRates#knowsRateForSettlementDay}) — a holiday inside the stored stretch is known, and never asked
 * again —; a settlement day after today can't be published yet, and isn't asked.
 * <ul>
 *   <li>Nothing missing: no request at all.</li>
 *   <li>Nothing stored yet: from two weeks before the earliest missing day to today.</li>
 *   <li>A missing day before the first stored rate: from two weeks before it to the day before the first stored rate.</li>
 *   <li>A missing day after the last stored rate: from the day after the last stored rate to today.</li>
 *   <li>Both: those two requests, oldest first.</li>
 * </ul>
 * Each request starts or ends next to the stored stretch, so the stretch stays unbroken and no stored day is asked again.
 * The two weeks before the earliest day are there for its "last rate before it", when the day itself has none.
 *
 * @param storedRates                the currency's stored rates
 * @param settlementDaysNeedingARate the settlement days whose rate the tax needs
 * @param today                      the sync's day
 */
public record ExchangeRateDownloadSchedule(ExchangeRates storedRates, Set<LocalDate> settlementDaysNeedingARate,
                                    LocalDate today) {

    /** Twice the longest the Bank of Israel went without a rate: the rate before the earliest day is surely among them. */
    private static final int DAYS_READ_BEFORE_THE_EARLIEST_DAY = 2 * BankOfIsraelRate.LONGEST_GAP_BETWEEN_RATES_IN_DAYS;

    public List<ExchangeRateDownloadPeriod> periodsToDownload() {
        List<LocalDate> daysWithoutAKnownRate = settlementDaysNeedingARate.stream()
                .filter(settlementDay -> !settlementDay.isAfter(today))
                .filter(settlementDay -> !storedRates.knowsRateForSettlementDay(settlementDay))
                .sorted()
                .toList();
        if (daysWithoutAKnownRate.isEmpty()) {
            return List.of();
        }
        LocalDate earliestDayWithoutAKnownRate = daysWithoutAKnownRate.getFirst();
        LocalDate latestDayWithoutAKnownRate = daysWithoutAKnownRate.getLast();
        if (storedRates.firstRateDate().isEmpty()) {
            return List.of(new ExchangeRateDownloadPeriod(
                    earliestDayWithoutAKnownRate.minusDays(DAYS_READ_BEFORE_THE_EARLIEST_DAY), today));
        }

        LocalDate firstStoredRateDate = storedRates.firstRateDate().get();
        LocalDate lastStoredRateDate = storedRates.lastRateDate().get();
        List<ExchangeRateDownloadPeriod> periodsToDownload = new ArrayList<>();
        if (earliestDayWithoutAKnownRate.isBefore(firstStoredRateDate)) {
            periodsToDownload.add(new ExchangeRateDownloadPeriod(
                    earliestDayWithoutAKnownRate.minusDays(DAYS_READ_BEFORE_THE_EARLIEST_DAY),
                    firstStoredRateDate.minusDays(1)));
        }
        if (latestDayWithoutAKnownRate.isAfter(lastStoredRateDate)) {
            periodsToDownload.add(new ExchangeRateDownloadPeriod(lastStoredRateDate.plusDays(1), today));
        }
        return periodsToDownload;
    }
}
