package portfolioboss.sources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import portfolioboss.utils.Utils;

import java.time.LocalDate;

/**
 * A company's next earnings report. Finnhub's earnings calendar is read straight into it, so the codes of the hour are
 * Finnhub's, and every other source writes them the same way.
 *
 * @param date the day of the report
 * @param hour {@link #BEFORE_THE_MARKET_OPENS}, {@link #AFTER_THE_MARKET_CLOSES} or {@link #DURING_THE_TRADING_HOURS};
 *             {@code null} while the source doesn't know yet
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EarningsDate(LocalDate date, String hour) {

    public static final String BEFORE_THE_MARKET_OPENS = "bmo";
    public static final String AFTER_THE_MARKET_CLOSES = "amc";
    public static final String DURING_THE_TRADING_HOURS = "dmh";

    /** Finnhub sends an empty text for an hour it doesn't know yet (AAPL's January report, read on 2026-10-27). */
    public EarningsDate {
        hour = Utils.trimmedOrNull(hour);
    }
}
