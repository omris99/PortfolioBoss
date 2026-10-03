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
import static portfolioboss.api.request.TradeRequest.WHOLE_NUMBER;

/**
 * The body of {@code POST /api/manual-positions}: a new manual position — its details as in
 * {@link ManualPositionRequest} — with its first buy and its first sell of the same quantity, so it always has a
 * closed position to show. More buys and sells are added through {@code POST /api/manual-positions/{id}/trades}.
 * Checked by {@code @Valid} with the same limits as {@link TradeRequest}. {@code sector}, {@code note} and both
 * commissions are optional; a commission left empty is stored as the default for that order.
 */
public record NewManualPositionRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 8) String currency,
        @Size(max = 60) String sector,
        @Size(max = 500) String note,
        @NotNull @Positive @Digits(integer = 14, fraction = 0, message = WHOLE_NUMBER) BigDecimal quantity,
        @NotNull @PastOrPresent LocalDate buyDate,
        @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal buyPrice,
        @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal buyCommission,
        @NotNull @PastOrPresent LocalDate sellDate,
        @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal sellPrice,
        @PositiveOrZero @Digits(integer = 14, fraction = 6, message = TOO_MANY_DIGITS) BigDecimal sellCommission) {

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
