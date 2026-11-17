package portfolioboss.tax;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One of the account's settlement days, with what the user confirmed for it (CAPITAL_GAINS_TAX_TODO.md, decision 7).
 * A day is waiting for its confirmation, or confirmed — nothing in between: the amount the user confirmed is what Phoenix
 * recorded, so it is final, and it counts in the year's balance in place of the calculated one, even if the calculation
 * changes later. A day not confirmed yet counts with its calculated amount.
 *
 * @param entry        the day as calculated
 * @param confirmation what the user confirmed for it; {@code null} while it waits
 */
record SettlementDayWithItsConfirmation(SettlementDayEntry entry, TaxConfirmation confirmation) {

    protected LocalDate settlementDay() {
        return entry.settlementDay();
    }

    protected boolean isConfirmed() {
        return confirmation != null;
    }

    /** In shekels: the confirmed amount when the day is confirmed, otherwise the calculated one. */
    protected BigDecimal recordedTaxAmount() {
        return isConfirmed() ? confirmation.taxAmount() : entry.taxAmount();
    }

    /** Confirmed once the user confirmed it, whatever its calculation; otherwise what its calculation needs. */
    protected SettlementDayStatus status() {
        return isConfirmed() ? SettlementDayStatus.CONFIRMED : entry.status();
    }
}
