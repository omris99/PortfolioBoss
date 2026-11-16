package portfolioboss.tax;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One of the account's tax years with what the user confirmed for its settlement days (CAPITAL_GAINS_TAX_TODO.md,
 * decision 7, session 4): each day with its confirmation, and the year's balance as recorded at Phoenix — a confirmed
 * day's confirmed amount, every other day's calculated one. A day of Hong Kong dollars, which isn't calculated, counts in
 * the year once its amount is confirmed.
 *
 * @param taxYear                the year as calculated
 * @param confirmationsOfTheYear the confirmations of its settlement days, one at most for a day
 */
record TaxYearWithItsConfirmations(TaxYearCalculator taxYear, List<TaxConfirmation> confirmationsOfTheYear) {

    protected int year() {
        return taxYear.year();
    }

    /**
     * Every calculated day with its confirmation, the earliest first. A confirmation whose day the calculation no longer
     * has — IB changed a settlement day in a report downloaded again, which is rare — is not left out without a word: it
     * comes as a day whose calculated entry is empty, so 0.
     */
    protected List<SettlementDayWithItsConfirmation> settlementDays() {
        Map<LocalDate, TaxConfirmation> confirmationsBySettlementDay = confirmationsBySettlementDay();
        List<SettlementDayWithItsConfirmation> settlementDays = new ArrayList<>();
        for (SettlementDayEntry entry : taxYear.entriesInDateOrder()) {
            settlementDays.add(new SettlementDayWithItsConfirmation(entry,
                    confirmationsBySettlementDay.remove(entry.settlementDay())));
        }
        for (TaxConfirmation confirmationWithoutItsDay : confirmationsBySettlementDay.values()) {
            settlementDays.add(new SettlementDayWithItsConfirmation(
                    emptyEntryOf(confirmationWithoutItsDay.settlementDay()), confirmationWithoutItsDay));
        }
        settlementDays.sort(Comparator.comparing(SettlementDayWithItsConfirmation::settlementDay));
        return settlementDays;
    }

    /** The year's balance from what was recorded: each day's {@link SettlementDayWithItsConfirmation#recordedTaxAmount}. */
    protected TaxYearBalance recordedBalance() {
        Map<LocalDate, BigDecimal> recordedTaxAmountBySettlementDay = new TreeMap<>();
        for (SettlementDayWithItsConfirmation settlementDay : settlementDays()) {
            recordedTaxAmountBySettlementDay.put(settlementDay.settlementDay(), settlementDay.recordedTaxAmount());
        }
        return new TaxYearBalance(recordedTaxAmountBySettlementDay, taxYear.lastSyncDay());
    }

    private Map<LocalDate, TaxConfirmation> confirmationsBySettlementDay() {
        Map<LocalDate, TaxConfirmation> confirmationsBySettlementDay = new HashMap<>();
        for (TaxConfirmation confirmation : confirmationsOfTheYear) {
            confirmationsBySettlementDay.put(confirmation.settlementDay(), confirmation);
        }
        return confirmationsBySettlementDay;
    }

    /** No sale and no expense: its tax amount is 0. */
    private SettlementDayEntry emptyEntryOf(LocalDate settlementDay) {
        return new SettlementDayEntry(settlementDay, List.of(), List.of(), Map.of());
    }
}
