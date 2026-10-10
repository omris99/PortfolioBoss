package portfolioboss.calculation;

import portfolioboss.ai.AnalystAction;
import portfolioboss.model.AnalystTrend;

import java.util.List;
import java.util.function.Predicate;

/**
 * Works out which way the analysts have been moving (AI_ANALYSIS_TODO.md, decision 18) from the recent actions Claude
 * extracted — only from their price targets: an action counts when it states the target before and after, raised or
 * lowered. More targets raised is {@code IMPROVING}, more lowered {@code DETERIORATING}, as many of each
 * {@code STABLE} — also when targets are stated and none moved — and no action with a target at all {@code null}.
 * Derived on every read, never stored.
 *
 * <p>So a rating alone moves nothing: services that grade stocks without a target — Zacks Rank, Weiss Ratings, Wall
 * Street Zen, whose "downgrades" are a letter grade or a 1–5 score — drop out without a list of names, and so does a
 * firm's upgrade that states no earlier target. The code decides, not Claude: until 2026-10-09 Claude did, and answered
 * differently from run to run.
 *
 * <p>Example, INTC on 2026-10-09: Mizuho lowered its target from $109 to $92; Northland upgraded to Buy with a $120
 * target but no earlier one; three firms kept or started coverage with a target — none raised, one lowered:
 * {@code DETERIORATING}.
 *
 * @param recentActions up to 10 from the last 90 days, as Claude extracted them
 */
public record AnalystTrendCalculator(List<AnalystAction> recentActions) {

    public int raisedTargetCount() {
        return countWhere(action -> statesBothTargets(action) && action.priceTarget() > action.previousPriceTarget());
    }

    public int loweredTargetCount() {
        return countWhere(action -> statesBothTargets(action) && action.priceTarget() < action.previousPriceTarget());
    }

    /** {@code null} while no action states a target: nothing says which way the analysts are going. */
    public AnalystTrend trend() {
        if (recentActions.stream().noneMatch(action -> action.priceTarget() != null)) {
            return null;
        }
        int raisedTargetCount = raisedTargetCount();
        int loweredTargetCount = loweredTargetCount();
        if (raisedTargetCount > loweredTargetCount) {
            return AnalystTrend.IMPROVING;
        }
        return loweredTargetCount > raisedTargetCount ? AnalystTrend.DETERIORATING : AnalystTrend.STABLE;
    }

    private boolean statesBothTargets(AnalystAction action) {
        return action.previousPriceTarget() != null && action.priceTarget() != null;
    }

    private int countWhere(Predicate<AnalystAction> condition) {
        return (int) recentActions.stream().filter(condition).count();
    }
}
