package portfolioboss.api.recommendations.response;

import portfolioboss.api.analysis.response.AnalysisRunResponse;
import portfolioboss.recommendations.RecommendationAnalysisRun;

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

    public RecommendationAnalysisRunResponse(RecommendationAnalysisRun run) {
        this(new AnalysisRunResponse(run.analysis()),
                run.picks() == null ? null : run.picks().stream().map(RecommendationPickResponse::new).toList());
    }
}
