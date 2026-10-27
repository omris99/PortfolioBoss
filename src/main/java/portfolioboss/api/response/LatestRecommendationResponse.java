package portfolioboss.api.response;

import java.time.Instant;
import java.util.List;

/**
 * The latest recommendation the code made (RECOMMENDATIONS_TODO.md, decisions 5 and 13), shown even after a newer scan,
 * with the date of its own (decision 12). The component names are JSON keys.
 *
 * @param scannedAt the scan the recommendation's short list came from — older than the page's when a scan came since
 * @param picks     in the recommendation's order; empty when no stock qualified
 */
public record LatestRecommendationResponse(Instant recommendedAt, Instant scannedAt, List<PickedStockResponse> picks) {
}
