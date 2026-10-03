package portfolioboss.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/manual-positions/{id}}: a manual position's own details as the user typed them. Its buys
 * and sells are changed through the trade endpoints. The limits match the {@code manual_position} table; {@code sector}
 * and {@code note} are optional.
 */
public record ManualPositionRequest(
        @NotBlank @Size(max = 32) String symbol,
        @NotBlank @Size(max = 8) String currency,
        @Size(max = 60) String sector,
        @Size(max = 500) String note) {
}
