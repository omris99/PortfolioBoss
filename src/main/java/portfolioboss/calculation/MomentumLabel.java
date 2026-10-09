package portfolioboss.calculation;

/**
 * What a momentum score of 0–5 means (AI_ANALYSIS_TODO.md, decision 5): {@code STRONG} is 4–5, {@code NEUTRAL} 2–3,
 * {@code WEAK} 0–1. Goes into the JSON as it is.
 */
public enum MomentumLabel {
    STRONG,
    NEUTRAL,
    WEAK
}
