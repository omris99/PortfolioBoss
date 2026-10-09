package portfolioboss.ai;

/**
 * How the week's news reads for someone holding the stock. {@code NEGATIVE} is one of the dot's three warning signs
 * (AI_ANALYSIS_TODO.md, decision 6). The names are JSON values.
 */
public enum Sentiment {
    POSITIVE,
    NEUTRAL,
    NEGATIVE
}
