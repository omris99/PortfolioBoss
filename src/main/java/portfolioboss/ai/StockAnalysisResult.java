package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.util.List;

/**
 * What Claude found about one stock in its search results (AI_ANALYSIS_TODO.md, 2.2): the analysts' consensus source by
 * source and their latest moves, the week's headlines and how they read. This record is the one definition of that
 * shape: the SDK turns it into the JSON schema Claude must answer in, the {@code stock_analysis} table stores it as
 * JSON, and the API sends it on. What is decided from it — which source's consensus to show, which way the analysts are
 * going, the dot's color — is worked out by {@code calculation.ConsensusCalculator}, {@code AnalystTrendCalculator} and
 * {@code SignalCalculator} on every read, so a change of rule applies to old analyses too.
 *
 * <p>Claude reports only: what the results don't say is {@code null} ({@code @Nullable}, see {@link SourceConsensus})
 * or an empty list, never a guess. Analyses stored until 2026-10-09 also hold Claude's own {@code analystTrend}, which
 * is skipped when read (decision 18).
 *
 * @param consensusBySource one entry per source that shows a consensus, neither merged nor chosen between
 * @param recentActions     up to 10 from the last 90 days, the newest first (5 until 2026-10-10)
 * @param headlines         up to 3, the most important of the last 14 days
 * @param sentimentReason   one short sentence in English
 */
public record StockAnalysisResult(
        List<SourceConsensus> consensusBySource,
        List<AnalystAction> recentActions,
        List<Headline> headlines,
        @Nullable Sentiment sentiment,
        @Nullable String sentimentReason) {
}
