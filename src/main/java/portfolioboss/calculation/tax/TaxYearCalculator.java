package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One tax year as calculated (CAPITAL_GAINS_TAX_TODO.md, decision 5): its settlement-day entries, its net taxable gain,
 * and its balance — the tax so far, the shield left and the monthly settlements ({@link TaxYearBalance}), from each
 * entry's tax amount.
 *
 * @param year               the tax year — of the settlement day (decision 16)
 * @param entriesInDateOrder the year's settlement-day entries, the earliest first
 * @param lastSyncDay        the day of the last sync: a month that hasn't ended by it is not settled yet
 */
public record TaxYearCalculator(int year, List<SettlementDayEntry> entriesInDateOrder, LocalDate lastSyncDay) {

    /** The balance of the entries as calculated: each settlement day's {@link SettlementDayEntry#taxAmount}. */
    public TaxYearBalance balance() {
        Map<LocalDate, BigDecimal> taxAmountBySettlementDay = new TreeMap<>();
        for (SettlementDayEntry entry : entriesInDateOrder) {
            taxAmountBySettlementDay.put(entry.settlementDay(), entry.taxAmount());
        }
        return new TaxYearBalance(taxAmountBySettlementDay, lastSyncDay);
    }

    /** The year's net taxable gain so far, in shekels: negative when the year has lost. */
    public BigDecimal taxableGain() {
        return entriesInDateOrder.stream()
                .map(SettlementDayEntry::taxableGainOfTheDay)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Every sale of the year, computed or not, in the order of their settlement days. */
    public List<SaleTaxableGain> sales() {
        return entriesInDateOrder.stream().flatMap(entry -> entry.sales().stream()).toList();
    }
}
