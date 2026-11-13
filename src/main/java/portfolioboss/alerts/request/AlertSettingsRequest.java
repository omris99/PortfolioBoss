package portfolioboss.alerts.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import portfolioboss.alerts.AlertType;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The body of {@code PUT /api/alerts/settings}: the whole settings drawer, every alert type once. {@code @Valid} on each
 * line has its own rules checked too.
 */
public record AlertSettingsRequest(@NotNull List<@NotNull @Valid AlertSettingRequest> settings) {

    /** The drawer saves all its lines together: a type missing or given twice is a mistake, not a change. */
    @AssertTrue(message = "every alert type must be given exactly once")
    private boolean isEveryTypeGivenOnce() {
        if (settings == null) {
            return true;
        }
        List<AlertType> givenTypes = settings.stream()
                .filter(Objects::nonNull)
                .map(AlertSettingRequest::alertType)
                .filter(Objects::nonNull)
                .toList();
        int typeCount = AlertType.values().length;
        return givenTypes.size() == typeCount && Set.copyOf(givenTypes).size() == typeCount;
    }
}
