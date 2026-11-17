package portfolioboss.recommendations;

import portfolioboss.ai.StockAnalysisRun;

import java.util.List;

/**
 * What one run of the Recommendations page's analysis did: the stocks analyzed and what it cost, and the picks.
 *
 * @param analysis the outcomes in the order of the stocks analyzed, and the run's sums
 * @param picks    in the recommendation's order — empty when no stock qualified; {@code null} for one stock's analysis,
 *                 which makes no recommendation
 */
public record RecommendationAnalysisRun(StockAnalysisRun analysis, List<RecommendationPick> picks) {
}
