package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What Phoenix recorded for one of the account's settlement days, as the user confirmed it (CAPITAL_GAINS_TAX_TODO.md,
 * decision 7) — a {@code tax_confirmation} row as the calculation reads it. Its amount is final: it counts in the year's
 * balance in place of the calculated one ({@link SettlementDayWithItsConfirmation}). Public, so that
 * {@code TaxConfirmationResponse} can copy it.
 *
 * @param id            the row's id
 * @param settlementDay the day
 * @param taxAmount     in shekels, to the agora: positive — tax to pay ("מס לשלם") —, negative — a tax shield ("מגן מס")
 * @param confirmedAt   when it was confirmed, or last corrected
 * @param note          the user's note; {@code null} when there is none
 */
public record TaxConfirmation(long id, LocalDate settlementDay, BigDecimal taxAmount, Instant confirmedAt, String note) {
}
