package portfolioboss.api.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import portfolioboss.db.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static portfolioboss.api.request.TradeRequest.TOO_MANY_DIGITS;

/**
 * The body of {@code POST /api/investors/{investorId}/cash-movements} and {@code PUT /api/cash-movements/{movementId}}:
 * one deposit or withdrawal as the user typed it. The limits match the {@code investor_cash_movement} table, as
 * {@link TradeRequest}'s match {@code trade}: {@code NUMERIC(20,6)}, and a note of at most 500 characters. The amount
 * is always above 0 — {@code type} says which way the money went. {@code note} is optional.
 */
public record CashMovementRequest(
        @NotNull @PastOrPresent LocalDate movementDate,
        @NotNull CashMovementType type,
        @NotNull @Positive @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal amount,
        @Size(max = 500) String note) {
}
