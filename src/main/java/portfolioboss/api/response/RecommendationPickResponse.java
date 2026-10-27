package portfolioboss.api.response;

import portfolioboss.model.RiskSign;

import java.util.List;

/**
 * One stock the code picked for the recommendation (RECOMMENDATIONS_TODO.md, decisions 5 and 13), in the
 * recommendation's order. The component names are JSON keys.
 *
 * @param riskSigns           the signs it has — their weights added up are 2 at most
 * @param reportsWithin14Days the timing note: wait for its report, due 0 to 14 days after the run
 * @param newsScore           the week's news events and the analysts' moves weighed; above 0 the good outweighs the bad
 * @param score               the news score less the risks' weight — what the picks are ordered by
 */
public record RecommendationPickResponse(
        long candidateId,
        String symbol,
        List<RiskSign> riskSigns,
        boolean reportsWithin14Days,
        int newsScore,
        int score) {
}
