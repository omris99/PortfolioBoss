package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One tax year's balance and how it settles month by month, as Phoenix keeps it (CAPITAL_GAINS_TAX_TODO.md, decision 5):
 * every settlement day adds its tax amount to the year's balance, and at each month's end the tax owed for the year so far
 * — the sum of the amounts up to then, never less than 0 — is settled against what was paid before: the difference is
 * paid, or refunded. A shield beyond what was paid stays for the rest of the year; one left at the year's end is lost (an
 * assumption: decision 5 — the next year starts from 0).
 *
 * <p>Example 2 of the plan: +1,000 in March is paid; −250 in April is refunded; −1,250 in May refunds the 750 still paid
 * and leaves a shield of 500; +500 in June uses it up, and nothing moves.
 *
 * <p>Assumed, not seen: that a refund is never more than what was paid in the same year (decision 5).
 *
 * <p>The same rule runs on the amounts as calculated ({@link TaxYearCalculator#balance}) and on the amounts as recorded at
 * Phoenix, where the user confirmed them ({@link TaxYearWithItsConfirmations#recordedBalance}).
 *
 * @param taxAmountBySettlementDay the tax amount of each settlement day of the year, in shekels: positive — tax to pay —,
 *                                 negative — a tax shield
 * @param lastSyncDay              the day of the last sync: a month that hasn't ended by it is not settled yet
 */
public record TaxYearBalance(Map<LocalDate, BigDecimal> taxAmountBySettlementDay, LocalDate lastSyncDay) {

    /** Every month that has a settlement day, in order. */
    public List<MonthlySettlement> monthlySettlements() {
        List<MonthlySettlement> monthlySettlements = new ArrayList<>();
        BigDecimal taxAmountsOfTheYearSoFar = BigDecimal.ZERO;
        BigDecimal paidSoFar = BigDecimal.ZERO;
        for (Map.Entry<YearMonth, BigDecimal> month : taxAmountsByMonth().entrySet()) {
            BigDecimal taxAmountsOfTheMonth = month.getValue();
            taxAmountsOfTheYearSoFar = taxAmountsOfTheYearSoFar.add(taxAmountsOfTheMonth);
            BigDecimal owedSoFar = noLessThanZero(taxAmountsOfTheYearSoFar);
            monthlySettlements.add(new MonthlySettlement(month.getKey(), taxAmountsOfTheMonth,
                    owedSoFar.subtract(paidSoFar), noLessThanZero(taxAmountsOfTheYearSoFar.negate()),
                    hasEnded(month.getKey())));
            paidSoFar = owedSoFar;
        }
        return monthlySettlements;
    }

    /** The year's tax so far, in shekels: what its settlements add up to, the current month's included. */
    public BigDecimal taxOfTheYearSoFar() {
        return noLessThanZero(sumOfTheTaxAmounts());
    }

    /** The year's shield not used yet, in shekels; 0 when there is none. */
    public BigDecimal shieldLeft() {
        return noLessThanZero(sumOfTheTaxAmounts().negate());
    }

    private Map<YearMonth, BigDecimal> taxAmountsByMonth() {
        Map<YearMonth, BigDecimal> taxAmountsByMonth = new TreeMap<>();
        taxAmountBySettlementDay.forEach((settlementDay, taxAmount) ->
                taxAmountsByMonth.merge(YearMonth.from(settlementDay), taxAmount, BigDecimal::add));
        return taxAmountsByMonth;
    }

    private BigDecimal sumOfTheTaxAmounts() {
        return taxAmountBySettlementDay.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Phoenix settles on the month's last day, so the month has ended only after it. */
    private boolean hasEnded(YearMonth month) {
        return lastSyncDay.isAfter(month.atEndOfMonth());
    }

    private BigDecimal noLessThanZero(BigDecimal amount) {
        return amount.max(BigDecimal.ZERO);
    }
}
