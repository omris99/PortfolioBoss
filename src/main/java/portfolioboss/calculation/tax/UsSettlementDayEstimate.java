package portfolioboss.calculation.tax;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The settlement day of a trade typed in by hand, estimated from its date (CAPITAL_GAINS_TAX_TODO.md, session 2.1): a
 * typed trade has a date but no settlement day, while IB's Flex trade has the real one. A US stock trade settles one
 * business day after the trade since 28 May 2024, two before it; Saturdays and Sundays are skipped.
 *
 * <p>US market holidays are not known, so on a week with one the estimate can be a day early (a trade of Friday
 * 24.05.2024 settled on Wednesday the 29th, after Memorial Day; the estimate says Tuesday). The tax takes the real day
 * from the trade's twin in Flex where there is one (session 3), and this estimate only for a typed trade Flex doesn't
 * have — a date typed wrong, say.
 *
 * @param tradeDate the day of the trade
 */
public record UsSettlementDayEstimate(LocalDate tradeDate) {

    /** The first trade day the US market settled one business day later, instead of two. */
    private static final LocalDate FIRST_TRADE_DAY_SETTLED_THE_NEXT_BUSINESS_DAY = LocalDate.of(2024, 5, 28);

    public LocalDate estimatedSettlementDay() {
        int businessDaysToSettle = tradeDate.isBefore(FIRST_TRADE_DAY_SETTLED_THE_NEXT_BUSINESS_DAY) ? 2 : 1;
        LocalDate settlementDay = tradeDate;
        for (int businessDaysCounted = 0; businessDaysCounted < businessDaysToSettle; businessDaysCounted++) {
            settlementDay = nextWeekdayAfter(settlementDay);
        }
        return settlementDay;
    }

    /** The day after, or the Monday after when the day after is a Saturday or a Sunday. */
    private LocalDate nextWeekdayAfter(LocalDate date) {
        LocalDate nextDay = date.plusDays(1);
        while (nextDay.getDayOfWeek() == DayOfWeek.SATURDAY || nextDay.getDayOfWeek() == DayOfWeek.SUNDAY) {
            nextDay = nextDay.plusDays(1);
        }
        return nextDay;
    }
}
