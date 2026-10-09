package portfolioboss.api.response;

import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystTrend;
import portfolioboss.ai.Headline;
import portfolioboss.ai.Sentiment;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.model.AnalystConsensus;

import java.time.Instant;
import java.util.List;

/**
 * A holding's latest analysis as the UI receives it (AI_ANALYSIS_TODO.md, 2.4): when and by which model, the consensus
 * the code chose (decision 16), and the rest of what Claude found as it is — the consensus of every source too, for the
 * expanded row. The calculation records go into the JSON as they are, like {@code HoldingWarning}. The component names
 * are JSON keys: added to, never renamed or removed.
 *
 * @param consensus {@code null} when no source showed a consensus
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
        String sentimentReason) {

    /** {@code consensus} is worked out by {@code PortfolioReadService} on every read, never stored. */
    public StockAnalysisResponse(StockAnalysisEntity analysis, AnalystConsensus consensus) {
        this(
                analysis.analyzedAt(),
                analysis.model(),
                consensus,
                analysis.result().consensusBySource(),
                analysis.result().analystTrend(),
                analysis.result().recentActions(),
                analysis.result().headlines(),
                analysis.result().sentiment(),
                analysis.result().sentimentReason());
    }
}
