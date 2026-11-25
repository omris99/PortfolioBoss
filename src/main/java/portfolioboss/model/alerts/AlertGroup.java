package portfolioboss.model.alerts;

/**
 * The two groups of the bell's panel (UI_POLISH_TODO.md, stage ד), in the order the panel shows them: the holdings'
 * alerts first, so that the short list's many can't push them out of sight. Which group an alert is in is
 * {@code calculation.alerts.AlertGroupCalculator}'s. The names reach the JSON as they are ({@code ui/src/types/alerts.ts}
 * mirrors them).
 */
public enum AlertGroup {
    /** "Your holdings": every alert about a stock held now, and every alert of a holding's own rules. */
    YOUR_HOLDINGS,
    /** "The short list": a stock that entered the Recommendations short list and isn't held now. */
    SHORT_LIST
}
