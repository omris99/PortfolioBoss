package portfolioboss.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/investors} and {@code PUT /api/investors/{investorId}}: an investor's name as the user
 * typed it. The limit matches the {@code investor} table ({@code VARCHAR(60)}); the spaces around it are dropped.
 */
public record InvestorRequest(@NotBlank @Size(max = 60) String name) {
}
