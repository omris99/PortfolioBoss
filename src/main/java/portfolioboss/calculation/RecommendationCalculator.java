package portfolioboss.calculation;

import portfolioboss.model.RiskSign;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The recommendation of RECOMMENDATIONS_TODO.md, decisions 5 and 13, picked by rules in code — never by Claude, who
 * answered the same question differently from run to run. From the short list: the stocks Finnhub gave figures for,
 * whose risk signs weigh 2 at most together — whatever the news says —; then the highest news score less the risks'
 * weight first, and on a tie the order of the list (decision 1); up to 3. When none qualifies the answer is no
 * recommendation this time.
 *
 * @param shortList the analyzed stocks of the short list — the first ones of HOT (decision 1), each that succeeded
 * @param today     the day of the analysis, for the analysts' moves the news score counts
 */
public record RecommendationCalculator(List<RecommendationCandidate> shortList, LocalDate today) {

    private static final int MOST_PICKS = 3;
    /**
     * Each sign's weight, since they don't all threaten the same (the user's rule, 2026-10-27): revenue falling under a
     * rising price is the heaviest, debt makes a fall deeper, and a loss, a high P/E or a rebound after a crash are
     * common in the growth stocks the scan finds. Opening numbers, like the limit below — the measurement tests each sign
     * on its own (decision 9).
     */
    private static final Map<RiskSign, Integer> WEIGHT_OF_RISK_SIGN = Map.of(
            RiskSign.LOSES_MONEY, 1,
            RiskSign.HIGH_DEBT, 2,
            RiskSign.EXPENSIVE, 1,
            RiskSign.REVENUE_FALLING, 3,
            RiskSign.RECOVERING, 1);
    /**
     * A stock whose signs weigh more than this together is not picked, whatever the news says (decision 13). It was 4
     * until the news events could offset the risks: a loss and a debt 16.8 times the equity (3) would then pass on one
     * piece of good news.
     */
    private static final int MOST_RISK_WEIGHT_OF_A_PICK = 2;

    /** In the order of the recommendation; empty when no stock qualifies. */
    public List<RecommendationCandidate> picks() {
        Comparator<RecommendationCandidate> highestScoreFirst =
                Comparator.comparingInt(this::scoreOf).reversed();
        Comparator<RecommendationCandidate> placeInList = (firstCandidate, secondCandidate) ->
                firstCandidate.hotStock().comparePlaceInList(secondCandidate.hotStock());
        return shortList.stream()
                .filter(this::canBePicked)
                .sorted(highestScoreFirst.thenComparing(placeInList))
                .limit(MOST_PICKS)
                .toList();
    }

    /** The news score less the risks' weight: a stock with no news and no sign scores 0. */
    public int scoreOf(RecommendationCandidate candidate) {
        return candidate.newsScore(today) - riskWeightOf(candidate);
    }

    /** The weights of the stock's signs added up; 0 when it has none. */
    public int riskWeightOf(RecommendationCandidate candidate) {
        return candidate.riskSigns().stream().mapToInt(WEIGHT_OF_RISK_SIGN::get).sum();
    }

    /**
     * Finnhub gave at least one of its figures, and the signs weigh 2 at most: whether the stock can be picked at all,
     * before the scores are compared — what the page says about a stock analyzed but not picked.
     */
    public boolean canBePicked(RecommendationCandidate candidate) {
        return candidate.hasCompanyFacts() && riskWeightOf(candidate) <= MOST_RISK_WEIGHT_OF_A_PICK;
    }
}
