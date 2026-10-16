package portfolioboss.api.request;

import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/holdings/{holdingId}/sector}: free text. {@code null}, empty or only spaces clears
 * the sector. 60 is the length of the {@code holding.sector} column.
 */
public record SectorRequest(@Size(max = 60) String sector) {
}
