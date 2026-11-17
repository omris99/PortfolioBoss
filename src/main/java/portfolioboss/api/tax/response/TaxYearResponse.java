package portfolioboss.api.tax.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * One tax year — the account's or an investor's —, in shekels. On the account's years the tax, the shield and the
 * monthly settlements are as recorded (a confirmed day's confirmed amount, every other day's calculated one); on an
 * investor's, as calculated.
 *
 * @param year               the tax year — of the settlement day (decision 16)
 * @param taxableGain        the year's net taxable gain so far, as calculated: negative when the year has lost
 * @param taxOfTheYearSoFar  the year's tax so far, never less than 0
 * @param shieldLeft         the year's shield not used yet; 0 when there is none
 * @param settlementDays     every settlement day with an entry, the earliest first
 * @param monthlySettlements every month with a settlement day, in order
 */
public record TaxYearResponse(int year, BigDecimal taxableGain, BigDecimal taxOfTheYearSoFar, BigDecimal shieldLeft,
                              List<SettlementDayResponse> settlementDays,
                              List<MonthlySettlementResponse> monthlySettlements) {
}
