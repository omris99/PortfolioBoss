package portfolioboss.calculation;

import java.time.LocalDate;

/**
 * One trading day's closing price of a contract, as IB's daily bars report it. A holding's momentum (and SPY's, its
 * benchmark) is computed from a year of these.
 */
public record DailyClose(LocalDate date, double close) {
}
