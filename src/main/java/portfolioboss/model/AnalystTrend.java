package portfolioboss.model;

/**
 * Which way the analysts have been moving, worked out by {@code calculation.AnalystTrendCalculator} from their recent
 * actions (AI_ANALYSIS_TODO.md, decision 18): more moves up — upgrades and raised targets — is {@code IMPROVING}, more
 * moves down {@code DETERIORATING}, as many of each {@code STABLE}. {@code DETERIORATING} is one of the dot's three
 * warning signs (decision 6). The names are JSON values.
 */
public enum AnalystTrend {
    IMPROVING,
    STABLE,
    DETERIORATING
}
