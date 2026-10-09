package portfolioboss.api.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import portfolioboss.calculation.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The body of {@code POST /api/holdings/{holdingId}/trades} and {@code PUT /api/trades/{tradeId}}: one buy or sell as
 * the user typed it. The annotations are checked by {@code @Valid} before the controller method runs, and their limits
 * match the {@code trade} table: {@code NUMERIC(20,6)} holds 14 digits before the decimal point and 6 after it,
 * {@code note} is {@code VARCHAR(500)}. The quantity is a whole number of shares, so it has no decimal places at all.
 * {@code price}, {@code note} and {@code commission} are optional; without a commission the default for one order is
 * stored ({@code OrderCommission.defaultFor}), and 0 is a commission too. {@code investorId}, added later, is
 * optional too: a new trade without one is the account owner's, and a correction without one keeps the trade's
 * investor.
 */
public record TradeRequest(
        @NotNull @PastOrPresent LocalDate tradeDate,
        @NotNull TradeSide side,
        @NotNull @Positive @Digits(integer = 14, fraction = 0, message = WHOLE_NUMBER) BigDecimal quantity,
        @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal price,
        @Size(max = 500) String note,
        @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal commission,
        Long investorId) {

    /**
     * Replaces Hibernate Validator's "numeric value out of bounds (<14 digits>.<6 digits> expected)", which the UI shows.
     * Also used by {@link NewManualPositionRequest}.
     */
    protected static final String TOO_MANY_DIGITS = "must have at most 6 decimal places (and 14 digits before the point)";

    /**
     * The same, for a quantity: {@code @Digits} with {@code fraction = 0}. It counts the decimal places as they were
     * written, so 10.0 is rejected too; the UI sends 10. Also used by {@link NewManualPositionRequest}.
     */
    protected static final String WHOLE_NUMBER = "must be a whole number (at most 14 digits)";
}
