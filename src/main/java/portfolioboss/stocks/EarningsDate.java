package portfolioboss.stocks;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import portfolioboss.utils.Utils;

import java.time.LocalDate;

/**
 * A company's earnings report as Finnhub's earnings calendar gives it — read straight from Finnhub's JSON, so the names
 * are Finnhub's.
 *
 * @param date the day of the report
 * @param hour {@code "bmo"} before the market opens, {@code "amc"} after it closes, {@code "dmh"} during the trading
 *             hours; {@code null} while Finnhub doesn't know yet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EarningsDate(LocalDate date, String hour) {

    /** Finnhub sends an empty text for an hour it doesn't know yet (AAPL's January report, read on 2026-10-27). */
    public EarningsDate {
        hour = Utils.trimmedOrNull(hour);
    }
}
