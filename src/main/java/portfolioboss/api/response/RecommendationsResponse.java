package portfolioboss.api.response;

import java.time.Instant;
import java.util.List;

/**
 * What {@code GET /api/recommendations} serves: the latest scan's stocks in the three lists of RECOMMENDATIONS_TODO.md,
 * decision 1, each list in the order of the page. The component names are JSON keys: added to, never renamed or removed.
 *
 * @param candidateCount every stock the scan kept — also those in no list (no long upward trend, or fewer than 200
 *                       closes)
 * @param hotStocks      0–15% above the 50-day average: worth a look now
 * @param tooHotStocks   more than 15% above it: wait for a pullback
 * @param correctingStocks below it: wait for the close to climb back above
 */
public record RecommendationsResponse(
        Instant scannedAt,
        int candidateCount,
        List<HotStockResponse> hotStocks,
        List<HotStockResponse> tooHotStocks,
        List<HotStockResponse> correctingStocks) {
}
