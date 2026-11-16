package portfolioboss.tax.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The body of {@code POST /api/taxes/confirmations}: one day, or every day of a month still waiting ("confirm the whole
 * month"), in one request — stored together or not at all. {@code @Valid} on each line has its own rules checked too.
 *
 * @param confirmations the days, each with what Phoenix recorded for it
 */
public record TaxConfirmationsRequest(@NotEmpty List<@NotNull @Valid TaxConfirmationRequest> confirmations) {

    /** A day has one confirmation: the same day twice in one request is a mistake, not two entries. */
    @AssertTrue(message = "each settlement day can be confirmed only once")
    private boolean isEveryDayGivenOnce() {
        if (confirmations == null) {
            return true;
        }
        List<LocalDate> givenDays = confirmations.stream()
                .filter(Objects::nonNull)
                .map(TaxConfirmationRequest::settlementDay)
                .filter(Objects::nonNull)
                .toList();
        return Set.copyOf(givenDays).size() == givenDays.size();
    }
}
