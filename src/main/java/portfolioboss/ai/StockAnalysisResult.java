package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * What Claude found about one stock in its search results (AI_ANALYSIS_TODO.md, 2.2): the analysts' consensus source by
 * source, their trend and latest moves, the week's headlines and how they read. This record is the one definition of
 * that shape: the SDK turns it into the JSON schema Claude must answer in, the {@code stock_analysis} table stores it as
 * JSON, and the API sends it on. What is decided from it — which source's consensus to show, the dot's color — is
 * worked out by {@code calculation.ConsensusCalculator} and {@code calculation.SignalCalculator} on every read, so a
 * change of rule applies to old analyses too.
 *
 * <p>Claude reports only: what the results don't say is {@code null} ({@code @Nullable}, see {@link SourceConsensus})
 * or an empty list, never a guess.
 *
 * @param consensusBySource one entry per source that shows a consensus, neither merged nor chosen between
 * @param recentActions     up to 5 from the last 90 days, the newest first
 * @param headlines         up to 3, the most important of the last 14 days
 * @param sentimentReason   one short sentence in English
 */
public record StockAnalysisResult(
        List<SourceConsensus> consensusBySource,
        @Nullable AnalystTrend analystTrend,
        List<AnalystAction> recentActions,
        List<Headline> headlines,
        @Nullable Sentiment sentiment,
        @Nullable String sentimentReason) {
}
