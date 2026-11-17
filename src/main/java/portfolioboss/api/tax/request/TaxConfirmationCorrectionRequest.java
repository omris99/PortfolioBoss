package portfolioboss.api.tax.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import static portfolioboss.api.tax.request.TaxConfirmationRequest.TOO_MANY_DIGITS;

/**
 * The body of {@code PUT /api/taxes/confirmations/{confirmationId}}: a correction of what was confirmed for a day — the
 * amount and the note as the user typed them now. The day itself doesn't change: a confirmation of another day is a new
 * one. The limits are {@link TaxConfirmationRequest}'s.
 *
 * @param taxAmount in shekels: positive — tax to pay —, negative — a tax shield
 * @param note      the user's note
 */
public record TaxConfirmationCorrectionRequest(
        @NotNull @Digits(integer = 18, fraction = 2, message = TOO_MANY_DIGITS) BigDecimal taxAmount,
        @Size(max = 500) String note) {
}
