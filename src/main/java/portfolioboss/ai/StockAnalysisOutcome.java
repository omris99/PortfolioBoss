package portfolioboss.ai;

/**
 * What one stock's analysis came to: Claude's reply and the search results it read, or — {@code reply} and
 * {@code searches} being {@code null} — why it failed.
 *
 * @param searches       only the results that name the stock: exactly what Claude read (AI_ANALYSIS_TODO.md, decision
 *                       22)
 * @param failureMessage {@code null} when the stock was analyzed
 */
public record StockAnalysisOutcome(String symbol, ClaudeReply reply, StockSearches searches, String failureMessage) {

    public boolean succeeded() {
        return reply != null;
    }

    /** What its searches cost; a stock that failed is not counted. */
    public int tavilyCredits() {
        return searches == null ? 0 : searches.credits();
    }
}
