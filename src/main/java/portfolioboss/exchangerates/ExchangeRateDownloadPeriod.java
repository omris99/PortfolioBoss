package portfolioboss.exchangerates;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The days one request to the Bank of Israel asks the rates of, both ends included.
 *
 * @param fromDate the first day
 * @param toDate   the last day, not before {@code fromDate}
 */
record ExchangeRateDownloadPeriod(LocalDate fromDate, LocalDate toDate) {

    protected ExchangeRateDownloadPeriod {
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("A period can't end (" + toDate + ") before it starts (" + fromDate + ")");
        }
    }

    /** How many days the period covers, both ends counted: 14.11 – 16.11 is 3 days. */
    protected long dayCount() {
        return ChronoUnit.DAYS.between(fromDate, toDate) + 1;
    }

    /** How the console writes it: "2026-11-14 – 2026-11-16". */
    @Override
    public String toString() {
        return fromDate + " – " + toDate;
    }
}
