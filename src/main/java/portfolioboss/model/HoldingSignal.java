package portfolioboss.model;

/**
 * The colored dot next to a holding (AI_ANALYSIS_TODO.md, decision 6), worked out by
 * {@code calculation.SignalCalculator}: {@code RED} for two warning signs or more, {@code GREEN} for none,
 * {@code YELLOW} for one. The names are JSON values.
 */
public enum HoldingSignal {
    GREEN,
    YELLOW,
    RED
}
