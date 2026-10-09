package portfolioboss.ai;

/**
 * An analyst rating on one scale for every source (AI_ANALYSIS_TODO.md, decision 16): Claude maps a source's own words
 * onto it — "Moderate Buy" or "Outperform" is {@code BUY}. Goes into the JSON as it is, so the names are JSON values.
 */
public enum AnalystRating {
    STRONG_BUY,
    BUY,
    HOLD,
    SELL,
    STRONG_SELL
}
