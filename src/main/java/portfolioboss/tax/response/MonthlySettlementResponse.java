package portfolioboss.tax.response;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * How one month of a tax year settles against the shekel account at Phoenix, at its end (decision 5) — to set against
 * Phoenix's "מס ששולם" (tax paid) and "זיכוי מס" (tax refunded). In shekels.
 *
 * @param month             the month, as {@code "2026-03"}
 * @param entriesOfTheMonth the sum of the month's settlement-day amounts
 * @param settlementAmount  what moves at the month's end: positive — tax paid —, negative — tax refunded —, 0 when nothing
 * @param shieldLeft        the year's shield not used yet at the month's end; 0 when there is none
 * @param settled           whether the month had ended by the last sync; the current month's amount is what it would be
 *                          if it ended now
 */
public record MonthlySettlementResponse(YearMonth month, BigDecimal entriesOfTheMonth, BigDecimal settlementAmount,
                                        BigDecimal shieldLeft, boolean settled) {
}
