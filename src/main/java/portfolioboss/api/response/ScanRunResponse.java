package portfolioboss.api.response;

import java.time.Instant;

/**
 * What {@code POST /api/scans} answers once the new scan is stored; the UI then reloads {@code /api/recommendations}.
 * The component names are JSON keys: added to, never renamed or removed.
 *
 * @param candidateCount the stocks the scan kept, on NYSE or NASDAQ
 */
public record ScanRunResponse(Instant scannedAt, int candidateCount) {
}
