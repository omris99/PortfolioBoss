package portfolioboss.calculation;

import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystActionType;
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A stock's news score (RECOMMENDATIONS_TODO.md, decision 13): the weights of the week's events in the news, and of the
 * analysts' upgrades and downgrades, added up — above 0 the good news outweighs the bad. Claude says what happened; how
 * much each is worth is here, so that the measurement can tune it. The weights are opening numbers.
 *
 * @param newsEvents    the events Claude found in the news
 * @param recentActions the analysts' actions Claude found, up to 90 days back
 * @param today         the day of the analysis: the analysts' moves of the 30 days before it count
 */
public record NewsScoreCalculator(List<NewsEvent> newsEvents, List<AnalystAction> recentActions, LocalDate today) {

    /** The kinds whose weight depends on their size — the only ones Claude gives a magnitude. */
    private static final Set<NewsEventType> KINDS_WEIGHED_BY_MAGNITUDE =
            Set.of(NewsEventType.PARTNERSHIP, NewsEventType.MAJOR_CONTRACT);
    private static final Map<EventMagnitude, Integer> WEIGHT_OF_MAGNITUDE = Map.of(
            EventMagnitude.HIGH, 3,
            EventMagnitude.MEDIUM, 2,
            EventMagnitude.LOW, 1);
    /**
     * Every other kind weighs the same whatever its size: the company changing the year's outlook is the strongest sign
     * of where the business is going, and a new product is common and mostly small. An event no kind fits weighs nothing.
     */
    private static final Map<NewsEventType, Integer> WEIGHT_OF_KIND = Map.of(
            NewsEventType.GUIDANCE_RAISED, 3,
            NewsEventType.EARNINGS_BEAT, 2,
            NewsEventType.REGULATORY_APPROVAL, 2,
            NewsEventType.PRODUCT_LAUNCH, 1,
            NewsEventType.GUIDANCE_CUT, -3,
            NewsEventType.EARNINGS_MISS, -2,
            NewsEventType.SHARE_OFFERING, -2,
            NewsEventType.LAWSUIT_OR_REGULATION, -2,
            NewsEventType.NOT_IN_THE_LIST, 0);
    /** An analyst's upgrade adds this much, a downgrade takes it away (the user's number). */
    private static final int WEIGHT_OF_AN_ANALYSTS_MOVE = 2;
    private static final int DAYS_AN_ANALYSTS_MOVE_COUNTS = 30;

    /** 0 with no event and no analyst's move. */
    public int score() {
        int eventsWeight = newsEvents.stream().mapToInt(this::weightOf).sum();
        int analystsWeight = recentActions.stream().mapToInt(this::weightOf).sum();
        return eventsWeight + analystsWeight;
    }

    /** A partnership or a contract by its size — the smallest when Claude didn't give one —, every other kind by kind. */
    public int weightOf(NewsEvent event) {
        if (KINDS_WEIGHED_BY_MAGNITUDE.contains(event.type())) {
            EventMagnitude magnitude = event.magnitude() == null ? EventMagnitude.LOW : event.magnitude();
            return WEIGHT_OF_MAGNITUDE.get(magnitude);
        }
        return WEIGHT_OF_KIND.get(event.type());
    }

    /**
     * An upgrade or a downgrade counts only with a price target — like the analysts' trend (AI_ANALYSIS_TODO.md,
     * decision 18), which leaves out the rating services that grade without one, with no list of names — and only from
     * the last 30 days. Every other action, and every older one, weighs nothing.
     */
    public int weightOf(AnalystAction action) {
        boolean ratingChanged = action.action() == AnalystActionType.UPGRADE
                || action.action() == AnalystActionType.DOWNGRADE;
        if (!ratingChanged || action.priceTarget() == null || !isRecent(action.date())) {
            return 0;
        }
        return action.action() == AnalystActionType.UPGRADE
                ? WEIGHT_OF_AN_ANALYSTS_MOVE
                : -WEIGHT_OF_AN_ANALYSTS_MOVE;
    }

    /** Within the 30 days before {@code today}, both ends counted. */
    private boolean isRecent(LocalDate actionDate) {
        return actionDate != null
                && !actionDate.isBefore(today.minusDays(DAYS_AN_ANALYSTS_MOVE_COUNTS))
                && !actionDate.isAfter(today);
    }
}
