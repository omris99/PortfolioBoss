package portfolioboss.api.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * The answer to {@code POST /api/analysis}: what one run did and cost (AI_ANALYSIS_TODO.md, decision 14) — the same
 * figures as its {@code [ai]} line. The costs are those of the stocks analyzed; a stock that failed may have spent some
 * too, uncounted. The analyses themselves are read like everything else, from {@code GET /api/portfolio}. The
 * component names are JSON keys.
 *
 * @param analyzed      how many stocks were analyzed and stored
 * @param tavilyCredits Tavily's credits (the free plan has 1,000 a month)
 * @param costUsd       what Claude's tokens cost, in dollars
 */
public record AnalysisRunResponse(
        int analyzed,
        List<FailedAnalysisResponse> failed,
        int tavilyCredits,
        long inputTokens,
        long outputTokens,
        BigDecimal costUsd) {
}
