package portfolioboss.api.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The body of {@code POST /api/holdings/{holdingId}/trades} and {@code PUT /api/trades/{tradeId}}: one buy or sell as
 * the user typed it. The annotations are checked by {@code @Valid} before the controller method runs, and their limits
 * match the {@code trade} table: {@code NUMERIC(20,6)} holds 14 digits before the decimal point and 6 after it,
 * {@code note} is {@code VARCHAR(500)}. {@code price} and {@code note} are optional.
 */
public record TradeRequest(
        @NotNull @PastOrPresent LocalDate tradeDate,
        @NotNull TradeSide side,
        @NotNull @Positive @Digits(integer = 14, fraction = 6) BigDecimal quantity,
        @PositiveOrZero @Digits(integer = 14, fraction = 6) BigDecimal price,
        @Size(max = 500) String note) {
}
