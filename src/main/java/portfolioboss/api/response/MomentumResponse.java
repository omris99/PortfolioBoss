package portfolioboss.api.response;

import portfolioboss.calculation.Momentum;
import portfolioboss.calculation.MomentumLabel;

import java.time.LocalDate;

/**
 * A holding's momentum as the UI receives it (AI_ANALYSIS_TODO.md, decision 5): the figures of {@link Momentum}, its
 * five checks — {@code null} where there were not enough closes —, how far below the 20-day high the last close is,
 * and the score with its label. The component names are JSON keys: added to, never renamed or removed.
 */
public record MomentumResponse(
        LocalDate asOf,
        double lastClose,
        Double sma20,
        Double sma50,
        Double sma200,
        Double high20,
        Double oneMonthReturnPercent,
        Double spyOneMonthReturnPercent,
        Boolean aboveSma20,
        Boolean aboveSma50,
        Boolean sma50AboveSma200,
        Boolean nearHigh,
        Boolean beatsSpy,
        Double percentBelowHigh,
        Integer score,
        MomentumLabel label) {

    /** A holding without a single stored close has no momentum: {@code PortfolioReadService} leaves it null. */
    public MomentumResponse(Momentum momentum) {
        this(
                momentum.asOf(),
                momentum.lastClose(),
                momentum.sma20(),
                momentum.sma50(),
                momentum.sma200(),
                momentum.high20(),
                momentum.oneMonthReturnPercent(),
                momentum.spyOneMonthReturnPercent(),
                momentum.aboveSma20(),
                momentum.aboveSma50(),
                momentum.sma50AboveSma200(),
                momentum.nearHigh(),
                momentum.beatsSpy(),
                momentum.percentBelowHigh(),
                momentum.score(),
                momentum.label());
    }
}
