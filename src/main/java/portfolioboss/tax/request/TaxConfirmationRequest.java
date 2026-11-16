package portfolioboss.tax.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of {@code POST /api/taxes/confirmations}: what Phoenix recorded for one settlement day, as the user confirmed
 * it. The limits match the {@code tax_confirmation} table: {@code NUMERIC(20,2)} — to the agora — and a note of at most
 * 500 characters. The amount may be negative (a tax shield) or 0; {@code note} is optional.
 *
 * @param settlementDay the day; never in the future — Phoenix records a day on the day itself
 * @param taxAmount     in shekels: positive — tax to pay —, negative — a tax shield
 * @param note          the user's note
 */
public record TaxConfirmationRequest(
        @NotNull @PastOrPresent LocalDate settlementDay,
        @NotNull @Digits(integer = 18, fraction = 2, message = TOO_MANY_DIGITS) BigDecimal taxAmount,
        @Size(max = 500) String note) {

    /** Hibernate Validator's own message for {@code @Digits} doesn't say the amount is in agorot. */
    protected static final String TOO_MANY_DIGITS = "must have at most 2 decimal places (agorot) and 18 digits before the point";
}
