package portfolioboss.api.response;

import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.Headline;
import portfolioboss.ai.MoveReason;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.calculation.AnalystTrendCalculator;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.model.AnalystConsensus;
import portfolioboss.model.AnalystTrend;

import java.time.Instant;
import java.util.List;

/**
 * A holding's latest analysis as the UI receives it (AI_ANALYSIS_TODO.md, 2.4): when and by which model, the consensus
 * and the analysts' trend the code worked out (decisions 16 and 18), and the rest of what Claude found as it is — the
 * consensus of every source too, for the expanded row. The calculation records go into the JSON as they are, like
 * {@code HoldingWarning}. The component names are JSON keys: added to, never renamed or removed.
 *
 * @param consensus       {@code null} when no source showed a consensus
 * @param analystTrend       {@code null} while no action states a price target (Claude's own until 2026-10-09)
 * @param raisedTargetCount  the price targets raised among {@code recentActions}, which the trend weighs against
 * @param loweredTargetCount the price targets lowered
 * @param moveReason         {@code null} when the news doesn't say why the price moved, and before 2026-10-27
 * @param newsEvents         the week's events in the news (RECOMMENDATIONS_TODO.md, decision 13); none before 2026-10-27
 */
public record StockAnalysisResponse(
        Instant analyzedAt,
        String model,
        AnalystConsensus consensus,
        List<SourceConsensus> consensusBySource,
        AnalystTrend analystTrend,
        List<AnalystAction> recentActions,
        List<Headline> headlines,
        Sentiment sentiment,
        String sentimentReason,
        int raisedTargetCount,
        int loweredTargetCount,
        MoveReason moveReason,
        List<NewsEvent> newsEvents) {

    /** {@code consensus} and the trend are worked out by {@code PortfolioReadService} on every read, never stored. */
    public StockAnalysisResponse(StockAnalysisEntity analysis, AnalystConsensus consensus,
                                 AnalystTrendCalculator analystTrend) {
        this(
                analysis.analyzedAt(),
                analysis.model(),
                consensus,
                analysis.result().consensusBySource(),
                analystTrend.trend(),
                analysis.result().recentActions(),
                analysis.result().headlines(),
                analysis.result().sentiment(),
                analysis.result().sentimentReason(),
                analystTrend.raisedTargetCount(),
                analystTrend.loweredTargetCount(),
                analysis.result().moveReason(),
                analysis.result().newsEvents());
    }
}
