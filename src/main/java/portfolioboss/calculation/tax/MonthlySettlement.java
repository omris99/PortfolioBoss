package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * How one month of a tax year settles against the shekel account at Phoenix, at its end (CAPITAL_GAINS_TAX_TODO.md,
 * decision 5): a payment ("מס ששולם"), a refund ("זיכוי מס"), or a shield that stays.
 *
 * @param month              the month
 * @param entriesOfTheMonth  the sum of the tax amounts of the month's settlement days, in shekels — as calculated
 *                           ({@link SettlementDayEntry#taxAmount}), or as recorded where the user confirmed them
 * @param settlementAmount   what moves at the month's end, in shekels: positive — tax paid —, negative — tax refunded —,
 *                           0 when nothing does
 * @param shieldLeft         the year's shield not used yet at the month's end, in shekels; 0 when there is none
 * @param settled            whether the month had ended by the last sync; the current month's amount is what it would be
 *                           if it ended now
 */
public record MonthlySettlement(YearMonth month, BigDecimal entriesOfTheMonth, BigDecimal settlementAmount,
                         BigDecimal shieldLeft, boolean settled) {
}
