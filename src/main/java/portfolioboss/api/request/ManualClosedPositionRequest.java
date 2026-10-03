package portfolioboss.api.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

import static portfolioboss.api.request.TradeRequest.TOO_MANY_DIGITS;

/**
 * The body of {@code POST /api/manual-closed-positions} and {@code PUT /api/manual-closed-positions/{id}}: a whole
 * round trip — one buy and one sell — as the user typed it. Checked by {@code @Valid} before the controller method runs,
 * with the same limits as {@link TradeRequest}, which match the {@code manual_closed_position} table. {@code sector},
 * {@code commission} and {@code note} are optional; without a commission the default for two orders, the buy and the
 * sell, is stored ({@code Utils.calculateOrderCommission}).
 */
public record ManualClosedPositionRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 8) String currency,
        @Size(max = 60) String sector,
        @NotNull @Positive @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal quantity,
        @NotNull @PastOrPresent LocalDate buyDate,
        @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal buyPrice,
        @NotNull @PastOrPresent LocalDate sellDate,
        @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal sellPrice,
        @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal commission,
        @Size(max = 500) String note) {

    /**
     * The one rule that spans two fields, so no annotation on a single field can check it. {@code @AssertTrue} makes
     * {@code @Valid} run this along with the others; the error names it after the method, without the "is":
     * "sellDateOnOrAfterBuyDate: the sell date is before the buy date". A missing date is left to {@code @NotNull}.
     */
    @AssertTrue(message = "the sell date is before the buy date")
    private boolean isSellDateOnOrAfterBuyDate() {
        return buyDate == null || sellDate == null || !sellDate.isBefore(buyDate);
    }
}
