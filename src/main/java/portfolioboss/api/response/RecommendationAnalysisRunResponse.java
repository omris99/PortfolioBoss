package portfolioboss.api.response;

import java.util.List;

/**
 * The answer to {@code POST /api/recommendations/analysis}: what the run did and cost, as for the holdings' analysis,
 * and the stocks the code picked. The analyses themselves are read from {@code GET /api/recommendations}. The component
 * names are JSON keys.
 *
 * @param picks in the recommendation's order — empty when no stock qualified; {@code null} for one stock's analysis,
 *              which makes no recommendation
 */
public record RecommendationAnalysisRunResponse(
        AnalysisRunResponse analysis,
        List<RecommendationPickResponse> picks) {
}
