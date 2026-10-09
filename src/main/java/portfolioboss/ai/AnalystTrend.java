package portfolioboss.ai;

/**
 * Which way the analysts have been moving over the last 90 days: upgrades and raised targets against downgrades and
 * lowered ones, or a change in the share of buy ratings on a source that shows its history. {@code DETERIORATING} is
 * one of the dot's three warning signs (AI_ANALYSIS_TODO.md, decision 6). The names are JSON values.
 */
public enum AnalystTrend {
    IMPROVING,
    STABLE,
    DETERIORATING
}
